/*
 * Copyright 2025 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.auth;

import io.fabric8.kubernetes.api.model.authentication.TokenReview;
import io.fabric8.kubernetes.api.model.authentication.TokenReviewBuilder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.kubernetes.client.KubernetesServer;
import io.quarkus.test.kubernetes.client.KubernetesTestServer;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the REAL MicroProfile Fault Tolerance {@code @CircuitBreaker} on
 * {@link TokenReviewClient#review(String)} — through the CDI proxy, against a mock Kubernetes API
 * server — rather than mocking the breaker's outcome. Proves that:
 * <ul>
 * <li>the interceptor is actually wired (a classic MP-FT footgun is an annotation that silently
 * never applies),</li>
 * <li>5 consecutive API failures open the circuit and further calls fail fast without reaching
 * the API server,</li>
 * <li>{@code authenticated=false} responses are healthy replies and never trip the breaker,</li>
 * <li>concurrent requests against an open circuit are all rejected consistently,</li>
 * <li>after the (config-shortened) open window, concurrent threads racing the probe window behave
 * consistently — the probe closes the circuit and traffic resumes,</li>
 * <li>the standard MP-FT config override ({@code TokenReviewClient/review/CircuitBreaker/delay})
 * documented as the tuning knob really applies.</li>
 * </ul>
 */
@QuarkusTest
@WithKubernetesTestServer(crud = false)
@TestProfile(TokenReviewClientCircuitBreakerTest.ShortDelayProfile.class)
class TokenReviewClientCircuitBreakerTest {

    private static final String TOKEN_REVIEW_PATH = "/apis/authentication.k8s.io/v1/tokenreviews";
    private static final long OPEN_DELAY_MS = 2_000;

    public static class ShortDelayProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // The standard MP-FT config key documented on TokenReviewClient — using it here also
            // proves the documented tuning knob applies to the annotation. Client-side retries
            // are disabled so each review() maps to exactly one API request, keeping the
            // request-count assertions exact.
            return Map.of(
                    "io.apicurio.registry.auth.TokenReviewClient/review/CircuitBreaker/delay",
                    String.valueOf(OPEN_DELAY_MS),
                    "quarkus.kubernetes-client.request-retry-backoff-limit", "0");
        }
    }

    @KubernetesTestServer
    KubernetesServer mockServer;

    @Inject
    TokenReviewClient tokenReviewClient;

    private TokenReview reviewResponse(boolean authenticated) {
        return new TokenReviewBuilder()
                .withNewStatus()
                .withAuthenticated(authenticated)
                .endStatus()
                .build();
    }

    @Test
    void circuitBreakerLifecycle() throws Exception {
        // --- Phase 1: authenticated=false responses are healthy replies and never trip the
        // breaker. Fill a whole rolling window (requestVolumeThreshold=5) with them.
        mockServer.expect().post().withPath(TOKEN_REVIEW_PATH)
                .andReturn(200, reviewResponse(false)).times(5);
        for (int i = 0; i < 5; i++) {
            assertFalse(tokenReviewClient.review("invalid-token").getAuthenticated(),
                    "authenticated=false must be returned normally");
        }

        // --- Phase 2: five consecutive API failures open the circuit.
        mockServer.expect().post().withPath(TOKEN_REVIEW_PATH)
                .andReturn(500, "the API server is on fire").times(5);
        for (int i = 0; i < 5; i++) {
            Exception e = assertThrows(Exception.class,
                    () -> tokenReviewClient.review("some-token"));
            assertFalse(e instanceof CircuitBreakerOpenException,
                    "circuit must still be closed while failures accumulate (failure " + i + ")");
        }
        // Phase 1 proved the circuit was still closed after 5 healthy-but-unauthenticated
        // replies: the five 500s above actually reached the server.
        int requestsBeforeOpen = mockServer.getKubernetesMockServer().getRequestCount();
        assertEquals(10, requestsBeforeOpen, "all ten calls so far must have reached the server");

        // The 6th call fails fast: the interceptor is wired and the circuit is open.
        assertThrows(CircuitBreakerOpenException.class,
                () -> tokenReviewClient.review("some-token"),
                "the call after five consecutive failures must fail fast with an open circuit");
        assertEquals(requestsBeforeOpen, mockServer.getKubernetesMockServer().getRequestCount(),
                "an open circuit must not let the call reach the API server");

        // --- Phase 3: concurrent requests against the open circuit are all rejected, and none
        // of them reaches the API server.
        List<Object> openOutcomes = raceConcurrently(10);
        for (Object outcome : openOutcomes) {
            assertInstanceOf(CircuitBreakerOpenException.class, outcome,
                    "every concurrent call against an open circuit must be rejected");
        }
        assertEquals(requestsBeforeOpen, mockServer.getKubernetesMockServer().getRequestCount(),
                "concurrent calls against an open circuit must not reach the API server");

        // --- Phase 4: after the open window elapses, threads racing the probe window behave
        // consistently: at least one probe goes through and succeeds, any other thread either
        // also succeeds (probe already closed the circuit) or is rejected with
        // CircuitBreakerOpenException — never an API error, never an inconsistent state.
        mockServer.expect().post().withPath(TOKEN_REVIEW_PATH)
                .andReturn(200, reviewResponse(true)).always();

        // Poll with whole concurrent bursts instead of sleeping: while the circuit is still open,
        // every burst is fully rejected without touching the server; the first burst after the
        // open window elapses races the half-open probe — exactly the scenario under test.
        AtomicReference<List<Object>> probeRace = new AtomicReference<>();
        await().atMost(Duration.ofMillis(OPEN_DELAY_MS * 5))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    List<Object> outcomes = raceConcurrently(10);
                    probeRace.set(outcomes);
                    return outcomes.stream()
                            .anyMatch(outcome -> !(outcome instanceof CircuitBreakerOpenException));
                });

        int successes = 0;
        for (Object outcome : probeRace.get()) {
            if (outcome instanceof CircuitBreakerOpenException) {
                continue; // rejected while the probe was still in flight — expected
            }
            assertInstanceOf(Boolean.class, outcome,
                    "a call admitted during recovery must complete normally, got: " + outcome);
            assertTrue((Boolean) outcome, "the healthy API now authenticates the token");
            successes++;
        }
        assertTrue(successes >= 1,
                "at least one probe must be admitted through the half-open circuit");

        // The probe's success closed the circuit: traffic flows normally again.
        assertTrue(tokenReviewClient.review("valid-token").getAuthenticated(),
                "the circuit must be closed again after a successful probe");
    }

    /**
     * Fires {@code threads} simultaneous {@code review()} calls (released together by a barrier)
     * and returns each call's outcome: the {@code authenticated} Boolean on success, or the
     * thrown exception.
     */
    private List<Object> raceConcurrently(int threads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CyclicBarrier startTogether = new CyclicBarrier(threads);
            List<Future<Object>> futures = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit((Callable<Object>) () -> {
                    startTogether.await();
                    try {
                        return tokenReviewClient.review("raced-token").getAuthenticated();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            List<Object> outcomes = new ArrayList<>(threads);
            for (Future<Object> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
