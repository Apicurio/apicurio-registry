package io.apicurio.registry.auth;

import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthorizedInterceptor} covering the 403 denial paths.
 * Verifies both the RBAC and OBAC denial paths throw the fixed message with no
 * trace of the caller's principal name.
 */
@Tag(ApicurioTestTags.AUTH)
public class AuthorizedInterceptorTest {

    /** Carries the {@code @Authorized} annotation looked up by the interceptor. */
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Write)
    private void writeEndpoint() {}

    @Test
    void rbacDenial_forbiddenMessageContainsNoPrincipalName() throws Exception {
        AuthorizedInterceptor interceptor = buildInterceptor("alice");
        interceptor.authConfig.roleBasedAuthorizationEnabled = true;
        when(interceptor.rbac.isAuthorized(any())).thenReturn(false);

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> interceptor.authorizeMethod(buildContext()));

        assertEquals(AuthorizedInterceptor.FORBIDDEN_MESSAGE, ex.getMessage());
        assertFalse(ex.getMessage().contains("alice"),
                "Principal name must not appear in the 403 response message");
    }

    @Test
    void obacDenial_forbiddenMessageContainsNoPrincipalName() throws Exception {
        AuthorizedInterceptor interceptor = buildInterceptor("alice");
        interceptor.authConfig.roleBasedAuthorizationEnabled = false;
        interceptor.authConfig.ownerOnlyAuthorizationEnabled = () -> true;
        when(interceptor.obac.isAuthorized(any())).thenReturn(false);

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> interceptor.authorizeMethod(buildContext()));

        assertEquals(AuthorizedInterceptor.FORBIDDEN_MESSAGE, ex.getMessage());
        assertFalse(ex.getMessage().contains("alice"),
                "Principal name must not appear in the 403 response message");
    }

    // ---- helpers ----

    private AuthorizedInterceptor buildInterceptor(String principalName) {
        AuthorizedInterceptor interceptor = new AuthorizedInterceptor();

        interceptor.log = mock(Logger.class);

        AuthConfig authConfig = mock(AuthConfig.class);
        when(authConfig.isAuthenticationEnabled()).thenReturn(true);
        authConfig.authenticatedReadAccessEnabled = () -> false;
        authConfig.ownerOnlyAuthorizationEnabled = () -> false;
        interceptor.authConfig = authConfig;

        AdminOverride adminOverride = mock(AdminOverride.class);
        when(adminOverride.isAdmin()).thenReturn(false);
        interceptor.adminOverride = adminOverride;

        interceptor.rbac = mock(RoleBasedAccessController.class);

        OwnerBasedAccessController obac = mock(OwnerBasedAccessController.class);
        when(obac.isAuthorized(any())).thenReturn(true);
        interceptor.obac = obac;

        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(principalName);
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(principal);
        interceptor.securityIdentity = identity;

        return interceptor;
    }

    private InvocationContext buildContext() throws NoSuchMethodException {
        Method method = AuthorizedInterceptorTest.class.getDeclaredMethod("writeEndpoint");
        InvocationContext ctx = mock(InvocationContext.class);
        when(ctx.getMethod()).thenReturn(method);
        return ctx;
    }
}
