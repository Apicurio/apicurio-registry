package io.apicurio.registry.auth;

import io.quarkus.oidc.runtime.OidcAuthenticationMechanism;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import no.nav.security.mock.oauth2.MockOAuth2Server;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests negative-path behavior of the OIDC credential caches:
 * <ul>
 *     <li>A 403 response is cached and rethrown without a second fetch</li>
 *     <li>A server-side OidcAuthException is cached the same way</li>
 *     <li>After FAILURE_CACHE_TTL expires, the cache lets a retry through (recovery)</li>
 *     <li>Before it expires, the cache blocks the retry entirely</li>
 *     <li>A fetch that dies on an Error strands neither a later caller nor a caller
 *     already joined on the in-flight future</li>
 * </ul>
 */
class OidcFailureCacheTest {

    private MockOAuth2Server mockServer;

    @BeforeEach
    void startMockServer() {
        mockServer = new MockOAuth2Server();
        mockServer.start();
    }

    @AfterEach
    void stopMockServer() {
        if (mockServer != null) {
            mockServer.shutdown();
        }
    }

    @Test
    void forbiddenResponseIsCachedAndRethrownWithoutSecondFetch() throws Exception {
        AtomicInteger fetchCount = new AtomicInteger(0);

        AppAuthenticationMechanism mockParent = mock(AppAuthenticationMechanism.class);
        when(mockParent.getAccessToken(any(Pair.class), anyString()))
                .thenAnswer(invocation -> {
                    fetchCount.incrementAndGet();
                    throw new io.quarkus.security.ForbiddenException(
                            "OIDC token request returned 403");
                });

        OidcAuthenticationMechanism oidcMech = mock(OidcAuthenticationMechanism.class);

        AuthConfig authConfig = new AuthConfig();
        String tokenUrl = mockServer.tokenEndpointUrl("default").toString();
        authConfig.authServerUrl = mockServer.issuerUrl("default").toString();
        authConfig.oidcTokenPath = tokenUrl;

        OidcAuthenticationStrategy strategy = new OidcAuthenticationStrategy(
                oidcMech, authConfig, null, null,
                LoggerFactory.getLogger(OidcFailureCacheTest.class), mockParent);

        Method authMethod = OidcAuthenticationStrategy.class.getDeclaredMethod(
                "authenticateWithClientCredentials",
                Pair.class, RoutingContext.class, IdentityProviderManager.class);
        authMethod.setAccessible(true);

        Pair<String, String> credentials = Pair.of("test-client", "test-secret");
        IdentityProviderManager idpManager = mock(IdentityProviderManager.class);

        // First call: should fetch and fail with ForbiddenException
        RoutingContext ctx1 = createMockRoutingContext();
        try {
            authMethod.invoke(strategy, credentials, ctx1, idpManager);
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof io.quarkus.security.ForbiddenException,
                    "Expected ForbiddenException, got " + e.getCause().getClass().getName());
        }
        assertEquals(1, fetchCount.get(), "First call should trigger exactly one fetch");
        assertFalse(strategy.cachedAuthFailures.isEmpty(),
                "Failure should be cached after first call");

