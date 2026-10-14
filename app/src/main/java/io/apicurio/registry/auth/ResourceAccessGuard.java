package io.apicurio.registry.auth;

import io.apicurio.registry.auth.grants.GrantsAccessController;
import io.apicurio.registry.auth.grants.GrantsAccessControllerConfig;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.function.Function;

/**
 * Applies resource-level authorization (owner-only authorization and per-resource grants) to a
 * resource identified by the request body rather than by method parameters, for example when
 * creating a group, publishing an MCP server, or renaming an Iceberg table. The
 * {@link AuthorizedInterceptor} cannot resolve such resources.
 *
 * <p>The endpoint must still carry an {@link Authorized} annotation: the interceptor applies
 * authentication and RBAC, and this check applies the same resource rules and bypasses as the
 * interceptor (authentication disabled, trusted proxy authorization, admin override, RBAC admins,
 * and owners for grants).</p>
 */
@ApplicationScoped
public class ResourceAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(ResourceAccessGuard.class);

    private static final int PAGE_SIZE = 500;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    AuthConfig authConfig;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    AdminOverride adminOverride;

    @Inject
    RoleBasedAccessController rbac;

    @Inject
    GrantsAccessControllerConfig grantsConfig;

    @Inject
    GrantsAccessController grantsAc;

    /**
     * @throws ForbiddenException if owner-only authorization or per-resource authorization denies
     *         {@code level} on {@code resource}
     */
    public void requireAccess(AuthorizedLevel level, AuthorizedResource resource) {
        if (level == AuthorizedLevel.None || isBypassed()) {
            return;
        }
        // Owner-only authorization: same rule as the interceptor (write operations, RBAC admins
        // exempt, missing or unowned resources allowed)
        if (authConfig.ownerOnlyAuthorizationEnabled.get() && level == AuthorizedLevel.Write
                && !isAnonymous() && !(authConfig.roleBasedAuthorizationEnabled && rbac.isAdmin())
                && !grantsAc.isOwnerOf(resource, resource.kind() == AuthorizedResource.Kind.GROUP)) {
            log.warn("OBAC enabled and operation not permitted due to wrong owner.");
            throw new ForbiddenException(AuthorizedInterceptor.FORBIDDEN_MESSAGE);
        }
        requireGrantsAccess(level, resource);
    }

    /**
     * Applies only per-resource (grants) authorization, not owner-only authorization. Used where
     * the interceptor has already authorized the operation for the group, but grants must also
     * hold for the specific artifact, e.g. the name of an artifact being created.
     *
     * @throws ForbiddenException if per-resource authorization denies {@code level} on
     *         {@code resource}
     */
    public void requireGrantsAccess(AuthorizedLevel level, AuthorizedResource resource) {
        if (isEnforced(level) && !grantsAc.isStrictOwner(resource) && !grantsAc.isAllowed(level, resource)) {
            log.warn("Per-resource authorization denied access.");
            throw new ForbiddenException(AuthorizedInterceptor.FORBIDDEN_MESSAGE);
        }
    }

    /**
     * For endpoints that report on the whole registry (e.g. GitOps status) and cannot be filtered
     * per resource: when per-resource authorization applies, only callers who bypass it (admins)
     * may use them.
     *
     * @throws ForbiddenException if per-resource authorization applies to the caller
     */
    public void requireUnrestrictedRead() {
        if (isEnforced(AuthorizedLevel.Read)) {
            log.warn("Per-resource authorization denied access to a registry-wide report.");
            throw new ForbiddenException(AuthorizedInterceptor.FORBIDDEN_MESSAGE);
        }
    }

    /**
     * Requires per-resource write access on every artifact in a group. Group-level deletes remove
     * all of them, so a group grant alone must not override an artifact-level deny.
     *
     * @throws ForbiddenException if any artifact in the group is not writable by the caller
     */
    public void requireWriteOnAllArtifacts(String groupId) {
        if (!isEnforced(AuthorizedLevel.Write)) {
            return;
        }
        String rawGroupId = new GroupId(groupId).getRawGroupIdWithNull();
        int offset = 0;
        while (true) {
            ArtifactSearchResultsDto page = storage.searchArtifacts(Set.of(SearchFilter.ofGroupId(rawGroupId)),
                    OrderBy.artifactId, OrderDirection.asc, offset, PAGE_SIZE, true);
            for (SearchedArtifactDto artifact : page.getArtifacts()) {
                requireGrantsAccess(AuthorizedLevel.Write,
                        AuthorizedResource.artifact(artifact.getGroupId(), artifact.getArtifactId()));
            }
            if (page.getArtifacts().size() < PAGE_SIZE) {
                return;
            }
            offset += PAGE_SIZE;
        }
    }

    /**
     * Loader for resolving artifact references (dereferencing, canonicalizing, validating or
     * registering content that references other artifacts). Referenced content becomes part of
     * the response or influences it, so every reference, direct or transitive, must be readable
     * by the caller, as if it were read directly.
     *
     * @return a loader that throws {@link ForbiddenException} for a reference the caller may not
     *         read; the plain storage loader when per-resource authorization does not apply
     */
    public Function<ArtifactReferenceDto, ContentWrapperDto> referenceLoader() {
        if (!isEnforced(AuthorizedLevel.Read)) {
            return storage::getContentByReference;
        }
        return reference -> {
            requireGrantsAccess(AuthorizedLevel.Read,
                    AuthorizedResource.artifact(reference.getGroupId(), reference.getArtifactId()));
            return storage.getContentByReference(reference);
        };
    }

    /**
     * @return true if the caller may read the artifact; always true when per-resource
     *         authorization does not apply to this request
     */
    public boolean canRead(String groupId, String artifactId) {
        if (!isEnforced(AuthorizedLevel.Read)) {
            return true;
        }
        AuthorizedResource artifact = AuthorizedResource.artifact(groupId, artifactId);
        return grantsAc.isStrictOwner(artifact) || grantsAc.isAllowed(AuthorizedLevel.Read, artifact);
    }

    /**
     * @return true if per-resource authorization applies to an operation at {@code level} for the
     *         current caller; false when it is disabled or bypassed (authentication disabled,
     *         trusted proxy authorization, admin override, RBAC admin)
     */
    public boolean isEnforced(AuthorizedLevel level) {
        if (!grantsConfig.isEnabled() || level == AuthorizedLevel.None || isBypassed()) {
            return false;
        }
        // Anonymous and authenticated read access only bypass RBAC; grants still apply
        return true;
    }

    /** Bypasses that skip all local resource authorization, as in the interceptor. */
    private boolean isBypassed() {
        if (!authConfig.isAuthenticationEnabled()) {
            return true;
        }
        if (isAnonymous()) {
            return false;
        }
        if (authConfig.proxyHeaderAuthEnabled && authConfig.proxyHeaderTrustProxyAuthorization
                && securityIdentity.getCredential(ProxyHeaderCredential.class) != null) {
            return true;
        }
        return adminOverride.isAdmin() || (authConfig.roleBasedAuthorizationEnabled && rbac.isAdmin());
    }

    private boolean isAnonymous() {
        return securityIdentity == null || securityIdentity.isAnonymous();
    }
}
