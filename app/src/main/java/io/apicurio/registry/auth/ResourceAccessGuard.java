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
 * Applies per-resource (grants) authorization to a resource that is identified by the request
 * body rather than by method parameters (for example creating a group, publishing an MCP server,
 * or renaming an Iceberg table), so {@link AuthorizedInterceptor} cannot resolve it.
 *
 * <p>The endpoint must still carry an {@link Authorized} annotation: this check runs after the
 * interceptor has applied authentication, RBAC and OBAC, and it honours the same bypasses
 * (authorization disabled, trusted proxy authorization, admin override, anonymous/authenticated
 * read access and ownership).</p>
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
    GrantsAccessControllerConfig grantsConfig;

    @Inject
    GrantsAccessController grantsAc;

    /**
     * @throws ForbiddenException if per-resource authorization denies {@code level} on
     *         {@code resource}
     */
    public void requireAccess(AuthorizedLevel level, AuthorizedResource resource) {
        if (!isEnforced(level)) {
            return;
        }
        if (grantsAc.isStrictOwner(resource) || grantsAc.isAllowed(level, resource)) {
            return;
        }
        log.warn("Per-resource authorization denied access.");
        throw new ForbiddenException(AuthorizedInterceptor.FORBIDDEN_MESSAGE);
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
        if (!grantsConfig.isEnabled() || !authConfig.isAuthenticationEnabled()
                || level == AuthorizedLevel.None) {
            return false;
        }
        boolean anonymous = securityIdentity == null || securityIdentity.isAnonymous();
        if (anonymous) {
            // The interceptor only lets anonymous callers through for reads when anonymous read
            // access is enabled, which deliberately overrides grants.
            return !(level == AuthorizedLevel.Read && authConfig.anonymousReadAccessEnabled.get());
        }
        if (authConfig.proxyHeaderAuthEnabled && authConfig.proxyHeaderTrustProxyAuthorization
                && securityIdentity.getCredential(ProxyHeaderCredential.class) != null) {
            return false;
        }
        if (adminOverride.isAdmin()) {
            return false;
        }
        return !(level == AuthorizedLevel.Read && authConfig.authenticatedReadAccessEnabled.get());
    }
}