        // Second call: should throw cached ForbiddenException without fetching
        RoutingContext ctx2 = createMockRoutingContext();
        try {
            authMethod.invoke(strategy, credentials, ctx2, idpManager);
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof io.quarkus.security.ForbiddenException,
                    "Cached failure should be ForbiddenException, got "
                            + e.getCause().getClass().getName());
        }
        assertEquals(1, fetchCount.get(),
                "Second call should NOT trigger a fetch; the cached failure should be returned");
    }

    @Test
    void oidcAuthExceptionIsCachedAndRethrownWithoutSecondFetch() throws Exception {
        AtomicInteger fetchCount = new AtomicInteger(0);

        AppAuthenticationMechanism mockParent = mock(AppAuthenticationMechanism.class);
        when(mockParent.getAccessToken(any(Pair.class), anyString()))
                .thenAnswer(invocation -> {
                    fetchCount.incrementAndGet();
                    throw new OidcAuthException("OIDC token request failed with status 500");
                });

        OidcAuthenticationMechanism oidcMech = mock(OidcAuthenticationMechanism.class);

        AuthConfig authConfig = new AuthConfig();
        String tokenUrl = mockServer.tokenEndpointUrl("default").toString();
        authConfig.authServerUrl = mockServer.issuerUrl("default").toString();
        authConfig.oidcTokenPath = tokenUrl;

        OidcAuthenticationStrategy strategy = new OidcAuthenticationStrategy(
                oidcMech, authConfig, null, null,
                LoggerFactory.getLogger(OidcFailureCacheTest.class), mockParent);

        Method authMethod = OidcAuthenticationStrategy.class.getDeclaredMethod(
                "authenticateWithClientCredentials",
                Pair.class, RoutingContext.class, IdentityProviderManager.class);
        authMethod.setAccessible(true);

        Pair<String, String> credentials = Pair.of("server-error-client", "secret");
        IdentityProviderManager idpManager = mock(IdentityProviderManager.class);

        // First call: should fetch and fail with OidcAuthException
        RoutingContext ctx1 = createMockRoutingContext();
        try {
            authMethod.invoke(strategy, credentials, ctx1, idpManager);
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof OidcAuthException,
                    "Expected OidcAuthException, got " + e.getCause().getClass().getName());
        }
        assertEquals(1, fetchCount.get(), "First call should trigger exactly one fetch");

        // Second call: cached OidcAuthException, no fetch
        RoutingContext ctx2 = createMockRoutingContext();
        try {
            authMethod.invoke(strategy, credentials, ctx2, idpManager);
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof OidcAuthException,
                    "Cached failure should be OidcAuthException, got "
                            + e.getCause().getClass().getName());
        }
        assertEquals(1, fetchCount.get(),
                "Second call should NOT trigger a fetch; the cached failure should be returned");
    }

    @Test
    void expiredFailureCacheAllowsRetryAndRecovery() throws Exception {
        AtomicInteger fetchCount = new AtomicInteger(0);

        AppAuthenticationMechanism mockParent = mock(AppAuthenticationMechanism.class);
        when(mockParent.getAccessToken(any(Pair.class), anyString()))
                .thenAnswer(invocation -> {
                    fetchCount.incrementAndGet();
                    return new WrappedValue<>(
                            Duration.ofMinutes(10), Instant.now(), "recovered-token");
                });

        OidcAuthenticationMechanism oidcMech = mock(OidcAuthenticationMechanism.class);
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(oidcMech.authenticate(any(RoutingContext.class), any(IdentityProviderManager.class)))
                .thenReturn(Uni.createFrom().item(identity));

        AuthConfig authConfig = new AuthConfig();
        String tokenUrl = mockServer.tokenEndpointUrl("default").toString();
        authConfig.authServerUrl = mockServer.issuerUrl("default").toString();
        authConfig.oidcTokenPath = tokenUrl;

        OidcAuthenticationStrategy strategy = new OidcAuthenticationStrategy(
                oidcMech, authConfig, null, null,
                LoggerFactory.getLogger(OidcFailureCacheTest.class), mockParent);

        // Pre-populate the failure cache with an already-expired entry.
        // Using a 1ms TTL and an instant in the past ensures it is expired.
        String credentialsHash = DigestUtils.sha256Hex("recovery-clientrecovery-secret");
        strategy.cachedAuthFailures.put(credentialsHash,
                new WrappedValue<>(Duration.ofMillis(1),
                        Instant.now().minusSeconds(10),
                        new io.quarkus.security.ForbiddenException("stale cached failure")));

        // Verify the cached entry is indeed expired
        assertTrue(strategy.cachedAuthFailures.get(credentialsHash).isExpired(),
                "The pre-populated failure cache entry should be expired");

        Method authMethod = OidcAuthenticationStrategy.class.getDeclaredMethod(
                "authenticateWithClientCredentials",
                Pair.class, RoutingContext.class, IdentityProviderManager.class);
        authMethod.setAccessible(true);

        Pair<String, String> credentials = Pair.of("recovery-client", "recovery-secret");
        IdentityProviderManager idpManager = mock(IdentityProviderManager.class);
        RoutingContext ctx = createMockRoutingContext();

        // Call should succeed because the failure cache entry is expired.
        // The parent mock returns a valid token, so no exception is thrown.
        authMethod.invoke(strategy, credentials, ctx, idpManager);

        assertEquals(1, fetchCount.get(),
                "After the failure cache expires, a fresh fetch should be attempted");

        // Verify the token was set on the request
        assertEquals("Bearer recovered-token",
                ctx.request().headers().get("Authorization"),
                "A successful recovery should set the Authorization header");
    }

    @Test
    void nonExpiredFailureCacheBlocksRetry() throws Exception {
        AtomicInteger fetchCount = new AtomicInteger(0);

        AppAuthenticationMechanism mockParent = mock(AppAuthenticationMechanism.class);
        when(mockParent.getAccessToken(any(Pair.class), anyString()))
                .thenAnswer(invocation -> {
                    fetchCount.incrementAndGet();
                    return new WrappedValue<>(
                            Duration.ofMinutes(10), Instant.now(), "should-not-reach");
                });

        OidcAuthenticationMechanism oidcMech = mock(OidcAuthenticationMechanism.class);

        AuthConfig authConfig = new AuthConfig();
        String tokenUrl = mockServer.tokenEndpointUrl("default").toString();
        authConfig.authServerUrl = mockServer.issuerUrl("default").toString();
        authConfig.oidcTokenPath = tokenUrl;

        OidcAuthenticationStrategy strategy = new OidcAuthenticationStrategy(
                oidcMech, authConfig, null, null,
                LoggerFactory.getLogger(OidcFailureCacheTest.class), mockParent);

        // Pre-populate the failure cache with a non-expired entry
        String credentialsHash = DigestUtils.sha256Hex("blocked-clientblocked-secret");
        strategy.cachedAuthFailures.put(credentialsHash,
                new WrappedValue<>(Duration.ofMinutes(5), Instant.now(),
                        new io.quarkus.security.ForbiddenException("active cached failure")));

        assertFalse(strategy.cachedAuthFailures.get(credentialsHash).isExpired(),
                "The pre-populated failure cache entry should NOT be expired");

        Method authMethod = OidcAuthenticationStrategy.class.getDeclaredMethod(
                "authenticateWithClientCredentials",
                Pair.class, RoutingContext.class, IdentityProviderManager.class);
        authMethod.setAccessible(true);

        Pair<String, String> credentials = Pair.of("blocked-client", "blocked-secret");
        IdentityProviderManager idpManager = mock(IdentityProviderManager.class);
        RoutingContext ctx = createMockRoutingContext();

        try {
            authMethod.invoke(strategy, credentials, ctx, idpManager);
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof io.quarkus.security.ForbiddenException,
                    "Active failure cache should block retry with ForbiddenException, got "
                            + e.getCause().getClass().getName());
        }

        assertEquals(0, fetchCount.get(),
                "An active (non-expired) failure cache entry should block fetching entirely");
    }

    @Test
    void errorDuringFetchDoesNotStrandLaterCallers() throws Exception {
        AtomicInteger fetchCount = new AtomicInteger(0);
        AtomicReference<CompletableFuture<WrappedValue<String>>> inFlight =
                new AtomicReference<>();
        String credentialsHash = DigestUtils.sha256Hex("error-clienterror-secret");

        AppAuthenticationMechanism mockParent = mock(AppAuthenticationMechanism.class);

        OidcAuthenticationMechanism oidcMech = mock(OidcAuthenticationMechanism.class);
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(oidcMech.authenticate(any(RoutingContext.class), any(IdentityProviderManager.class)))
                .thenReturn(Uni.createFrom().item(identity));

        AuthConfig authConfig = new AuthConfig();
        String tokenUrl = mockServer.tokenEndpointUrl("default").toString();
        authConfig.authServerUrl = mockServer.issuerUrl("default").toString();
        authConfig.oidcTokenPath = tokenUrl;

        OidcAuthenticationStrategy strategy = new OidcAuthenticationStrategy(
                oidcMech, authConfig, null, null,
                LoggerFactory.getLogger(OidcFailureCacheTest.class), mockParent);

        // Stubbed after construction so the answer can reach into the strategy. While
        // the answer runs, the future is registered in the map and not yet completed,
        // which is exactly the state a concurrent caller would join on.
        when(mockParent.getAccessToken(any(Pair.class), anyString()))
                .thenAnswer(invocation -> {
                    if (fetchCount.incrementAndGet() == 1) {
                        inFlight.set(strategy.cachedAccessTokens.get(credentialsHash));
                        // An Error, not a RuntimeException: the creator's catch block
                        // does not cover it, so only a finally can clean up after it.
                        throw new SimulatedJvmError();
                    }
                    return new WrappedValue<>(
                            Duration.ofMinutes(10), Instant.now(), "token-after-error");
                });

        Method authMethod = OidcAuthenticationStrategy.class.getDeclaredMethod(
                "authenticateWithClientCredentials",
                Pair.class, RoutingContext.class, IdentityProviderManager.class);
        authMethod.setAccessible(true);

        Pair<String, String> credentials = Pair.of("error-client", "error-secret");
        IdentityProviderManager idpManager = mock(IdentityProviderManager.class);

        // First call: the Error escapes, since none of the catch clauses on the way
        // out cover it.
        RoutingContext ctx1 = createMockRoutingContext();
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> authMethod.invoke(strategy, credentials, ctx1, idpManager));
        assertInstanceOf(SimulatedJvmError.class, thrown.getCause(),
                "The injected Error should propagate");
        assertEquals(1, fetchCount.get(), "First call should trigger exactly one fetch");

        // The orphan must be gone from the map. This test is single-threaded, so the
        // 2-arg remove cannot lose a race: a non-null value here means the cleanup
        // did not run at all.
        assertNull(strategy.cachedAccessTokens.get(credentialsHash),
                "An uncompleted future was left in the token cache; every later caller "
                        + "with these credentials would block on join() forever");

        // Removing it from the map only rescues callers who arrive afterwards. A caller
        // already parked in join() is released by completeExceptionally and by nothing
        // else, so assert the future itself was settled. Bounded get() rather than
        // join(): if this half regresses the test fails instead of hanging.
        CompletableFuture<WrappedValue<String>> orphan = inFlight.get();
        assertNotNull(orphan, "The in-flight future should have been registered");
        ExecutionException released = assertThrows(ExecutionException.class,
                () -> orphan.get(10, TimeUnit.SECONDS),
                "A caller already joined on the dead future was never released");
        assertInstanceOf(OidcAuthException.class, released.getCause(),
                "The cleanup path should surface OidcAuthException to a joining caller");

        // Second call must make progress instead of joining the orphan. Preemptive so
        // that the bug surfaces as a failure rather than hanging the build.
        RoutingContext ctx2 = createMockRoutingContext();
        assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> authMethod.invoke(strategy, credentials, ctx2, idpManager),
                "Second call never returned; it is blocked on the stranded future");

        assertEquals(2, fetchCount.get(),
                "The second call should attempt a fresh fetch, not reuse the dead future");
        assertEquals("Bearer token-after-error",
                ctx2.request().headers().get("Authorization"),
                "Recovery after the Error should set the Authorization header");
    }

    /**
     * Stands in for an Error raised by the token fetch, such as a linkage error or
     * OutOfMemoryError. Deliberately not a RuntimeException.
     */
    private static final class SimulatedJvmError extends Error {
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
