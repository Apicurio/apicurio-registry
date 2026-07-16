package io.apicurio.registry.http;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link HttpClientService} verifying correct HTTP status code handling
 * and interrupt flag restoration.
 *
 * <p>Uses a Vert.x HTTP stub server (no external dependencies required) following
 * the same pattern as {@code WebhookArtifactTypesTest}.
 */
@QuarkusTest
public class HttpClientServiceTest {

    @Inject
    HttpClientService httpClientService;

    private static Vertx vertx;
    private static HttpServer server;
    private static int serverPort;
    private static final AtomicInteger requestCount = new AtomicInteger(0);
    private static CountDownLatch slowRequestReceived;

    /**
     * Simple response class for JSON deserialization in tests.
     */
    public static class TestResponse {
        public String message;
    }

    @BeforeAll
    static void startStubServer() throws Exception {
        vertx = Vertx.vertx();
        CountDownLatch latch = new CountDownLatch(1);

        server = vertx.createHttpServer();
        server.requestHandler(req -> {
            requestCount.incrementAndGet();
            String path = req.path();

            req.bodyHandler(body -> {
                switch (path) {
                    case "/status-200":
                        req.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", "application/json")
                                .end("{\"message\":\"ok\"}");
                        break;
                    case "/status-202":
                        req.response()
                                .setStatusCode(202)
                                .putHeader("Content-Type", "application/json")
                                .end("{\"message\":\"accepted\"}");
                        break;
                    case "/status-204":
                        req.response()
                                .setStatusCode(204)
                                .end();
                        break;
                    case "/slow":
                        // Signal that the request was received, then do not respond —
                        // connection stays open for interrupt testing
                        slowRequestReceived.countDown();
                        break;
                    default:
                        req.response().setStatusCode(404).end();
                        break;
                }
            });
        });

        server.listen(0).onComplete(ar -> {
            if (ar.succeeded()) {
                serverPort = ar.result().actualPort();
                latch.countDown();
            }
        });

        latch.await();
    }

    @AfterAll
    static void stopStubServer() {
        if (server != null) {
            server.close();
        }
        if (vertx != null) {
            vertx.close();
        }
    }

    @BeforeEach
    void resetState() {
        requestCount.set(0);
        slowRequestReceived = new CountDownLatch(1);
    }

    /**
     * Verifies that an HTTP 202 Accepted response is treated as success,
     * returns the parsed body, and does not trigger SmallRye @Retry.
     */
    @Test
    void testHttp202DoesNotThrowAndDoesNotRetry() throws HttpClientException {
        TestResponse response = httpClientService.post(
                stubUrl("/status-202"), "{}", TestResponse.class);

        assertNotNull(response, "202 with body should return parsed response");
        assertEquals("accepted", response.message);
        assertEquals(1, requestCount.get(),
                "Server should receive exactly 1 request (no retries on 202)");
    }

    /**
     * Verifies that an HTTP 204 No Content response returns null
     * without attempting JSON deserialization.
     */
    @Test
    void testHttp204ReturnsNullWithoutDeserialization() throws HttpClientException {
        TestResponse response = httpClientService.post(
                stubUrl("/status-204"), "{}", TestResponse.class);

        assertNull(response, "204 No Content should return null");
        assertEquals(1, requestCount.get(),
                "Server should receive exactly 1 request (no retries on 204)");
    }

    /**
     * Verifies that an HTTP 200 response with a JSON body is parsed correctly
     * (baseline behavior preserved).
     */
    @Test
    void testHttp200ReturnsBody() throws HttpClientException {
        TestResponse response = httpClientService.post(
                stubUrl("/status-200"), "{}", TestResponse.class);

        assertNotNull(response);
        assertEquals("ok", response.message);
        assertEquals(1, requestCount.get(),
                "Server should receive exactly 1 request (no retries on 200)");
    }

    /**
     * Verifies that interrupting a thread blocked on {@code Future.get()} results in
     * an {@link HttpClientException} and that the thread's interrupt flag is restored
     * per the Java concurrency contract.
     */
    @Test
    void testInterruptRestoresFlag() throws Exception {
        AtomicReference<Throwable> caught = new AtomicReference<>();
        AtomicBoolean interruptedAfter = new AtomicBoolean(false);

        Thread testThread = new Thread(() -> {
            try {
                // This will block on .get() because /slow never responds
                httpClientService.post(
                        stubUrl("/slow"), "{}", TestResponse.class);
            } catch (Throwable e) {
                caught.set(e);
                interruptedAfter.set(Thread.currentThread().isInterrupted());
            }
        });

        testThread.start();
        // Wait until the stub server has received the request, ensuring .get() is blocking
        slowRequestReceived.await(5, TimeUnit.SECONDS);
        testThread.interrupt();
        testThread.join(10_000);

        assertNotNull(caught.get(), "Should have caught an exception after interrupt");
        assertInstanceOf(HttpClientInterruptException.class, caught.get(),
                "InterruptedException should be wrapped in HttpClientInterruptException");
        assertTrue(interruptedAfter.get(),
                "Thread interrupt flag should be restored after catching InterruptedException");
    }

    private String stubUrl(String path) {
        return "http://localhost:" + serverPort + path;
    }
}
