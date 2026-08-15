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
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ERCacheRetriableFailureTest {

    @Test
    void retriesDirectAndWrapped429() {
        assertTrue(ERCache.isRetriableCacheLoadFailure(api(429)));
        assertTrue(ERCache.isRetriableCacheLoadFailure(
                new RuntimeException(new ExecutionException(api(429)))));
        assertTrue(ERCache.isRetriableCacheLoadFailure(
                new RuntimeException(new RuntimeException(new ExecutionException(api(429))))));
    }

    @Test
    void retriesOutageStatusCodes() {
        assertTrue(ERCache.isRetriableCacheLoadFailure(api(502)));
        assertTrue(ERCache.isRetriableCacheLoadFailure(api(503)));
        assertTrue(ERCache.isRetriableCacheLoadFailure(api(504)));
    }

    @Test
    void doesNotRetryNonRetriableApiStatuses() {
        assertFalse(ERCache.isRetriableCacheLoadFailure(api(404)));
        assertFalse(ERCache.isRetriableCacheLoadFailure(api(500)));
    }

    @Test
    void doesNotRetryApiExceptionWithoutStatusCode() {
        ApiException withoutStatus = new ApiException("no status");
        assertFalse(ERCache.isRetriableCacheLoadFailure(withoutStatus));
        assertFalse(ERCache.isRetriableCacheLoadFailure(new RuntimeException(withoutStatus)));
    }

    @Test
    void retriesSocketFailuresWithoutMessageMatching() {
        assertTrue(ERCache.isRetriableCacheLoadFailure(new ConnectException("Connection refused")));
        assertTrue(ERCache.isRetriableCacheLoadFailure(new SocketException("Connection reset")));
        assertTrue(ERCache.isRetriableCacheLoadFailure(new SocketTimeoutException("Read timed out")));
    }

    @Test
    void doesNotRetryGenericIoOrRuntimeFailures() {
        assertFalse(ERCache.isRetriableCacheLoadFailure(new IOException((String) null)));
        assertFalse(ERCache.isRetriableCacheLoadFailure(new IOException("disk full")));
        assertFalse(ERCache.isRetriableCacheLoadFailure(new RuntimeException("boom")));
    }

    @Test
    void retriesVertxClosedAndTimeoutFailures() {
        assertTrue(ERCache.isRetriableCacheLoadFailure(new HttpClosedException("Connection was closed")));
        assertTrue(ERCache.isRetriableCacheLoadFailure(
                new VertxException("The timeout period of 30000ms has been exceeded")));
        assertTrue(ERCache.isRetriableCacheLoadFailure(
                new RuntimeException(new VertxException("The timeout was triggered"))));
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
