package io.apicurio.registry.auth;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Answers "may the current caller perform a read-level operation?" without going through an
 * {@link Authorized}-annotated method. It applies the same grants {@link AuthorizedInterceptor} applies to
 * {@code @Authorized(level = AuthorizedLevel.Read)} operations, for code that must filter data for a caller
 * on an endpoint that is itself open (for example Agent Card discovery, which serves public cards to
 * anonymous callers).
 * <p>
 * {@code AuthorizedInterceptorReadParityTest} checks this against the interceptor across every combination
 * of authentication settings and caller identity, so the two cannot drift apart.
 * </p>
 */
@ApplicationScoped
public class ReadAccessChecker {

    @Inject
    AuthConfig authConfig;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    AdminOverride adminOverride;

    @Inject
    RoleBasedAccessController rbac;

    /**
     * @return {@code true} if the current caller may read artifacts
     */
    public boolean canRead() {
        boolean anonymous = securityIdentity == null || securityIdentity.isAnonymous();

        // A trusted proxy has already authorized the request.
        if (authConfig.proxyHeaderAuthEnabled && authConfig.proxyHeaderTrustProxyAuthorization && !anonymous
                && securityIdentity.getCredential(ProxyHeaderCredential.class) != null) {
            return true;
        }
        if (!authConfig.isAuthenticationEnabled()) {
            return true;
        }
        if (anonymous) {
            return authConfig.isAnonymousReadsEnabled();
        }
        if (adminOverride.isAdmin()) {
            return true;
        }
        if (authConfig.isAuthenticatedReadsEnabled()) {
            return true;
        }
        // Owner-only authorization never restricts reads, so RBAC is the last gate.
        return !authConfig.isRbacEnabled() || rbac.isReadOnly() || rbac.isDeveloper() || rbac.isAdmin();
    }
}
