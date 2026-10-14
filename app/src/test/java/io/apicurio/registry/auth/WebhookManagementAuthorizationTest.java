package io.apicurio.registry.auth;

import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the fail-closed {@link WebhookManagementApiOperation} gate in
 * {@link AuthorizedInterceptor}.
 */
public class WebhookManagementAuthorizationTest {

    private static final String PROCEEDED = "proceeded";

    /** A webhook management operation, as the REST layer will annotate it. */
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    @WebhookManagementApiOperation
    private void webhookEndpoint() {
    }

    /** An ordinary admin operation, to show the gate does not change other endpoints. */
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    private void ordinaryAdminEndpoint() {
    }

    // ---- deployment cannot identify an administrator: 403 for everyone ----

    @Test
    void authenticationDisabled_isForbiddenEvenThoughOtherEndpointsAreOpen() throws Exception {
        AuthorizedInterceptor interceptor = interceptor(false, "alice");
        InvocationContext webhook = context("webhookEndpoint");

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> interceptor.authorizeMethod(webhook));

        assertEquals(AuthorizedInterceptor.WEBHOOK_MANAGEMENT_NOT_ENFORCED_MESSAGE, ex.getMessage());
        verify(webhook, never()).proceed();
        // With authentication disabled, an ordinary endpoint is still allowed through.
        assertEquals(PROCEEDED, interceptor.authorizeMethod(context("ordinaryAdminEndpoint")));
    }

    @Test
    void authenticationWithoutRbacOrAdminOverride_isForbiddenForAnAuthenticatedCaller() throws Exception {
        AuthorizedInterceptor interceptor = interceptor(true, "alice");
        InvocationContext webhook = context("webhookEndpoint");

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> interceptor.authorizeMethod(webhook));

        assertEquals(AuthorizedInterceptor.WEBHOOK_MANAGEMENT_NOT_ENFORCED_MESSAGE, ex.getMessage());
        verify(webhook, never()).proceed();
    }

    @Test
    void ownerOnlyAuthorization_isNotEnoughToIdentifyAnAdmin() throws Exception {
        AuthorizedInterceptor interceptor = interceptor(true, "alice");
        interceptor.authConfig.ownerOnlyAuthorizationEnabled = () -> true;
        InvocationContext webhook = context("webhookEndpoint");

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> interceptor.authorizeMethod(webhook));

        assertEquals(AuthorizedInterceptor.WEBHOOK_MANAGEMENT_NOT_ENFORCED_MESSAGE, ex.getMessage());
        verify(webhook, never()).proceed();
    }

    // ---- RBAC enabled ----

    @Test
    void rbacAdmin_isAllowed() throws Exception {
        AuthorizedInterceptor interceptor = interceptor(true, "alice");
        interceptor.authConfig.roleBasedAuthorizationEnabled = true;
        when(interceptor.rbac.isAdmin()).thenReturn(true);

        assertEquals(PROCEEDED, interceptor.authorizeMethod(context("webhookEndpoint")));
    }

    @Test
    void rbacNonAdmin_isForbiddenWithoutPrincipalNameInMessage() throws Exception {
        AuthorizedInterceptor interceptor = interceptor(true, "alice");
        interceptor.authConfig.roleBasedAuthorizationEnabled = true;
        when(interceptor.rbac.isAdmin()).thenReturn(false);
        InvocationContext webhook = context("webhookEndpoint");

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> interceptor.authorizeMethod(webhook));

        assertEquals(AuthorizedInterceptor.FORBIDDEN_MESSAGE, ex.getMessage());
        assertFalse(ex.getMessage().contains("alice"));
        verify(webhook, never()).proceed();
    }

    @Test
    void rbacAnonymousCaller_isUnauthenticated() throws Exception {
        AuthorizedInterceptor interceptor = interceptor(true, "alice");
        interceptor.authConfig.roleBasedAuthorizationEnabled = true;
        when(interceptor.securityIdentity.isAnonymous()).thenReturn(true);
        InvocationContext webhook = context("webhookEndpoint");

        assertThrows(UnauthorizedException.class, () -> interceptor.authorizeMethod(webhook));
        verify(webhook, never()).proceed();
    }

    // ---- admin-override enabled, RBAC disabled ----

    @Test
    void adminOverrideAdmin_isAllowed() throws Exception {
        AuthorizedInterceptor interceptor = interceptor(true, "alice");
        interceptor.authConfig.adminOverrideEnabled = true;
        when(interceptor.adminOverride.isAdmin()).thenReturn(true);

        assertEquals(PROCEEDED, interceptor.authorizeMethod(context("webhookEndpoint")));
    }

    @Test
    void adminOverrideNonAdmin_isForbiddenAndRbacRoleIsIgnoredWhenRbacIsOff() throws Exception {
        AuthorizedInterceptor interceptor = interceptor(true, "alice");
        interceptor.authConfig.adminOverrideEnabled = true;
        when(interceptor.adminOverride.isAdmin()).thenReturn(false);
        // RBAC is disabled, so an admin role from the RBAC role provider must not count.
        when(interceptor.rbac.isAdmin()).thenReturn(true);
        InvocationContext webhook = context("webhookEndpoint");

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> interceptor.authorizeMethod(webhook));

        assertEquals(AuthorizedInterceptor.FORBIDDEN_MESSAGE, ex.getMessage());
        verify(webhook, never()).proceed();
    }

    // ---- trusted proxy authorization ----

    @Test
    void trustedProxyCaller_isNotTrustedForWebhookManagement() throws Exception {
        AuthorizedInterceptor interceptor = trustedProxyInterceptor();
        when(interceptor.rbac.isAdmin()).thenReturn(false);
        InvocationContext webhook = context("webhookEndpoint");

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> interceptor.authorizeMethod(webhook));

        assertEquals(AuthorizedInterceptor.FORBIDDEN_MESSAGE, ex.getMessage());
        verify(webhook, never()).proceed();
        // The same caller is still trusted on an ordinary endpoint.
        assertEquals(PROCEEDED, interceptor.authorizeMethod(context("ordinaryAdminEndpoint")));
    }

    @Test
    void trustedProxyCallerWithLocalAdminRole_isAllowed() throws Exception {
        AuthorizedInterceptor interceptor = trustedProxyInterceptor();
        when(interceptor.rbac.isAdmin()).thenReturn(true);

        assertEquals(PROCEEDED, interceptor.authorizeMethod(context("webhookEndpoint")));
    }

    // ---- helpers ----

    private AuthorizedInterceptor trustedProxyInterceptor() {
        AuthorizedInterceptor interceptor = interceptor(true, "alice");
        interceptor.authConfig.proxyHeaderAuthEnabled = true;
        interceptor.authConfig.proxyHeaderTrustProxyAuthorization = true;
        interceptor.authConfig.roleBasedAuthorizationEnabled = true;
        when(interceptor.securityIdentity.getCredential(ProxyHeaderCredential.class))
                .thenReturn(new ProxyHeaderCredential("alice", "alice@example.com"));
        return interceptor;
    }

    /**
     * Builds an interceptor with RBAC, admin-override and owner-only authorization all disabled.
     */
    private AuthorizedInterceptor interceptor(boolean authenticationEnabled, String principalName) {
        AuthorizedInterceptor interceptor = new AuthorizedInterceptor();
        interceptor.log = mock(Logger.class);

        AuthConfig authConfig = mock(AuthConfig.class);
        when(authConfig.isAuthenticationEnabled()).thenReturn(authenticationEnabled);
        authConfig.anonymousReadAccessEnabled = () -> false;
        authConfig.authenticatedReadAccessEnabled = () -> false;
        authConfig.ownerOnlyAuthorizationEnabled = () -> false;
        interceptor.authConfig = authConfig;

        interceptor.adminOverride = mock(AdminOverride.class);
        interceptor.rbac = mock(RoleBasedAccessController.class);
        interceptor.obac = mock(OwnerBasedAccessController.class);

        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(principalName);
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(principal);
        interceptor.securityIdentity = identity;

        return interceptor;
    }

    private InvocationContext context(String methodName) throws Exception {
        Method method = WebhookManagementAuthorizationTest.class.getDeclaredMethod(methodName);
        InvocationContext ctx = mock(InvocationContext.class);
        when(ctx.getMethod()).thenReturn(method);
        when(ctx.proceed()).thenReturn(PROCEEDED);
        return ctx;
    }
}
