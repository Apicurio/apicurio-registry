package io.apicurio.registry.auth.grants;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import io.apicurio.authz.GrantsAuthorizer;
import io.apicurio.authz.GrantsData;
import io.apicurio.authz.RolePrincipal;
import io.apicurio.authz.User;
import io.apicurio.registry.auth.AbstractAccessController;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedResource;
import io.apicurio.registry.metrics.OTelMetricsProvider;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.AuthorizationNames;
import io.kroxylicious.authorizer.service.Action;
import io.kroxylicious.authorizer.service.AuthorizeResult;
import io.kroxylicious.authorizer.service.Decision;
import io.kroxylicious.authorizer.service.ResourceType;
import io.kroxylicious.identity.Principal;
import io.kroxylicious.identity.Subject;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.interceptor.InvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-resource authorization backed by a Kroxylicious {@link io.kroxylicious.authorizer.service.Authorizer}
 * (the grants-file based {@link GrantsAuthorizer}). Resources are named by
 * {@link AuthorizationNames}.
 */
@Singleton
public class GrantsAccessController extends AbstractAccessController {

    private static final Logger LOG = LoggerFactory.getLogger(GrantsAccessController.class);
    private static final Logger AUDIT = LoggerFactory.getLogger("io.apicurio.registry.audit.authz");

    private static final String TYPE_ARTIFACT = "artifact";
    private static final String TYPE_GROUP = "group";
    private static final String TYPE_CONTENT = "content";

    @Inject
    OTelMetricsProvider metrics;

    private volatile GrantsAuthorizer authorizer;

    void setAuthorizer(GrantsAuthorizer authorizer) {
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

    public GrantsAuthorizer getAuthorizer() {
        return authorizer;
    }

    GrantsData getGrantsData() {
        GrantsAuthorizer current = authorizer;
        return current != null ? current.getGrantsData() : null;
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
        return switch (resource.kind()) {
            case ARTIFACT -> decide(subject, toArtifactOp(level), TYPE_ARTIFACT, level,
                    buildResourceName(resource.groupId(), resource.artifactId()));
            case GROUP -> decide(subject, toGroupOp(level), TYPE_GROUP, level,
                    normalizeGroup(resource.groupId()));
            case CONTENT -> decideContent(subject, level, resource.contentUsers());
        };
    }

    public boolean canReadArtifact(String groupId, String artifactId) {
        return isAllowed(AuthorizedLevel.Read, AuthorizedResource.artifact(groupId, artifactId));
    }

    /**
     * Content can be shared by many artifacts: access is allowed if the caller may access at least
     * one artifact using it, or owns one. Content no artifact uses (including unknown IDs) is
     * denied, except for grants-file admins.
     */
    private boolean decideContent(Subject subject, AuthorizedLevel level, List<AuthorizedResource> users) {
        ResourceType<?> operation = toArtifactOp(level);
        List<Action> actions = users.stream()
                .map(u -> new Action(operation, buildResourceName(u.groupId(), u.artifactId())))
                .distinct()
                .toList();
        boolean allowed = actions.isEmpty()
                ? isGrantsAdmin()
                : !authorize(subject, actions).allowed().isEmpty()
                        // Owners bypass grants for their artifacts, so also for their content
                        || users.stream().anyMatch(this::isStrictOwner);
        record(allowed, TYPE_CONTENT, level, "content used by " + actions.size() + " artifact(s)");
        return allowed;
    }

    /** Grants-file admins have access even when there is no artifact to evaluate. */
    private boolean isGrantsAdmin() {
        GrantsData data = getGrantsData();
        return data != null && securityIdentity != null && !securityIdentity.isAnonymous()
                && data.isAdmin(securityIdentity.getRoles());
    }

    private boolean decide(Subject subject, ResourceType<?> operation, String resourceType,
            AuthorizedLevel level, String resourceName) {
        boolean allowed = authorize(subject, List.of(new Action(operation, resourceName)))
                .decision(operation, resourceName) == Decision.ALLOW;
        record(allowed, resourceType, level, resourceName);
        return allowed;
    }

    private AuthorizeResult authorize(Subject subject, List<Action> actions) {
        // GrantsAuthorizer evaluates in-memory and always returns a completed stage
        return authorizer.authorize(subject, actions).toCompletableFuture().join();
    }

    private void record(boolean allowed, String resourceType, AuthorizedLevel level, String resource) {
        String operation = level.name().toLowerCase(Locale.ROOT);
        metrics.recordAuthzDecision(allowed, resourceType, operation);
        if (!allowed) {
            AUDIT.info("authz.denied user=\"{}\" operation=\"{}\" resource_type=\"{}\" resource=\"{}\"",
                    getUsername(), operation, resourceType, resource);
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
     * {@link User} principal plus one {@link RolePrincipal} per role.
     */
    public Subject currentSubject() {
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            return Subject.anonymous();
        }
        Set<Principal> principals = new HashSet<>();
        principals.add(new User(securityIdentity.getPrincipal().getName()));
        for (String role : securityIdentity.getRoles()) {
            principals.add(new RolePrincipal(role));
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

    private static RegistryResourceType.Artifact toArtifactOp(AuthorizedLevel level) {
        return switch (level) {
            case Read, None -> RegistryResourceType.Artifact.Read;
            case Write -> RegistryResourceType.Artifact.Write;
            case Admin, AdminOrOwner -> RegistryResourceType.Artifact.Admin;
        };
    }

    private static RegistryResourceType.Group toGroupOp(AuthorizedLevel level) {
        return switch (level) {
            case Read, None -> RegistryResourceType.Group.Read;
            case Write -> RegistryResourceType.Group.Write;
            case Admin, AdminOrOwner -> RegistryResourceType.Group.Admin;
        };
    }
}
