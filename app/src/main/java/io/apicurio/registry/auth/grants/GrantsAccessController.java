package io.apicurio.registry.auth.grants;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import io.apicurio.authz.Artifact;
import io.apicurio.authz.Group;
import io.apicurio.authz.AccessRulesAuthorizer;
import io.apicurio.authz.Role;
import io.apicurio.registry.auth.AbstractAccessController;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedResource;
import io.apicurio.registry.metrics.OTelMetricsProvider;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.AuthorizationNames;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.scope.ResourceScope;
import io.apicurio.scope.ResourceScopeProvider;
import io.kroxylicious.authorizer.service.Action;
import io.kroxylicious.authorizer.service.AuthorizeResult;
import io.kroxylicious.authorizer.service.Decision;
import io.kroxylicious.authorizer.service.ResourceType;
import io.kroxylicious.identity.Principal;
import io.kroxylicious.identity.Subject;
import io.kroxylicious.proxy.authentication.User;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.interceptor.InvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.utils.StringUtil.sanitizeForLog;

/**
 * Per-resource authorization backed by an access rules file in the Kroxylicious ACL rules format,
 * evaluated by {@link AccessRulesAuthorizer}. Resources are named by {@link AuthorizationNames}.
 */
@Singleton
public class GrantsAccessController extends AbstractAccessController {

    private static final Logger LOG = LoggerFactory.getLogger(GrantsAccessController.class);
    private static final Logger AUDIT = LoggerFactory.getLogger("io.apicurio.registry.audit.authz");

    /**
     * Role given to unauthenticated callers, so access rules can allow anonymous access explicitly
     * with {@code Role with name = "$anonymous"}. Never matches a {@code User} rule.
     */
    public static final String ANONYMOUS_ROLE = "$anonymous";

    private static final String TYPE_ARTIFACT = "artifact";
    private static final String TYPE_GROUP = "group";
    private static final String TYPE_CONTENT = "content";

    @Inject
    OTelMetricsProvider metrics;

    @Inject
    GrantsAccessControllerConfig config;

    private volatile AccessRulesAuthorizer authorizer;

