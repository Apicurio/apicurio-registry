package io.apicurio.registry.client.common;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies what {@link HttpLoggingInterceptor} logs for a real HTTP round trip.
 */
class HttpLoggingInterceptorTest {

    private static final String RESPONSE_BODY = "{\"ok\":true}";

    private static final String GROUPS_PATH = "/apis/registry/v3/groups";

    private static final String REDIRECT_PATH = "/moved";

    private static Vertx vertx;
    private static HttpServer server;
    private static int port;

    private final List<String> logged = new CopyOnWriteArrayList<>();

    private Logger logger;
    private Handler captureHandler;
    private Level originalLevel;
    private boolean originalUseParentHandlers;

    @BeforeAll
    static void startServer() throws Exception {
        vertx = Vertx.vertx();
        server = vertx.createHttpServer()
                .requestHandler(request -> request.body().onSuccess(body -> {
                    if (REDIRECT_PATH.equals(request.path())) {
                        request.response()
                                .setStatusCode(302)
                                .putHeader("Location", "http://localhost:" + port + GROUPS_PATH)
                                .end();
                        return;
                    }
                    request.response()
                            .setStatusCode(201)
                            .putHeader("Content-Type", "application/json")
                            .putHeader("Set-Cookie", "session=super-secret")
                            .end(RESPONSE_BODY);
                }))
                .listen(0)
                .toCompletionStage()
                .toCompletableFuture()
                .get(30, TimeUnit.SECONDS);
        port = server.actualPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.close();
        }
        if (vertx != null) {
            vertx.close();
        }
    }

    @BeforeEach
    void captureLogRecords() {
        logger = Logger.getLogger(HttpLoggingInterceptor.LOGGER_NAME);
        originalLevel = logger.getLevel();
        originalUseParentHandlers = logger.getUseParentHandlers();
        captureHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        captureHandler.setLevel(Level.ALL);
        logger.addHandler(captureHandler);
        logger.setLevel(Level.FINE);
        logger.setUseParentHandlers(false);
    }

    @AfterEach
    void restoreLogger() {
        logger.removeHandler(captureHandler);
        logger.setLevel(originalLevel);
        logger.setUseParentHandlers(originalUseParentHandlers);
        logged.clear();
    }

    @Test
    void logsRequestMethodUrlHeadersAndBody() throws Exception {
        post(GROUPS_PATH, "{\"groupId\":\"g\"}");

        var request = recordStartingWith("HTTP request:");
        assertTrue(request.contains("> POST http://localhost:" + port + GROUPS_PATH), request);
        assertTrue(request.contains("> Content-Type: application/json"), request);
        assertTrue(request.contains("> {\"groupId\":\"g\"}"), request);
    }

    @Test
    void logsResponseStatusHeadersAndBody() throws Exception {
        post(GROUPS_PATH, "{\"groupId\":\"g\"}");

        var response = recordStartingWith("HTTP response:");
        assertTrue(response.contains("< 201 Created"), response);
        assertTrue(response.contains("< Content-Type: application/json"), response);
        assertTrue(response.contains("< " + RESPONSE_BODY), response);
    }

    @Test
    void redactsCredentialHeaders() throws Exception {
        post(GROUPS_PATH, "{\"groupId\":\"g\"}");

        var request = recordStartingWith("HTTP request:");
        assertTrue(request.contains("> Authorization: <redacted>"), request);
        assertFalse(request.contains("Bearer super-secret-token"), request);

        var response = recordStartingWith("HTTP response:");
        assertTrue(response.contains("< Set-Cookie: <redacted>"), response);
        assertFalse(response.contains("session=super-secret"), response);
    }

    @Test
    void truncatesLongBodies() throws Exception {
        var body = "x".repeat(10_000);
        post(GROUPS_PATH, body);

        var request = recordStartingWith("HTTP request:");
        assertTrue(request.contains("... (truncated, 10000 characters total)"), request);
        assertFalse(request.contains("x".repeat(8193)), request);
    }

    @Test
    void redactsCredentialQueryParameters() throws Exception {
        post(GROUPS_PATH + "?access_token=super-secret-token&groupId=g&code=super-secret-code",
                "{\"groupId\":\"g\"}");

        var request = recordStartingWith("HTTP request:");
        assertTrue(request.contains("> POST http://localhost:" + port + GROUPS_PATH
                + "?access_token=<redacted>&groupId=g&code=<redacted>"), request);
        assertFalse(request.contains("super-secret-token"), request);
        assertFalse(request.contains("super-secret-code"), request);
    }

    @Test
    void tagsRedirectHopsSoTheyAreNotReadAsSeparateCalls() throws Exception {
        get(REDIRECT_PATH);

        var first = recordStartingWith("HTTP request:");
        assertTrue(first.contains("> GET http://localhost:" + port + REDIRECT_PATH), first);

        var hop = recordStartingWith("HTTP request (redirect 1):");
        assertTrue(hop.contains("> GET http://localhost:" + port + GROUPS_PATH), hop);

        var response = recordStartingWith("HTTP response (after 1 redirect):");
        assertTrue(response.contains("< 201 Created"), response);
    }

    @Test
    void logsNothingWhenNotInstalled() throws Exception {
        var webClient = WebClient.create(vertx);
        try {
            webClient.post(port, "localhost", GROUPS_PATH)
                    .sendBuffer(Buffer.buffer("{}"))
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
        } finally {
            webClient.close();
        }

        assertEquals(List.of(), logged);
    }

    private void get(String path) throws Exception {
        var webClient = WebClient.create(vertx);
        HttpLoggingInterceptor.install(webClient);
        try {
            webClient.get(port, "localhost", path)
                    .send()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
        } finally {
            webClient.close();
        }
    }

    private void post(String path, String body) throws Exception {
        var webClient = WebClient.create(vertx);
        HttpLoggingInterceptor.install(webClient);
        try {
            webClient.post(port, "localhost", path)
                    .putHeader("Content-Type", "application/json")
                    .putHeader("Authorization", "Bearer super-secret-token")
                    .sendBuffer(Buffer.buffer(body))
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
        } finally {
            webClient.close();
        }
    }

    private String recordStartingWith(String prefix) {
        return logged.stream()
                .filter(record -> record.startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No log record starting with '" + prefix + "' in " + logged));
    }
}
