package io.apicurio.registry.auth;

import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ReadAccessChecker} must grant read access exactly when {@link AuthorizedInterceptor} lets a
 * {@code @Authorized(level = Read)} operation through. This runs both against every combination of
 * authentication settings, admin override, roles and caller identity, so a change to one that is not
 * mirrored in the other fails here.
 */
class AuthorizedInterceptorReadParityTest {

    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public void readOperation() {
    }

    private enum Caller { ANONYMOUS, NO_ROLE, READONLY, DEVELOPER, ADMIN_ROLE, PROXY_USER }

    @Test
    void testReadAccessCheckerMatchesInterceptor() throws Exception {
        Method method = getClass().getMethod("readOperation");
        List<String> mismatches = new ArrayList<>();
        int cases = 0;

        for (boolean authEnabled : bools()) {
            for (boolean proxyAuth : bools()) {
                for (boolean proxyTrust : bools()) {
                    for (boolean anonymousRead : bools()) {
                        for (boolean authenticatedRead : bools()) {
                            for (boolean rbacEnabled : bools()) {
                                for (boolean ownerOnly : bools()) {
                                    for (boolean adminOverrideGrants : bools()) {
                                        for (Caller caller : Caller.values()) {
                                            AuthConfig config = new AuthConfig();
                                            config.basicAuthEnabled = authEnabled;
                                            config.proxyHeaderAuthEnabled = proxyAuth;
                                            config.proxyHeaderTrustProxyAuthorization = proxyTrust;
                                            config.anonymousReadAccessEnabled = () -> anonymousRead;
                                            config.authenticatedReadAccessEnabled = () -> authenticatedRead;
                                            config.roleBasedAuthorizationEnabled = rbacEnabled;
                                            config.ownerOnlyAuthorizationEnabled = () -> ownerOnly;

                                            SecurityIdentity identity = identity(caller);
                                            AdminOverride adminOverride = mock(AdminOverride.class);
                                            when(adminOverride.isAdmin()).thenReturn(
                                                    caller != Caller.ANONYMOUS && adminOverrideGrants);
                                            RoleBasedAccessController rbac = rbac(caller);
                                            OwnerBasedAccessController obac = mock(OwnerBasedAccessController.class);
                                            // Owner-only authorization only restricts Write operations.
                                            when(obac.isAuthorized(any())).thenReturn(true);

                                            boolean interceptorAllows = interceptorAllows(method, config,
                                                    identity, adminOverride, rbac, obac);

                                            ReadAccessChecker checker = new ReadAccessChecker();
                                            checker.authConfig = config;
                                            checker.securityIdentity = identity;
                                            checker.adminOverride = adminOverride;
                                            checker.rbac = rbac;

                                            cases++;
                                            if (checker.canRead() != interceptorAllows) {
                                                mismatches.add(String.format(
                                                        "auth=%s proxy=%s trust=%s anonRead=%s authRead=%s rbac=%s"
                                                                + " ownerOnly=%s adminOverride=%s caller=%s:"
                                                                + " interceptor=%s checker=%s",
                                                        authEnabled, proxyAuth, proxyTrust, anonymousRead,
                                                        authenticatedRead, rbacEnabled, ownerOnly,
                                                        adminOverrideGrants, caller, interceptorAllows,
                                                        !interceptorAllows));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        assertEquals(1536, cases);
        assertEquals(List.of(), mismatches);
    }

    private static boolean interceptorAllows(Method method, AuthConfig config, SecurityIdentity identity,
            AdminOverride adminOverride, RoleBasedAccessController rbac, OwnerBasedAccessController obac)
            throws Exception {
        AuthorizedInterceptor interceptor = new AuthorizedInterceptor();
        interceptor.log = LoggerFactory.getLogger(AuthorizedInterceptor.class);
        interceptor.authConfig = config;
        interceptor.securityIdentity = identity;
        interceptor.adminOverride = adminOverride;
        interceptor.rbac = rbac;
        interceptor.obac = obac;

        InvocationContext context = mock(InvocationContext.class);
        when(context.getMethod()).thenReturn(method);
        when(context.proceed()).thenReturn("proceeded");
        try {
            return "proceeded".equals(interceptor.authorizeMethod(context));
        } catch (UnauthorizedException | ForbiddenException e) {
            return false;
        }
    }

    private static SecurityIdentity identity(Caller caller) {
        SecurityIdentity identity = mock(SecurityIdentity.class);
        if (caller == Caller.ANONYMOUS) {
            when(identity.isAnonymous()).thenReturn(true);
            return identity;
        }
        Principal principal = () -> caller.name().toLowerCase(Locale.ROOT);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(principal);
        when(identity.getRoles()).thenReturn(Set.of());
        if (caller == Caller.PROXY_USER) {
            when(identity.getCredential(ProxyHeaderCredential.class))
                    .thenReturn(new ProxyHeaderCredential("proxy_user", "proxy@example.com"));
        }
        return identity;
    }

    /** Role answers per caller; isAuthorized follows RoleBasedAccessController's Read rule. */
    private static RoleBasedAccessController rbac(Caller caller) {
        boolean readOnly = caller == Caller.READONLY;
        boolean developer = caller == Caller.DEVELOPER;
        boolean admin = caller == Caller.ADMIN_ROLE;
        RoleBasedAccessController rbac = mock(RoleBasedAccessController.class);
        when(rbac.isReadOnly()).thenReturn(readOnly);
        when(rbac.isDeveloper()).thenReturn(developer);
        when(rbac.isAdmin()).thenReturn(admin);
        when(rbac.isAuthorized(any())).thenReturn(readOnly || developer || admin);
        return rbac;
    }

    private static boolean[] bools() {
        return new boolean[] { false, true };
    }
}