    void setAuthorizer(AccessRulesAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    // Visible for testing
    void setSecurityIdentity(SecurityIdentity securityIdentity) {
        this.securityIdentity = securityIdentity;
    }

    // Visible for testing
    void setStorage(RegistryStorage storage) {
        this.storage = storage;
    }

    public AccessRulesAuthorizer getAuthorizer() {
        return authorizer;
    }

    /**
     * @return the names the access rules allow the current caller to read, for search
     *         pre-filtering; null if per-resource authorization is not initialized
     */
    ResourceScope readScope(ResourceType<?> readOperation) {
        ResourceScopeProvider current = authorizer;
        // The access rules are evaluated in memory, so the stage is always complete
        return current != null ? current.resolveScope(currentSubject(), readOperation).toCompletableFuture().join()
                : null;
    }

    @Override
    public boolean isAuthorized(InvocationContext context) {
        Authorized annotation = context.getMethod().getAnnotation(Authorized.class);
        if (annotation == null || annotation.level() == AuthorizedLevel.None) {
            return true;
        }
        Optional<AuthorizedResource> resource = resolveResource(context);
        if (resource.isEmpty()) {
            // Not a single-resource operation (style None), or parameters the endpoint rejects
            return true;
        }
        return isAllowed(annotation.level(), resource.get());
    }

    /**
     * Evaluates grants for an explicitly identified resource. Used by endpoints whose target
     * comes from the request body rather than from method parameters.
     */
    public boolean isAllowed(AuthorizedLevel level, AuthorizedResource resource) {
        if (level == AuthorizedLevel.None) {
            return true;
        }
        if (authorizer == null) {
            LOG.error("Per-resource authorization is enabled but not initialized, denying access.");
            return false;
        }
        Subject subject = currentSubject();
        resource = canonical(resource);
        return switch (resource.kind()) {
            case ARTIFACT -> decide(subject, toArtifactOp(level), TYPE_ARTIFACT, level,
                    buildResourceName(resource.groupId(), resource.artifactId()));
            case GROUP -> decide(subject, toGroupOp(level), TYPE_GROUP, level,
                    normalizeGroup(resource.groupId()));
            case CONTENT -> decideContent(subject, level, resource.contentUsers());
        };
    }

    /**
     * Returns the resource with the group and artifact IDs as stored. Some databases (MySQL, SQL
     * Server) compare IDs case-insensitively, so a request for {@code TEAM-A/Foo} operates on the
     * stored {@code team-a/foo}; grants must be evaluated against the stored spelling, or a
     * case-only change would bypass them. Resources that do not exist keep the requested IDs,
     * except that the group of a new artifact resolves to the existing group it is created in.
     */
    AuthorizedResource canonical(AuthorizedResource resource) {
        if (storage == null) {
            return resource;
        }
        return switch (resource.kind()) {
            case ARTIFACT -> lookups().artifact(storage, resource.groupId(), resource.artifactId())
                    .map(stored -> AuthorizedResource.artifact(stored.getGroupId(), stored.getArtifactId()))
                    .orElseGet(() -> AuthorizedResource.artifact(canonicalGroupId(resource.groupId()),
                            resource.artifactId()));
            case GROUP -> AuthorizedResource.group(canonicalGroupId(resource.groupId()));
            case CONTENT -> resource;
        };
    }

    private String canonicalGroupId(String groupId) {
        if (AuthorizationNames.DEFAULT_GROUP.equals(AuthorizationNames.group(groupId))) {
            return groupId;
        }
        return lookups().group(storage, groupId).map(GroupMetaDataDto::getGroupId).orElse(groupId);
    }

    /**
     * Owner bypass of per-resource authorization, unless disabled by configuration.
     */
    @Override
    public boolean isStrictOwner(AuthorizedResource resource) {
        return (config == null || config.isOwnerBypassEnabled()) && super.isStrictOwner(resource);
    }

    public boolean canReadArtifact(String groupId, String artifactId) {
        return isAllowed(AuthorizedLevel.Read, AuthorizedResource.artifact(groupId, artifactId));
    }

    /**
     * Content can be shared by many artifacts: access is allowed if the caller may access at least
     * one artifact using it, or owns one. Content no artifact uses (including unknown IDs) is
     * denied: there is no artifact the access rules could allow.
     */
    private boolean decideContent(Subject subject, AuthorizedLevel level, List<AuthorizedResource> users) {
        ResourceType<?> operation = toArtifactOp(level);
        List<Action> actions = users.stream()
                .map(u -> new Action(operation, buildResourceName(u.groupId(), u.artifactId())))
                .distinct()
                .toList();
        boolean allowed = !actions.isEmpty()
                && (!authorize(subject, actions).allowed().isEmpty()
                        // Owners bypass grants for their artifacts, so also for their content
                        || users.stream().anyMatch(this::isStrictOwner));
        record(allowed, TYPE_CONTENT, level, "content used by " + actions.size() + " artifact(s)");
        return allowed;
    }

    private boolean decide(Subject subject, ResourceType<?> operation, String resourceType,
            AuthorizedLevel level, String resourceName) {
        boolean allowed = authorize(subject, List.of(new Action(operation, resourceName)))
                .decision(operation, resourceName) == Decision.ALLOW;
        record(allowed, resourceType, level, resourceName);
        return allowed;
    }

    private AuthorizeResult authorize(Subject subject, List<Action> actions) {
        // The access rules are evaluated in memory, so the stage is always complete
        return authorizer.authorize(subject, actions).toCompletableFuture().join();
    }

    private void record(boolean allowed, String resourceType, AuthorizedLevel level, String resource) {
        String operation = level.name().toLowerCase(Locale.ROOT);
        metrics.recordAuthzDecision(allowed, resourceType, operation);
        if (!allowed) {
            // user and resource are not fully registry-controlled (IdP principal name; a resource
            // name embeds group/artifact IDs), so sanitize before writing them to the audit log
            AUDIT.info("authz.denied user=\"{}\" operation=\"{}\" resource_type=\"{}\" resource=\"{}\"",
                    sanitizeForLog(getUsername()), operation, resourceType, sanitizeForLog(resource));
        }
    }

    private String getUsername() {
        if (securityIdentity != null && !securityIdentity.isAnonymous()) {
            return securityIdentity.getPrincipal().getName();
        }
        return "<anonymous>";
    }

    /**
     * Maps the current Quarkus {@link SecurityIdentity} to a Kroxylicious {@link Subject}: one
     * {@link User} principal, the Kroxylicious user class that access rules use for {@code User}
     * by default, plus one {@link Role} per role. Unauthenticated callers get only the
     * {@link #ANONYMOUS_ROLE} role.
     */
    public Subject currentSubject() {
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            return new Subject(Set.of(new Role(ANONYMOUS_ROLE)));
        }
        Set<Principal> principals = new HashSet<>();
        principals.add(new User(securityIdentity.getPrincipal().getName()));
        for (String role : securityIdentity.getRoles()) {
            principals.add(new Role(role));
        }
        return new Subject(principals);
    }

    /**
     * @return the grants resource name of an artifact; see {@link AuthorizationNames#artifact}
     */
    public static String buildResourceName(String groupId, String artifactId) {
        return AuthorizationNames.artifact(groupId, artifactId);
    }

    /**
     * @return the grants resource name of a group; see {@link AuthorizationNames#group}
     */
    public static String normalizeGroup(String groupId) {
        return AuthorizationNames.group(groupId);
    }

    private static Artifact toArtifactOp(AuthorizedLevel level) {
        return switch (level) {
            case Read, None -> Artifact.READ;
            case Write -> Artifact.WRITE;
            case Admin, AdminOrOwner -> Artifact.ADMIN;
        };
    }

    private static Group toGroupOp(AuthorizedLevel level) {
        return switch (level) {
            case Read, None -> Group.READ;
            case Write -> Group.WRITE;
            case Admin, AdminOrOwner -> Group.ADMIN;
        };
    }
}
