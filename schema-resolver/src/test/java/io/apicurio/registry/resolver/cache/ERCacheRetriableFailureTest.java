/*
 * Copyright 2026 Red Hat
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

package io.apicurio.registry.resolver.cache;

import com.microsoft.kiota.ApiException;
import io.vertx.core.VertxException;
import io.vertx.core.http.HttpClosedException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ERCacheRetriableFailureTest {

    @Test
    void retriesDirectAndWrapped429EvenWithoutTransientOptIn() {
        assertTrue(ERCache.isRetriableCacheLoadFailure(api(429)));
        assertTrue(ERCache.isRetriableCacheLoadFailure(
                new RuntimeException(new ExecutionException(api(429)))));
        assertTrue(ERCache.isRetriableCacheLoadFailure(
                new RuntimeException(new RuntimeException(new ExecutionException(api(429))))));
        assertTrue(ERCache.isRetriableCacheLoadFailure(api(429), false));
    }

    @Test
    void outageStatusesRequireTransientOptIn() {
        assertFalse(ERCache.isRetriableCacheLoadFailure(api(502), false));
        assertFalse(ERCache.isRetriableCacheLoadFailure(api(503), false));
        assertFalse(ERCache.isRetriableCacheLoadFailure(api(504), false));

        assertTrue(ERCache.isRetriableCacheLoadFailure(api(502), true));
        assertTrue(ERCache.isRetriableCacheLoadFailure(api(503), true));
        assertTrue(ERCache.isRetriableCacheLoadFailure(api(504), true));
    }

    @Test
    void doesNotRetryNonRetriableApiStatuses() {
        assertFalse(ERCache.isRetriableCacheLoadFailure(api(404), true));
        assertFalse(ERCache.isRetriableCacheLoadFailure(api(500), true));
    }

    @Test
    void apiStatusOutvotesNetworkCauseDeeperInChain() {
        ApiException notFound = api(404);
        notFound.initCause(new ConnectException("Connection refused"));
        assertFalse(ERCache.isRetriableCacheLoadFailure(new RuntimeException(notFound), true));
    }

    @Test
    void doesNotRetryApiExceptionWithoutStatusCode() {
        ApiException withoutStatus = new ApiException("no status");
        assertFalse(ERCache.isRetriableCacheLoadFailure(withoutStatus, true));
        assertFalse(ERCache.isRetriableCacheLoadFailure(new RuntimeException(withoutStatus), true));
    }

    @Test
    void networkFailuresRequireTransientOptIn() {
        ConnectException refused = new ConnectException("Connection refused");
        assertFalse(ERCache.isRetriableCacheLoadFailure(refused, false));
        assertTrue(ERCache.isRetriableCacheLoadFailure(refused, true));
        assertTrue(ERCache.isRetriableCacheLoadFailure(new SocketException("Connection reset"), true));
        assertTrue(ERCache.isRetriableCacheLoadFailure(new SocketTimeoutException("Read timed out"), true));
    }

    @Test
    void doesNotRetryResourceExhaustionButRetriesUnreachableHost() {
        assertFalse(ERCache.isRetriableCacheLoadFailure(
                new SocketException("Too many open files"), true));
        assertFalse(ERCache.isRetriableCacheLoadFailure(
                new SocketException("No buffer space available"), true));
        assertTrue(ERCache.isRetriableCacheLoadFailure(
                new NoRouteToHostException("Network is unreachable"), true));
    }

    @Test
    void doesNotRetryGenericIoOrRuntimeFailures() {
        assertFalse(ERCache.isRetriableCacheLoadFailure(new IOException((String) null), true));
        assertFalse(ERCache.isRetriableCacheLoadFailure(new IOException("disk full"), true));
        assertFalse(ERCache.isRetriableCacheLoadFailure(new RuntimeException("boom"), true));
    }

    @Test
    void retriesVertxClosedAndTimeoutFailuresWhenTransientEnabled() {
        assertFalse(ERCache.isRetriableCacheLoadFailure(
                new HttpClosedException("Connection was closed"), false));
        assertTrue(ERCache.isRetriableCacheLoadFailure(
                new HttpClosedException("Connection was closed"), true));
        assertTrue(ERCache.isRetriableCacheLoadFailure(
                new VertxException("The timeout period of 30000ms has been exceeded"), true));
        assertTrue(ERCache.isRetriableCacheLoadFailure(
                new RuntimeException(new VertxException("The timeout was triggered")), true));
    }

    @Test
    void causeWalkHasDepthCapAgainstCycles() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b");
        a.initCause(b);
        b.initCause(a);
        assertFalse(ERCache.isRetriableCacheLoadFailure(a, true));
    }

    @Test
    void retryLoopInvokesRetriesPlusOneForRetriableFailure() {
        AtomicInteger attempts = new AtomicInteger();
        ERCache.Result<String, RuntimeException> result = ERCache.retry(
                Duration.ofMillis(1), 2, true,
                () -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException(new ConnectException("Connection refused"));
                });

        assertTrue(result.isError());
        assertEquals(3, attempts.get());
    }

    @Test
    void retryLoopStopsImmediatelyForNonRetriableFailure() {
        AtomicInteger attempts = new AtomicInteger();
        ERCache.Result<String, RuntimeException> result = ERCache.retry(
                Duration.ofMillis(1), 3, true,
                () -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException(api(404));
                });

        assertTrue(result.isError());
        assertEquals(1, attempts.get());
    }

    @Test
    void retryLoopDoesNotRetryNetworkFailuresWithoutTransientOptIn() {
        AtomicInteger attempts = new AtomicInteger();
        ERCache.Result<String, RuntimeException> result = ERCache.retry(
                Duration.ofMillis(1), 3, false,
                () -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException(new ConnectException("Connection refused"));
                });

        assertTrue(result.isError());
        assertEquals(1, attempts.get());
    }

    @Test
    void retryLoopHonorsTotalTimeoutBudget() {
        AtomicInteger attempts = new AtomicInteger();
        ERCache.Result<String, RuntimeException> result = ERCache.retry(
                Duration.ofMillis(50), 10, true, Duration.ofMillis(80),
                () -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException(new ConnectException("Connection refused"));
                });

        assertTrue(result.isError());
        assertTrue(attempts.get() >= 1);
        assertTrue(attempts.get() < 11, "total timeout should stop before exhausting all retries");
        assertTrue(result.error.getMessage().contains("total retry timeout"));
        assertNotNull(result.error.getCause(),
                "timeout error must keep the last attempt's failure as cause");
        assertTrue(result.error.getCause().getCause() instanceof ConnectException
                || result.error.getCause() instanceof ConnectException);
    }

    private static ApiException api(int status) {
        return new TestApiException(status);
    }

    private static final class TestApiException extends ApiException {
        TestApiException(int status) {
            super("HTTP " + status);
            setResponseStatusCode(status);
        }
    }
}
