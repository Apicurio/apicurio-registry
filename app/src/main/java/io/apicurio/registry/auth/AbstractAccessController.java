package io.apicurio.registry.auth;

import io.apicurio.registry.ccompat.rest.v7.impl.CCompatConfig;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.iceberg.rest.v1.impl.IcebergNamespaces;
import io.apicurio.registry.rest.headers.Headers;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.error.NotFoundException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.interceptor.InvocationContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class AbstractAccessController implements IAccessController {

    @Inject
    protected AuthConfig authConfig;

    @Inject
    protected SecurityIdentity securityIdentity;

    @Inject
    @Current
    protected RegistryStorage storage;

    @Inject
    protected CCompatConfig ccompatConfig;

    @Inject
    @Context
    protected HttpServletRequest request;

    /**
     * Resolves the resource addressed by the invocation from its {@link AuthorizedStyle} and
     * parameters. This is the single place that interprets styles, so owner-based and
     * per-resource authorization always evaluate the resource the endpoint actually operates on.
     *
     * @return the resource, or empty when the style addresses no single resource or the parameters
     *         are malformed (the endpoint rejects them itself). IDs that do not exist resolve to
     *         content used by no artifact, which per-resource authorization denies.
     */
    public Optional<AuthorizedResource> resolveResource(InvocationContext context) {
        Authorized annotation = context.getMethod().getAnnotation(Authorized.class);
        if (annotation == null) {
            return Optional.empty();
        }
        return switch (annotation.style()) {
            // A mis-annotated single-parameter method fails loudly rather than skipping checks
            case GroupAndArtifact -> Optional.of(AuthorizedResource.artifact(getStringParam(context, 0),
                    getStringParam(context, 1)));
            case GroupOnly -> Optional.of(AuthorizedResource.group(getStringParam(context, 0)));
            case ArtifactOnly -> resolveCCompatSubject(getStringParam(context, 0));
            case QualifiedArtifactName -> {
                String name = getStringParam(context, 0);
                int slash = name == null ? -1 : name.indexOf('/');
                if (slash < 0) {
                    yield Optional.empty();
                }
                yield Optional.of(AuthorizedResource.artifact(name.substring(0, slash),
                        name.substring(slash + 1)));
            }
            case GlobalId -> resolveGlobalId(getLongParam(context, 0));
            case IcebergNamespace -> Optional.of(AuthorizedResource.group(
                    IcebergNamespaces.encodedToGroupId(getStringParam(context, 1))));
            case IcebergTable -> Optional.of(AuthorizedResource.artifact(
                    IcebergNamespaces.encodedToGroupId(getStringParam(context, 1)),
                    getStringParam(context, 2)));
            case ContentId -> resolveContentId(getLongParam(context, 0));
            case ContentHash -> {
                String hash = getStringParam(context, 0);
                yield hash == null ? Optional.of(unknownId())
                        : storage.contentIdFromHash(hash).map(this::resolveContentId).orElse(Optional.of(unknownId()));
            }
            case CCompatSchemaId -> {
                Long id = getLongParam(context, 0);
                yield ccompatConfig.isLegacyIdModeEnabled() ? resolveGlobalId(id) : resolveContentId(id);
            }
            case None -> Optional.empty();
        };
    }

    /**
     * Owner-based authorization (OBAC) check. Lenient by design: anything that cannot be
     * resolved, does not exist yet, or has no owner is allowed, and the endpoint decides.
     */
    protected boolean isOwner(InvocationContext context) {
        Authorized annotation = context.getMethod().getAnnotation(Authorized.class);
        AuthorizedStyle style = annotation.style();
        Optional<AuthorizedResource> resource = resolveResource(context);
        if (resource.isEmpty()) {
            return true;
        }
        AuthorizedResource r = resource.get();
        return switch (r.kind()) {
            case ARTIFACT -> verifyArtifactOwner(r.groupId(), r.artifactId());
            case GROUP -> {
                // Group ownership is only enforced when explicitly configured
                boolean groupStyle = style == AuthorizedStyle.GroupOnly
                        || style == AuthorizedStyle.IcebergNamespace;
                if (groupStyle && authConfig.ownerOnlyAuthorizationLimitGroupAccess.get()) {
                    yield verifyGroupOwner(r.groupId());
                }
                yield true;
            }
            case CONTENT -> true;
        };
    }

    /**
     * Strict ownership check used to let owners bypass per-resource (grants) authorization.
     * Unlike {@link #isOwner}, it is true only when the resource exists and its recorded owner is
     * the caller: unresolvable, missing or unowned resources never bypass grants.
     */
    public boolean isStrictOwner(InvocationContext context) {
        return resolveResource(context).map(this::isStrictOwner).orElse(false);
    }

    /**
     * @see #isStrictOwner(InvocationContext)
     */
    public boolean isStrictOwner(AuthorizedResource r) {
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            return false;
        }
        String caller = securityIdentity.getPrincipal().getName();
        try {
            return switch (r.kind()) {
                case ARTIFACT -> caller.equals(storage.getArtifactMetaData(r.groupId(), r.artifactId())
                        .getOwner());
                case GROUP -> caller.equals(storage.getGroupMetaData(r.groupId()).getOwner());
                case CONTENT -> false;
            };
        } catch (NotFoundException e) {
            return false;
        }
    }

    private Optional<AuthorizedResource> resolveCCompatSubject(String subject) {
        if (ccompatConfig.isGroupConcatEnabled()) {
            String separator = ccompatConfig.getGroupConcatSeparator();
            int idx = subject == null ? -1 : subject.indexOf(separator);
            if (idx < 1) {
                // Malformed subject; the endpoint rejects it with 400
                return Optional.empty();
            }
            return Optional.of(AuthorizedResource.artifact(subject.substring(0, idx),
                    subject.substring(idx + separator.length())));
        }
        return Optional.of(AuthorizedResource.artifact(getGroupIdHeader(), subject));
    }

    private Optional<AuthorizedResource> resolveGlobalId(Long globalId) {
        if (globalId == null) {
            return Optional.of(unknownId());
        }
        try {
            ArtifactVersionMetaDataDto vmd = storage.getArtifactVersionMetaData(globalId);
            return Optional.of(AuthorizedResource.artifact(vmd.getGroupId(), vmd.getArtifactId()));
        } catch (NotFoundException e) {
            return Optional.of(unknownId());
        }
    }

    /**
     * An ID that does not resolve to any artifact: content used by no artifact. Owner-based
     * authorization allows it (the endpoint answers 404); per-resource authorization denies it,
     * so an unresolvable ID can never skip the grants check.
     */
    private static AuthorizedResource unknownId() {
        return AuthorizedResource.content(List.of());
    }

    private Optional<AuthorizedResource> resolveContentId(Long contentId) {
        if (contentId == null) {
            return Optional.of(unknownId());
        }
        Set<AuthorizedResource> users = new LinkedHashSet<>();
        try {
            for (ArtifactVersionMetaDataDto vmd : storage.getArtifactVersionsByContentId(contentId)) {
                users.add(AuthorizedResource.artifact(vmd.getGroupId(), vmd.getArtifactId()));
            }
        } catch (NotFoundException e) {
            return Optional.of(unknownId());
        }
        return Optional.of(AuthorizedResource.content(List.copyOf(users)));
    }

    private String getGroupIdHeader() {
        if (request != null) {
            String groupIdHeader = request.getHeader(Headers.GROUP_ID);
            if (groupIdHeader != null && !groupIdHeader.isBlank()) {
                return groupIdHeader;
            }
        }
        return null;
    }

    private boolean verifyGroupOwner(String groupId) {
        try {
            GroupMetaDataDto dto = storage.getGroupMetaData(groupId);
            String owner = dto.getOwner();
            return owner == null || owner.equals(securityIdentity.getPrincipal().getName());
        } catch (NotFoundException nfe) {
            // If the group is not found, then return true and let the operation proceed.
            return true;
        }
    }

    private boolean verifyArtifactOwner(String groupId, String artifactId) {
        try {
            ArtifactMetaDataDto dto = storage.getArtifactMetaData(groupId, artifactId);
            String owner = dto.getOwner();
            return owner == null || owner.equals(securityIdentity.getPrincipal().getName());
        } catch (NotFoundException nfe) {
            // If the artifact is not found, then return true and let the operation proceed
            // as normal. The result of which will typically be a 404 response, but sometimes
            // will be some other result (e.g. creating an artifact that doesn't exist)
            return true;
        }
    }

    protected String getStringParam(InvocationContext context, int index) {
        return (String) context.getParameters()[index];
    }

    /**
     * Reads a numeric ID parameter. IDs are declared as {@code long}, {@code Long} or
     * {@code BigInteger} depending on the API (the Confluent compatibility API uses BigInteger).
     */
    protected Long getLongParam(InvocationContext context, int index) {
        Object value = context.getParameters()[index];
        return value instanceof Number number ? number.longValue() : null;
    }
}
