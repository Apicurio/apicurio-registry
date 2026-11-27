package io.apicurio.registry.auth;

import io.quarkus.oidc.runtime.OidcAuthenticationMechanism;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies concurrent client-credentials requests with same credentials result in exactly one token fetch. */
class OidcTokenStampedeTest {

    @Test
    void concurrentClientCredentialsFetchResultsInSingleTokenRequest() throws Exception {
        int threadCount = 20;
        AtomicInteger tokenFetchCount = new AtomicInteger(0);
        CountDownLatch secondFetch = new CountDownLatch(2);

        // Mock the parent so getAccessToken counts invocations and holds the first fetch open
        // for up to 200 ms. Without stampede prevention the other threads all see a cache miss
        // in that window and fetch too, which releases the latch early.
        AppAuthenticationMechanism mockParent = mock(AppAuthenticationMechanism.class);
        when(mockParent.getAccessToken(any(Pair.class), anyString()))
                .thenAnswer(invocation -> {
                    tokenFetchCount.incrementAndGet();
                    secondFetch.countDown();
                    secondFetch.await(200, TimeUnit.MILLISECONDS);
                    return new WrappedValue<>(
                            Duration.ofMinutes(10), Instant.now(), "mock-access-token");
                });

        // No request ever reaches the token endpoint: getAccessToken is mocked above, so a
        // fixed dummy URL is all the strategy needs.
        AuthConfig authConfig = new AuthConfig();
        authConfig.authServerUrl = "http://localhost/realms/test";
        authConfig.oidcTokenPath = "http://localhost/realms/test/token";
        authConfig.accessTokenExpiration = 10;
        authConfig.accessTokenExpirationOffset = 10;

        // The final OIDC authenticate call after the Bearer header is set.
        // Not under test here, so it returns a stub identity.
        OidcAuthenticationMechanism oidcMech = mock(OidcAuthenticationMechanism.class);
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(oidcMech.authenticate(any(RoutingContext.class), any(IdentityProviderManager.class)))
                .thenReturn(Uni.createFrom().item(identity));

        OidcAuthenticationStrategy strategy = new OidcAuthenticationStrategy(
                oidcMech, authConfig, null, null,
                LoggerFactory.getLogger(OidcTokenStampedeTest.class), mockParent);

        Pair<String, String> credentials = Pair.of("test-client", "test-secret");
        IdentityProviderManager idpManager = mock(IdentityProviderManager.class);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            List<RoutingContext> contexts = new ArrayList<>();
            List<Future<Object>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                RoutingContext ctx = createMockRoutingContext();
                contexts.add(ctx);
                futures.add(executor.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return strategy.authenticateWithClientCredentials(credentials, ctx, idpManager);
                }));
            }

            // One budget for every caller, so a hung regression fails after 10 s, not 20 x 10 s.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            for (Future<Object> future : futures) {
                try {
                    future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                } catch (ExecutionException e) {
                    throw new AssertionError("Concurrent token fetch failed", e.getCause());
                }
            }

            // Every caller, not only the one that fetched, must have got the token.
            for (RoutingContext ctx : contexts) {
                assertEquals("Bearer mock-access-token",
                        ctx.request().headers().get("Authorization"));
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, tokenFetchCount.get(),
                "Expected exactly 1 token fetch for " + threadCount
                        + " concurrent requests with same credentials, but got "
                        + tokenFetchCount.get()
                        + ". This indicates a cache stampede.");
    }

    @Test
    void timeoutExceptionFromCreatorReleasesJoinersAndIsCached() throws Exception {
        AppAuthenticationMechanism mockParent = mock(AppAuthenticationMechanism.class);
        when(mockParent.getAccessToken(any(Pair.class), anyString()))
                .thenThrow(new org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException(
                        "Timed out"));

        AuthConfig authConfig = new AuthConfig();
        authConfig.authServerUrl = "http://localhost/realms/test";
        authConfig.oidcTokenPath = "http://localhost/realms/test/token";
        authConfig.accessTokenExpiration = 10;
        authConfig.accessTokenExpirationOffset = 10;
        authConfig.failureCacheTtlSeconds = 30;

        OidcAuthenticationMechanism oidcMech = mock(OidcAuthenticationMechanism.class);
        OidcAuthenticationStrategy strategy = new OidcAuthenticationStrategy(
                oidcMech, authConfig, null, null,
                LoggerFactory.getLogger(OidcTokenStampedeTest.class), mockParent);

        Pair<String, String> credentials = Pair.of("timeout-client", "secret");
        IdentityProviderManager idpManager = mock(IdentityProviderManager.class);

        assertThrows(org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException.class,
                () -> strategy.authenticateWithClientCredentials(
                        credentials, createMockRoutingContext(), idpManager));

        // Second call within failureCacheTtlSeconds should hit the failure cache, not getAccessToken.
        assertThrows(org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException.class,
                () -> strategy.authenticateWithClientCredentials(
                        credentials, createMockRoutingContext(), idpManager));

        verify(mockParent, times(1)).getAccessToken(any(Pair.class), anyString());
    }

    @Test
    void joinerDoesNotHangForeverWhenCreatorStalls() throws Exception {
        CountDownLatch creatorStarted = new CountDownLatch(1);
        CountDownLatch releaseCreator = new CountDownLatch(1);

        AppAuthenticationMechanism mockParent = mock(AppAuthenticationMechanism.class);
        when(mockParent.getAccessToken(any(Pair.class), anyString()))
                .thenAnswer(invocation -> {
                    creatorStarted.countDown();
                    releaseCreator.await(10, TimeUnit.SECONDS);
                    return new WrappedValue<>(
                            Duration.ofMinutes(10), Instant.now(), "late-token");
                });

        AuthConfig authConfig = new AuthConfig();
        authConfig.authServerUrl = "http://localhost/realms/test";
        authConfig.oidcTokenPath = "http://localhost/realms/test/token";
        authConfig.accessTokenExpiration = 10;
        authConfig.accessTokenExpirationOffset = 10;
        authConfig.tokenFetchTimeoutSeconds = 1;

        OidcAuthenticationMechanism oidcMech = mock(OidcAuthenticationMechanism.class);
        OidcAuthenticationStrategy strategy = new OidcAuthenticationStrategy(
                oidcMech, authConfig, null, null,
                LoggerFactory.getLogger(OidcTokenStampedeTest.class), mockParent);

        Pair<String, String> credentials = Pair.of("hung-client", "secret");
        IdentityProviderManager idpManager = mock(IdentityProviderManager.class);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> creatorFuture = executor.submit(() -> strategy.authenticateWithClientCredentials(
                    credentials, createMockRoutingContext(), idpManager));
            assertTrue(creatorStarted.await(5, TimeUnit.SECONDS));

            long startNanos = System.nanoTime();
            Future<?> joinerFuture = executor.submit(() -> strategy.authenticateWithClientCredentials(
                    credentials, createMockRoutingContext(), idpManager));

            ExecutionException ee = assertThrows(ExecutionException.class,
                    () -> joinerFuture.get(5, TimeUnit.SECONDS));
            assertTrue(ee.getCause() instanceof OidcAuthException,
                    "Expected OidcAuthException on joiner timeout, got: " + ee.getCause());
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            assertTrue(elapsedMs >= 900 && elapsedMs < 4000,
                    "Expected joiner to time out around 1s, took " + elapsedMs + "ms");

            releaseCreator.countDown();
            creatorFuture.get(5, TimeUnit.SECONDS);
        } finally {
            releaseCreator.countDown();
            executor.shutdownNow();
        }
    }

    private static RoutingContext createMockRoutingContext() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        when(request.headers()).thenReturn(headers);
        when(ctx.request()).thenReturn(request);
        return ctx;
    }
}
