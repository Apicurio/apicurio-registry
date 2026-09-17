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
package io.apicurio.registry.noprofile.http;

import io.apicurio.registry.http.HttpClientContentException;
import io.apicurio.registry.http.HttpClientErrorException;
import io.apicurio.registry.http.HttpClientException;
import io.apicurio.registry.http.HttpClientInterruptedException;
import io.apicurio.registry.http.HttpClientService;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.client.WebClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link HttpClientService} covering HTTP 2xx handling, non-retriable content
 * failure discrimination (empty body / malformed JSON), 4xx client error aborts, fault
 * tolerance retries, and interrupt handling through FT interceptors.
 */
@QuarkusTest
class HttpClientServiceTest {

    private static String baseUrl;

    private static Vertx vertx;
    private static HttpServer stubServer;
    private static final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private static volatile CountDownLatch delayedInterruptLatch;

    /** CDI-managed bean — carries {@code @Retry} + {@code @Timeout} interceptors. */
    @Inject
    HttpClientService httpClientService;

    /**
     * Plain, non-intercepted instance — bypasses {@code @Retry}/{@code @Timeout} so that
     * status-branching (500) and interrupt tests complete in a single attempt without backoff.
     */
    private static HttpClientService rawService;

    @BeforeAll
    static void startStubServer() throws Exception {
        vertx = Vertx.vertx();
        CompletableFuture<Void> ready = new CompletableFuture<>();

        vertx.createHttpServer().requestHandler(req -> {
            String path = req.path();
            int attempt = requestCounts.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();

            switch (path) {
                case "/ok200":
                    req.response().setStatusCode(200)
                            .putHeader("content-type", "application/json")
                            .end("{\"result\":\"ok\"}");
                    break;
                case "/created201":
                    req.response().setStatusCode(201)
                            .putHeader("content-type", "application/json")
                            .end("{\"result\":\"created\"}");
                    break;
                case "/accepted202":
                    req.response().setStatusCode(202)
                            .putHeader("content-type", "application/json")
                            .end("{\"result\":\"accepted\"}");
                    break;
                case "/noContent204":
                    req.response().setStatusCode(204).end();
                    break;
                case "/malformedJson":
                    req.response().setStatusCode(200)
                            .putHeader("content-type", "application/json")
                            .end("not-valid-json");
                    break;
                case "/clientError400":
                    req.response().setStatusCode(400)
                            .putHeader("content-type", "application/json")
                            .end("{\"error\":\"bad_request\"}");
                    break;
                case "/clientError404":
                    req.response().setStatusCode(404)
                            .putHeader("content-type", "application/json")
                            .end("{\"error\":\"not_found\"}");
                    break;
                case "/retryThenSuccess":
                    if (attempt <= 2) {
                        req.response().setStatusCode(500)
                                .putHeader("content-type", "text/plain")
                                .end("Transient Error");
                    } else {
                        req.response().setStatusCode(200)
                                .putHeader("content-type", "application/json")
                                .end("{\"result\":\"ok\"}");
                    }
                    break;
                case "/serverError500":
                    req.response().setStatusCode(500)
                            .putHeader("content-type", "text/plain")
                            .end("Internal Server Error");
                    break;
                case "/delayed":
                    vertx.setTimer(2000, id -> {
                        req.response().setStatusCode(200)
                                .putHeader("content-type", "application/json")
                                .end("{\"result\":\"ok\"}");
                    });
                    break;
                case "/delayedInterrupt":
                    if (delayedInterruptLatch != null) {
                        delayedInterruptLatch.countDown();
                    }
                    vertx.setTimer(5000, id -> {
                        req.response().setStatusCode(200)
                                .putHeader("content-type", "application/json")
                                .end("{\"result\":\"ok\"}");
                    });
                    break;
                default:
                    req.response().setStatusCode(404).end();
            }
        }).listen(0, result -> {
            if (result.succeeded()) {
                stubServer = result.result();
                baseUrl = "http://localhost:" + stubServer.actualPort();
                try {
                    // Build a non-intercepted HttpClientService directly so that
                    // status-branching and interrupt tests bypass the full retry chain.
                    rawService = new HttpClientService();
                    Field webClientField = HttpClientService.class.getDeclaredField("webClient");
                    webClientField.setAccessible(true);
                    webClientField.set(rawService, WebClient.create(vertx));
                } catch (Exception e) {
                    ready.completeExceptionally(e);
                    return;
                }
                ready.complete(null);
            } else {
                ready.completeExceptionally(result.cause());
            }
        });

        ready.get(10, TimeUnit.SECONDS);
    }

