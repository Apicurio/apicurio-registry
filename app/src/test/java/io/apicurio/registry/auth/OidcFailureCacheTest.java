package io.apicurio.registry.auth;

import io.quarkus.oidc.runtime.OidcAuthenticationMechanism;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
 *     <li>A failed fetch is recorded before its future stops being the cache entry, so no
 *     request can start a second fetch in between</li>
 *     <li>Only auth rejections and server errors are cached, not other exceptions</li>
 *     <li>A joiner released by a creator that died on an Error records what it saw</li>
 *     <li>A 401 is cached like a 403</li>
 * </ul>
 */
class OidcFailureCacheTest {

    @Test
    void forbiddenResponseIsCachedAndRethrownWithoutSecondFetch() throws Exception {
        AtomicInteger fetchCount = new AtomicInteger(0);

        AppAuthenticationMechanism mockParent = mock(AppAuthenticationMechanism.class);
        when(mockParent.getAccessToken(any(Pair.class), anyString()))
                .thenAnswer(invocation -> {
                    fetchCount.incrementAndGet();
                    throw new ForbiddenException(
                            "OIDC token request returned 403");
                });

        OidcAuthenticationMechanism oidcMech = mock(OidcAuthenticationMechanism.class);

        AuthConfig authConfig = new AuthConfig();
        String tokenUrl = "http://localhost/realms/test/token";
        authConfig.authServerUrl = "http://localhost/realms/test";
        authConfig.oidcTokenPath = tokenUrl;

        OidcAuthenticationStrategy strategy = new OidcAuthenticationStrategy(
                oidcMech, authConfig, null, null,
                LoggerFactory.getLogger(OidcFailureCacheTest.class), mockParent);

        Pair<String, String> credentials = Pair.of("test-client", "test-secret");
        IdentityProviderManager idpManager = mock(IdentityProviderManager.class);

        // First call: should fetch and fail with ForbiddenException
        RoutingContext ctx1 = createMockRoutingContext();
        assertThrows(ForbiddenException.class,
                () -> strategy.authenticateWithClientCredentials(credentials, ctx1, idpManager),
                "Expected ForbiddenException");
        assertEquals(1, fetchCount.get(), "First call should trigger exactly one fetch");
        assertFalse(strategy.credentialStates.isEmpty(),
                "Failure should be cached after first call");
        assertInstanceOf(ForbiddenException.class,
                ((OidcAuthenticationStrategy.FailureState) strategy.credentialStates.values().iterator().next()).failure().getValue());

        // Second call: should throw cached ForbiddenException without fetching
        RoutingContext ctx2 = createMockRoutingContext();
        assertThrows(ForbiddenException.class,
                () -> strategy.authenticateWithClientCredentials(credentials, ctx2, idpManager),
                "Cached failure should be ForbiddenException");
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
        String tokenUrl = "http://localhost/realms/test/token";
        authConfig.authServerUrl = "http://localhost/realms/test";
        authConfig.oidcTokenPath = tokenUrl;

        OidcAuthenticationStrategy strategy = new OidcAuthenticationStrategy(
                oidcMech, authConfig, null, null,
                LoggerFactory.getLogger(OidcFailureCacheTest.class), mockParent);

        Pair<String, String> credentials = Pair.of("server-error-client", "secret");
        IdentityProviderManager idpManager = mock(IdentityProviderManager.class);

        // First call: should fetch and fail with OidcAuthException
        RoutingContext ctx1 = createMockRoutingContext();
        assertThrows(OidcAuthException.class,
                () -> strategy.authenticateWithClientCredentials(credentials, ctx1, idpManager),
                "Expected OidcAuthException");
        assertEquals(1, fetchCount.get(), "First call should trigger exactly one fetch");

        // Second call: cached OidcAuthException, no fetch
        RoutingContext ctx2 = createMockRoutingContext();
        assertThrows(OidcAuthException.class,
                () -> strategy.authenticateWithClientCredentials(credentials, ctx2, idpManager),
                "Cached failure should be OidcAuthException");
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
        String tokenUrl = "http://localhost/realms/test/token";
        authConfig.authServerUrl = "http://localhost/realms/test";
        authConfig.oidcTokenPath = tokenUrl;

        OidcAuthenticationStrategy strategy = new OidcAuthenticationStrategy(
                oidcMech, authConfig, null, null,
                LoggerFactory.getLogger(OidcFailureCacheTest.class), mockParent);

        // Pre-populate the failure cache with an already-expired entry.
        // Using a 1ms TTL and an instant in the past ensures it is expired.
        String credentialsHash = DigestUtils.sha256Hex("recovery-clientrecovery-secret");
        strategy.credentialStates.put(credentialsHash,
                new OidcAuthenticationStrategy.FailureState(new WrappedValue<>(Duration.ofMillis(1),
                        Instant.now().minusSeconds(10),
                        new ForbiddenException("stale cached failure"))));

        // Verify the cached entry is indeed expired
        assertTrue(((OidcAuthenticationStrategy.FailureState) strategy.credentialStates.get(credentialsHash)).failure().isExpired(),
                "The pre-populated failure cache entry should be expired");

        Pair<String, String> credentials = Pair.of("recovery-client", "recovery-secret");
        IdentityProviderManager idpManager = mock(IdentityProviderManager.class);
        RoutingContext ctx = createMockRoutingContext();

        // Call should succeed because the failure cache entry is expired.
        // The parent mock returns a valid token, so no exception is thrown.
        strategy.authenticateWithClientCredentials(credentials, ctx, idpManager);

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
        String tokenUrl = "http://localhost/realms/test/token";
        authConfig.authServerUrl = "http://localhost/realms/test";
        authConfig.oidcTokenPath = tokenUrl;

        OidcAuthenticationStrategy strategy = new OidcAuthenticationStrategy(
                oidcMech, authConfig, null, null,
                LoggerFactory.getLogger(OidcFailureCacheTest.class), mockParent);

        // Pre-populate the failure cache with a non-expired entry
        String credentialsHash = DigestUtils.sha256Hex("blocked-clientblocked-secret");
        strategy.credentialStates.put(credentialsHash,
                new OidcAuthenticationStrategy.FailureState(new WrappedValue<>(Duration.ofMinutes(5), Instant.now(),
                        new ForbiddenException("active cached failure"))));

        assertFalse(((OidcAuthenticationStrategy.FailureState) strategy.credentialStates.get(credentialsHash)).failure().isExpired(),
                "The pre-populated failure cache entry should NOT be expired");

        Pair<String, String> credentials = Pair.of("blocked-client", "blocked-secret");
        IdentityProviderManager idpManager = mock(IdentityProviderManager.class);
        RoutingContext ctx = createMockRoutingContext();

        assertThrows(ForbiddenException.class,
                () -> strategy.authenticateWithClientCredentials(credentials, ctx, idpManager),
                "Active failure cache should block retry with ForbiddenException");

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
        String tokenUrl = "http://localhost/realms/test/token";
        authConfig.authServerUrl = "http://localhost/realms/test";
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
                        inFlight.set(((OidcAuthenticationStrategy.TokenState) strategy.credentialStates.get(credentialsHash)).future());
                        // An Error, not a RuntimeException: the creator's catch block
                        // does not cover it, so only a finally can clean up after it.
                        throw new SimulatedJvmError();
                    }
                    return new WrappedValue<>(
                            Duration.ofMinutes(10), Instant.now(), "token-after-error");
                });

        Pair<String, String> credentials = Pair.of("error-client", "error-secret");
        IdentityProviderManager idpManager = mock(IdentityProviderManager.class);

        // First call: the Error escapes, since none of the catch clauses on the way
        // out cover it.
        RoutingContext ctx1 = createMockRoutingContext();
        assertThrows(SimulatedJvmError.class,
                () -> strategy.authenticateWithClientCredentials(credentials, ctx1, idpManager),
                "The injected Error should propagate");
        assertEquals(1, fetchCount.get(), "First call should trigger exactly one fetch");

        // The entry may remain, but it must be completed exceptionally and therefore
        // replaceable by a later caller. Its identity also guards late joiners.
        OidcAuthenticationStrategy.TokenState failedState = assertInstanceOf(
                OidcAuthenticationStrategy.TokenState.class, strategy.credentialStates.get(credentialsHash));
        assertTrue(failedState.future().isCompletedExceptionally());

        // Making the entry replaceable rescues later callers. A caller
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
                () -> strategy.authenticateWithClientCredentials(credentials, ctx2, idpManager),
                "Second call never returned; it is blocked on the stranded future");

        assertEquals(2, fetchCount.get(),
                "The second call should attempt a fresh fetch, not reuse the dead future");
        assertEquals("Bearer token-after-error",
                ctx2.request().headers().get("Authorization"),
                "Recovery after the Error should set the Authorization header");
    }

    // A cacheable failure replaces the fetching state before its waiters are released.
    @Test
    void failureIsCachedBeforeTheFailedFutureIsReleased() {
        String key = DigestUtils.sha256Hex("gap-clientgap-secret");
        AtomicReference<OidcAuthenticationStrategy.CredentialState> stateAtCompletion = new AtomicReference<>();
        AtomicInteger fetchCount = new AtomicInteger();
        AppAuthenticationMechanism parent = mock(AppAuthenticationMechanism.class);
        OidcAuthenticationStrategy strategy = newStrategy(parent);
        when(parent.getAccessToken(any(Pair.class), anyString())).thenAnswer(invocation -> {
            fetchCount.incrementAndGet();
            OidcAuthenticationStrategy.TokenState pending = (OidcAuthenticationStrategy.TokenState)
                    strategy.credentialStates.get(key);
            pending.future().whenComplete((value, failure) ->
                    stateAtCompletion.set(strategy.credentialStates.get(key)));
            throw new OidcAuthException("server error");
        });
        Pair<String, String> credentials = Pair.of("gap-client", "gap-secret");
        IdentityProviderManager manager = mock(IdentityProviderManager.class);
        OidcAuthException first = assertThrows(OidcAuthException.class,
                () -> strategy.authenticateWithClientCredentials(credentials, createMockRoutingContext(), manager));
        OidcAuthenticationStrategy.FailureState failed = assertInstanceOf(
                OidcAuthenticationStrategy.FailureState.class, stateAtCompletion.get());
        assertSame(first, failed.failure().getValue());
        assertSame(first, assertThrows(OidcAuthException.class,
                () -> strategy.authenticateWithClientCredentials(credentials, createMockRoutingContext(), manager)));
        assertEquals(1, fetchCount.get());
    }

    /**
     * A RuntimeException that is neither an auth rejection nor a server error is a bug or an
     * outage of another kind, not a verdict on the credentials. It must propagate and leave
     * the failure cache empty, so the next caller gets a fresh fetch.
     */
    @Test
    void unexpectedRuntimeExceptionIsNotCached() throws Exception {
        AtomicInteger fetchCount = new AtomicInteger(0);
        AppAuthenticationMechanism mockParent = mock(AppAuthenticationMechanism.class);
        when(mockParent.getAccessToken(any(Pair.class), anyString()))
                .thenAnswer(invocation -> {
                    fetchCount.incrementAndGet();
                    throw new IllegalStateException("not an auth failure");
                });

        AuthConfig authConfig = new AuthConfig();
        authConfig.authServerUrl = "http://localhost/realms/test";
        authConfig.oidcTokenPath = "http://localhost/realms/test/token";
        OidcAuthenticationStrategy strategy = new OidcAuthenticationStrategy(
                mock(OidcAuthenticationMechanism.class), authConfig, null, null,
                LoggerFactory.getLogger(OidcFailureCacheTest.class), mockParent);

        Pair<String, String> credentials = Pair.of("odd-client", "odd-secret");
        IdentityProviderManager idpManager = mock(IdentityProviderManager.class);
        for (int attempt = 1; attempt <= 2; attempt++) {
            assertThrows(IllegalStateException.class,
                    () -> strategy.authenticateWithClientCredentials(credentials,
                            createMockRoutingContext(), idpManager));
        }

        assertEquals(2, fetchCount.get(), "Nothing was cached, so each call should fetch");
        assertTrue(strategy.credentialStates.isEmpty(),
                "A non-cacheable failure must leave no cache entry");
    }

    /**
     * A 401 is an auth rejection like a 403, so it is cached the same way.
     */
    @Test
    void unauthorizedResponseIsCachedAndRethrownWithoutSecondFetch() {
        AtomicInteger fetchCount = new AtomicInteger(0);
        AppAuthenticationMechanism mockParent = mock(AppAuthenticationMechanism.class);
        when(mockParent.getAccessToken(any(Pair.class), anyString()))
                .thenAnswer(invocation -> {
                    fetchCount.incrementAndGet();
                    throw new UnauthorizedException("OIDC token request returned 401");
                });
        OidcAuthenticationStrategy strategy = newStrategy(mockParent);
        Pair<String, String> credentials = Pair.of("rejected-client", "rejected-secret");
        IdentityProviderManager idpManager = mock(IdentityProviderManager.class);

        UnauthorizedException first = assertThrows(UnauthorizedException.class,
                () -> strategy.authenticateWithClientCredentials(credentials,
                        createMockRoutingContext(), idpManager));
        UnauthorizedException second = assertThrows(UnauthorizedException.class,
                () -> strategy.authenticateWithClientCredentials(credentials,
                        createMockRoutingContext(), idpManager));

        assertEquals(1, fetchCount.get(), "The second call should get the cached 401");
        assertSame(first, second, "The cached failure should be rethrown as is");
    }

    /**
     * A creator that dies on an Error leaves joiners parked on its future. They are released
     * with an OidcAuthException, and the one that reaches authenticateWithClientCredentials
     * has to record it, since the creator's own catch block never ran.
     */
    @Test
    void joinerOfADeadCreatorRecordsTheFailureItSees() throws Exception {
        String credentialsHash = DigestUtils.sha256Hex("dead-clientdead-secret");
        CountDownLatch creatorFetching = new CountDownLatch(1);
        CountDownLatch releaseCreator = new CountDownLatch(1);
        AppAuthenticationMechanism mockParent = mock(AppAuthenticationMechanism.class);
        when(mockParent.getAccessToken(any(Pair.class), anyString()))
                .thenAnswer(invocation -> {
                    creatorFetching.countDown();
                    assertTrue(releaseCreator.await(10, TimeUnit.SECONDS),
                            "The joiner never parked on the creator's future");
                    throw new SimulatedJvmError();
                });
        OidcAuthenticationStrategy strategy = newStrategy(mockParent);
        Pair<String, String> credentials = Pair.of("dead-client", "dead-secret");
        IdentityProviderManager idpManager = mock(IdentityProviderManager.class);
        AtomicReference<Throwable> creatorOutcome = new AtomicReference<>();
        AtomicReference<Throwable> joinerOutcome = new AtomicReference<>();

        Thread creator = Thread.ofPlatform().daemon().start(() -> {
            try {
                strategy.authenticateWithClientCredentials(credentials, createMockRoutingContext(),
                        idpManager);
            } catch (Throwable e) {
                creatorOutcome.set(e);
            }
        });
        assertTrue(creatorFetching.await(10, TimeUnit.SECONDS), "The creator never started fetching");

        Thread joiner = Thread.ofPlatform().daemon().start(() -> {
            try {
                strategy.authenticateWithClientCredentials(credentials, createMockRoutingContext(),
                        idpManager);
            } catch (Throwable e) {
                joinerOutcome.set(e);
            }
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (joiner.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(Thread.State.WAITING, joiner.getState(), "The joiner should be parked on the future");

        releaseCreator.countDown();
        creator.join(TimeUnit.SECONDS.toMillis(10));
        joiner.join(TimeUnit.SECONDS.toMillis(10));

        assertInstanceOf(SimulatedJvmError.class, creatorOutcome.get(),
                "The Error should escape the creator");
        OidcAuthException seen = assertInstanceOf(OidcAuthException.class, joinerOutcome.get(),
                "The joiner should be released with an OidcAuthException");
        assertSame(seen, ((OidcAuthenticationStrategy.FailureState) strategy.credentialStates.get(credentialsHash)).failure().getValue(),
                "The joiner should have recorded the exception it saw");
        assertInstanceOf(OidcAuthenticationStrategy.FailureState.class,
                strategy.credentialStates.get(credentialsHash));
    }

    private OidcAuthenticationStrategy newStrategy(AppAuthenticationMechanism parent) {
        AuthConfig authConfig = new AuthConfig();
        authConfig.authServerUrl = "http://localhost/realms/test";
        authConfig.oidcTokenPath = "http://localhost/realms/test/token";
        return new OidcAuthenticationStrategy(mock(OidcAuthenticationMechanism.class), authConfig, null,
                null, LoggerFactory.getLogger(OidcFailureCacheTest.class), parent);
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

    @Test
    void concurrentCallersShareOneFailedFetch() throws Exception {
        CountDownLatch fetchStarted = new CountDownLatch(1);
        CountDownLatch releaseFetch = new CountDownLatch(1);
        AtomicInteger fetchCount = new AtomicInteger();

        OidcAuthException failure = new OidcAuthException("Token server unavailable");
        AppAuthenticationMechanism parent = mock(AppAuthenticationMechanism.class);

        when(parent.getAccessToken(any(Pair.class), anyString()))
                .thenAnswer(invocation -> {
                    fetchCount.incrementAndGet();
                    fetchStarted.countDown();
                    assertTrue(releaseFetch.await(10, TimeUnit.SECONDS),
                            "The test did not release the fetch");
                    throw failure;
                });

        OidcAuthenticationStrategy strategy = newStrategy(parent);
        Pair<String, String> credentials = Pair.of("concurrent-client", "secret");
        IdentityProviderManager manager = mock(IdentityProviderManager.class);

        AtomicReference<Throwable> creatorOutcome = new AtomicReference<>();
        AtomicReference<Throwable> joinerOutcome = new AtomicReference<>();

        Thread creator = new Thread(() -> {
            try {
                strategy.authenticateWithClientCredentials(
                        credentials, createMockRoutingContext(), manager);
            } catch (Throwable ex) {
                creatorOutcome.set(ex);
            }
        });

        Thread joiner = new Thread(() -> {
            try {
                strategy.authenticateWithClientCredentials(
                        credentials, createMockRoutingContext(), manager);
            } catch (Throwable ex) {
                joinerOutcome.set(ex);
            }
        });

        creator.setDaemon(true);
        joiner.setDaemon(true);

        try {
            creator.start();
            assertTrue(fetchStarted.await(10, TimeUnit.SECONDS),
                    "The creator did not start fetching");

            joiner.start();

            // In this test, the joiner's indefinite wait is on the shared future.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (joiner.getState() != Thread.State.WAITING
                    && joiner.isAlive()
                    && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }

            assertEquals(Thread.State.WAITING, joiner.getState(),
                    "The joiner did not wait on the shared future");
            assertEquals(1, fetchCount.get());

            releaseFetch.countDown();
            creator.join(TimeUnit.SECONDS.toMillis(10));
            joiner.join(TimeUnit.SECONDS.toMillis(10));

            assertFalse(creator.isAlive(), "The creator did not finish");
            assertFalse(joiner.isAlive(), "The joiner was not released");
            assertSame(failure, creatorOutcome.get());
            assertSame(failure, joinerOutcome.get());

            // A later request must also reuse the recorded failure.
            assertSame(failure, assertThrows(OidcAuthException.class,
                    () -> strategy.authenticateWithClientCredentials(
                            credentials, createMockRoutingContext(), manager)));

            assertEquals(1, fetchCount.get(),
                    "Concurrent and later callers must share one failed fetch");
        } finally {
            releaseFetch.countDown();
            creator.interrupt();
            joiner.interrupt();
            creator.join(TimeUnit.SECONDS.toMillis(2));
            joiner.join(TimeUnit.SECONDS.toMillis(2));
        }
    }
}
