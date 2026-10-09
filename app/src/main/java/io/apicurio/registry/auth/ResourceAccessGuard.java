package io.apicurio.registry.auth;

import io.apicurio.registry.auth.grants.GrantsAccessController;
import io.apicurio.registry.auth.grants.GrantsAccessControllerConfig;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies resource-level authorization (owner-only authorization and per-resource grants) to a
 * resource identified by the request body rather than by method parameters, for example when
 * creating a group, publishing an MCP server, or renaming an Iceberg table. The
 * {@link AuthorizedInterceptor} cannot resolve such resources.
 *
 * <p>The endpoint must still carry an {@link Authorized} annotation: the interceptor applies
 * authentication and RBAC, and this check applies the same resource rules and bypasses as the
 * interceptor (authentication disabled, trusted proxy authorization, admin override,
 * anonymous/authenticated read access, RBAC admins for OBAC, and owners for grants).</p>
 */
@ApplicationScoped
public class ResourceAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(ResourceAccessGuard.class);

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
        if (isEnforced(level) && !grantsAc.isStrictOwner(resource) && !grantsAc.isAllowed(level, resource)) {
            log.warn("Per-resource authorization denied access.");
            throw new ForbiddenException(AuthorizedInterceptor.FORBIDDEN_MESSAGE);
        }
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
     *         trusted proxy authorization, admin override, anonymous/authenticated read access)
     */
    public boolean isEnforced(AuthorizedLevel level) {
        if (!grantsConfig.isEnabled() || level == AuthorizedLevel.None || isBypassed()) {
            return false;
        }
        if (isAnonymous()) {
            // The interceptor only lets anonymous callers through for reads when anonymous read
            // access is enabled, which deliberately overrides grants.
            return !(level == AuthorizedLevel.Read && authConfig.anonymousReadAccessEnabled.get());
        }
        return !(level == AuthorizedLevel.Read && authConfig.authenticatedReadAccessEnabled.get());
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
        return adminOverride.isAdmin();
    }

    private boolean isAnonymous() {
        return securityIdentity == null || securityIdentity.isAnonymous();
    }
}