    @AfterAll
    static void stopStubServer() throws Exception {
        if (stubServer != null) {
            stubServer.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    // --- 2xx success-path tests (injected bean, FT interceptors active) ---

    @Test
    void testHttp200DoesNotThrow() {
        httpClientService.post(baseUrl + "/ok200", "{}", Object.class);
    }

    @Test
    void testHttp201DoesNotThrow() {
        // 201 Created is a valid success code — must not trigger retries or throw.
        httpClientService.post(baseUrl + "/created201", "{}", Object.class);
    }

    @Test
    void testHttp202DoesNotThrow() {
        // 202 Accepted is the standard CloudEvents webhook acknowledgment.
        httpClientService.post(baseUrl + "/accepted202", "{}", Object.class);
    }

    @Test
    void testHttp204WithVoidClassReturnsNull() {
        // 204 No Content with Void.class must return null without deserialization attempt.
        Object result = httpClientService.post(baseUrl + "/noContent204", "{}", Void.class);
        assertNull(result);
    }

    @Test
    void testVoidClassWithBodyReturnsNull() {
        // A response with a body must return null without deserializing when Void.class is expected.
        Object result = httpClientService.post(baseUrl + "/ok200", "{}", Void.class);
        assertNull(result);
    }

    // --- Behavioral abortOn and retry tests (injected bean, FT interceptors active) ---

    @Test
    void testHttp204WithNonVoidClassAbortsWithoutRetries() {
        // Empty body when non-Void is expected throws HttpClientContentException and must abort on attempt 1.
        int countBefore = requestCounts.getOrDefault("/noContent204", new AtomicInteger()).get();
        assertThrows(HttpClientContentException.class, () ->
                httpClientService.post(baseUrl + "/noContent204", "{}", Object.class));
        int countAfter = requestCounts.get("/noContent204").get();
        assertEquals(countBefore + 1, countAfter, "Content error must abort immediately without retries");
    }

    @Test
    void testMalformedJsonAbortsWithoutRetries() {
        // Malformed JSON throws HttpClientContentException and must abort on attempt 1 without retries.
        assertThrows(HttpClientContentException.class, () ->
                httpClientService.post(baseUrl + "/malformedJson", "{}", Object.class));
        assertEquals(1, requestCounts.get("/malformedJson").get(),
                "Malformed JSON response must abort immediately without retries");
    }

    @Test
    void testHttp400BadRequestAbortsWithoutRetries() {
        // 400 Bad Request throws HttpClientErrorException and must abort on attempt 1 without retries.
        assertThrows(HttpClientErrorException.class, () ->
                httpClientService.post(baseUrl + "/clientError400", "{}", Object.class));
        assertEquals(1, requestCounts.get("/clientError400").get(),
                "400 Bad Request must abort immediately without retries");
    }

    @Test
    void testHttp404NotFoundAbortsWithoutRetries() {
        // 404 Not Found throws HttpClientErrorException and must abort on attempt 1 without retries.
        assertThrows(HttpClientErrorException.class, () ->
                httpClientService.post(baseUrl + "/clientError404", "{}", Object.class));
        assertEquals(1, requestCounts.get("/clientError404").get(),
                "404 Not Found must abort immediately without retries");
    }

    @Test
    void testServerError5xxIsRetried() {
        // Transient 5xx server errors must be retried with backoff until success.
        httpClientService.post(baseUrl + "/retryThenSuccess", "{}", Object.class);
        assertEquals(3, requestCounts.get("/retryThenSuccess").get(),
                "Transient 5xx error should be retried until success");
    }

    @Test
    void testInterruptAbortsRetriesOnInjectedBean() throws Exception {
        // Asserts that when a thread executing the CDI-managed (FT-intercepted) bean is interrupted,
        // the call fails with an interrupt exception and @Retry aborts immediately without executing retries.
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        delayedInterruptLatch = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            try {
                httpClientService.post(baseUrl + "/delayedInterrupt", "{}", Object.class);
            } catch (Throwable t) {
                thrown.set(t);
            }
        });

        worker.start();
        assertTrue(delayedInterruptLatch.await(5, TimeUnit.SECONDS),
                "Server should receive the delayedInterrupt request before worker thread is interrupted");

        worker.interrupt();
        worker.join(5000);

        Throwable ex = thrown.get();
        if (ex instanceof io.quarkus.arc.ArcUndeclaredThrowableException && ex.getCause() != null) {
            ex = ex.getCause();
        }
        assertTrue(ex instanceof HttpClientInterruptedException || ex instanceof InterruptedException,
                "Expected HttpClientInterruptedException or InterruptedException from FT-intercepted bean, got: " + ex);
        assertEquals(1, requestCounts.get("/delayedInterrupt").get(),
                "Interrupted call must abort immediately on attempt 1 without retrying");
    }

    // --- Status-branching and interrupt tests (non-intercepted rawService) ---

    @Test
    void testNon2xxThrowsHttpClientException() {
        // Uses rawService to skip the 8-retry/exponential-backoff chain — resolves in one attempt.
        assertThrows(HttpClientException.class, () ->
                rawService.post(baseUrl + "/serverError500", "{}", Object.class));
    }

    @Test
    void testInterruptRestoresFlag() {
        // Uses rawService (no @Timeout interceptor) so the method runs on the calling thread.
        // Pre-interrupting the thread causes future.get() to throw InterruptedException,
        // which our catch block wraps in HttpClientInterruptedException and restores the flag.
        Thread.currentThread().interrupt();
        try {
            HttpClientException ex = assertThrows(HttpClientException.class, () ->
                    rawService.post(baseUrl + "/delayed", "{}", Object.class));
            // The exception must be the interrupt-specific subtype so @Retry abortOn works.
            assertInstanceOf(HttpClientInterruptedException.class, ex,
                    "Expected HttpClientInterruptedException for interrupt path");
            // Interrupt flag must be restored so callers can observe the interruption.
            assertTrue(Thread.currentThread().isInterrupted(),
                    "Interrupt flag must be restored after InterruptedException is caught");
        } finally {
            // Clear the flag so subsequent tests are not affected.
            Thread.interrupted();
        }
    }
}
