package io.apicurio.registry.auth;

import io.quarkus.oidc.runtime.OidcAuthenticationMechanism;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the threading contract of {@code OidcAuthenticationStrategy.authenticate}: Quarkus calls
 * {@code HttpAuthenticationMechanism.authenticate} on the Vert.x event loop thread, and the
 * client-credentials path blocks in {@code join()} while the token is fetched, so the fetch has
 * to run on the worker pool (#9776), as {@code KubernetesAuthenticationStrategy} already does.
 */
class OidcClientCredentialsOffloadTest {

    private static final String BASIC_HEADER = "Basic " + Base64.getEncoder()
            .encodeToString("offload-client:offload-secret".getBytes(StandardCharsets.UTF_8));

    @Test
    void tokenFetchRunsOnTheWorkerPoolNotTheCallerThread() {
        AtomicReference<Thread> fetchThread = new AtomicReference<>();
        AppAuthenticationMechanism parent = mock(AppAuthenticationMechanism.class);
        when(parent.getAccessToken(any(Pair.class), anyString()))
                .thenAnswer(invocation -> {
                    fetchThread.set(Thread.currentThread());
                    return new WrappedValue<>(Duration.ofMinutes(10), Instant.now(),
                            "offloaded-token");
                });

        SecurityIdentity identity = mock(SecurityIdentity.class);
        OidcAuthenticationMechanism oidcMech = mock(OidcAuthenticationMechanism.class);
        when(oidcMech.authenticate(any(RoutingContext.class), any(IdentityProviderManager.class)))
                .thenReturn(Uni.createFrom().item(identity));

        OidcAuthenticationStrategy strategy = newStrategy(parent, oidcMech);
        RoutingContext ctx = createMockRoutingContext();

        SecurityIdentity result = strategy.authenticate(ctx, mock(IdentityProviderManager.class))
                .await().atMost(Duration.ofSeconds(10));

        assertSame(identity, result, "authenticate should complete with the OIDC identity");
        assertNotSame(Thread.currentThread(), fetchThread.get(),
                "The blocking token fetch ran on the caller thread; it must be offloaded to "
                        + "the worker pool so the Vert.x event loop is not blocked");
        assertEquals("Bearer offloaded-token", ctx.request().headers().get("Authorization"),
                "The offloaded fetch should still set the Bearer header before the OIDC call");
    }

    @Test
    void serverErrorFallsBackToThePlainOidcMechanism() {
        AppAuthenticationMechanism parent = mock(AppAuthenticationMechanism.class);
        when(parent.getAccessToken(any(Pair.class), anyString()))
                .thenThrow(new OidcAuthException("OIDC token request failed with status 500"));

        SecurityIdentity fallback = mock(SecurityIdentity.class);
        OidcAuthenticationMechanism oidcMech = mock(OidcAuthenticationMechanism.class);
        when(oidcMech.authenticate(any(RoutingContext.class), any(IdentityProviderManager.class)))
                .thenReturn(Uni.createFrom().item(fallback));

        OidcAuthenticationStrategy strategy = newStrategy(parent, oidcMech);

        SecurityIdentity result = strategy.authenticate(createMockRoutingContext(),
                mock(IdentityProviderManager.class)).await().atMost(Duration.ofSeconds(10));

        assertSame(fallback, result,
                "A fetch that ends in a server error must fall back to the plain OIDC mechanism");
    }

    @Test
    void forbiddenFetchFailsTheReturnedUni() {
        AppAuthenticationMechanism parent = mock(AppAuthenticationMechanism.class);
        when(parent.getAccessToken(any(Pair.class), anyString()))
                .thenThrow(new ForbiddenException("OIDC token request returned 403"));

        SecurityIdentity fallback = mock(SecurityIdentity.class);
        OidcAuthenticationMechanism oidcMech = mock(OidcAuthenticationMechanism.class);
        when(oidcMech.authenticate(any(RoutingContext.class), any(IdentityProviderManager.class)))
                .thenReturn(Uni.createFrom().item(fallback));

        OidcAuthenticationStrategy strategy = newStrategy(parent, oidcMech);

        // A 403 is a verdict on the credentials, so it must reach the caller as a failed Uni,
        // not be swallowed by the fallback that 401 and 5xx use.
        assertThrows(ForbiddenException.class,
                () -> strategy.authenticate(createMockRoutingContext(),
                        mock(IdentityProviderManager.class)).await()
                                .atMost(Duration.ofSeconds(10)),
                "A 403 must fail the authentication instead of falling back");
    }

    private static OidcAuthenticationStrategy newStrategy(AppAuthenticationMechanism parent,
            OidcAuthenticationMechanism oidcMech) {
        AuthConfig authConfig = new AuthConfig();
        authConfig.authServerUrl = "http://localhost/realms/test";
        authConfig.oidcTokenPath = "http://localhost/realms/test/token";
        authConfig.basicClientCredentialsAuthEnabled = () -> true;
        return new OidcAuthenticationStrategy(oidcMech, authConfig, null, null,
                LoggerFactory.getLogger(OidcClientCredentialsOffloadTest.class), parent);
    }

    private static RoutingContext createMockRoutingContext() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .add("Authorization", BASIC_HEADER);
        when(request.headers()).thenReturn(headers);
        when(ctx.request()).thenReturn(request);
        return ctx;
    }
}
