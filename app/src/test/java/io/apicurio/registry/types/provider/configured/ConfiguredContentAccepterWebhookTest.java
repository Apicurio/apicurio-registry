package io.apicurio.registry.types.provider.configured;

import io.apicurio.registry.config.artifactTypes.ArtifactTypeConfiguration;
import io.apicurio.registry.config.artifactTypes.WebhookProvider;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.http.HttpClientService;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests a content accepter configured as a webhook: the configured headers are sent with the request,
 * and the response is read as the {@code ContentAccepterResponse} of the webhook contract
 * ({@code {"accepted": boolean}}), with a bare JSON boolean still accepted and logged at DEBUG.
 */
class ConfiguredContentAccepterWebhookTest {

    private static final String TOKEN = "Bearer accepter-token";

    private static Vertx vertx;
    private static HttpServer stubServer;
    private static String baseUrl;
    private static HttpClientService httpClientService;
    private static final List<String> receivedAuthorization = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void startStubServer() throws Exception {
        vertx = Vertx.vertx();
        CompletableFuture<Void> ready = new CompletableFuture<>();

        vertx.createHttpServer().requestHandler(req -> req.body().onComplete(body -> {
            String authorization = req.getHeader("Authorization");
            receivedAuthorization.add(authorization == null ? "<none>" : authorization);
            String response;
            switch (req.path()) {
                case "/requiresToken":
                    if (!TOKEN.equals(authorization)) {
                        req.response().setStatusCode(401).end();
                        return;
                    }
                    response = new JsonObject().put("accepted", true).encode();
                    break;
                case "/acceptedTrue":
                    response = new JsonObject().put("accepted", true).encode();
                    break;
                case "/acceptedFalse":
                    response = new JsonObject().put("accepted", false).encode();
                    break;
                case "/bareTrue":
                    response = "true";
                    break;
                case "/bareFalse":
                    response = "false";
                    break;
                case "/missingAccepted":
                    response = "{}";
                    break;
                case "/acceptedNotBoolean":
                    response = new JsonObject().put("accepted", "yes").encode();
                    break;
                default:
                    req.response().setStatusCode(404).end();
                    return;
            }
            req.response().setStatusCode(200).putHeader("content-type", "application/json").end(response);
        })).listen(0, result -> {
            if (result.failed()) {
                ready.completeExceptionally(result.cause());
                return;
            }
            stubServer = result.result();
            baseUrl = "http://localhost:" + stubServer.actualPort();
            try {
                // A plain HttpClientService without fault tolerance interceptors, so that a 401 fails at once.
                httpClientService = new HttpClientService();
                Field webClientField = HttpClientService.class.getDeclaredField("webClient");
                webClientField.setAccessible(true);
                webClientField.set(httpClientService, WebClient.create(vertx));
                ready.complete(null);
            } catch (Exception e) {
                ready.completeExceptionally(e);
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

    @BeforeEach
    void clearReceived() {
        receivedAuthorization.clear();
    }

    @Test
    void testConfiguredHeadersAreSent() {
        assertTrue(accepts("/requiresToken", Map.of("Authorization", TOKEN)));
        assertEquals(List.of(TOKEN), received());
    }

    @Test
    void testWebhookRequiringTokenRejectsRequestWithoutHeaders() {
        assertFalse(accepts("/requiresToken", null));
        assertEquals(List.of("<none>"), received());
    }

    @Test
    void testNoHeadersConfiguredSendsNoAuthorization() {
        assertTrue(accepts("/acceptedTrue", null));
        assertEquals(List.of("<none>"), received());
    }

    @Test
    void testContractResponse() {
        assertTrue(accepts("/acceptedTrue", null));
        assertFalse(accepts("/acceptedFalse", null));
    }

    @Test
    void testBareBooleanResponseIsStillAccepted() {
        assertTrue(accepts("/bareTrue", null));
        assertFalse(accepts("/bareFalse", null));
    }

    @Test
    void testBareBooleanResponseIsLoggedAtDebug() {
        List<LogRecord> records = captureDebug(() -> assertTrue(accepts("/bareTrue", null)));

        assertEquals(1, records.size(), "expected exactly one DEBUG record for the legacy bare boolean");
        String message = records.get(0).getMessage();
        assertTrue(message.contains("bare boolean") && message.contains("TEST_WEBHOOK"),
                "expected the legacy form and the artifact type in the DEBUG record, got: " + message);
    }

    @Test
    void testContractResponseIsNotLoggedAtDebug() {
        List<LogRecord> records = captureDebug(() -> assertTrue(accepts("/acceptedTrue", null)));

        assertEquals(0, records.size());
    }

    @Test
    void testResponseWithoutBooleanAcceptedIsNotAccepted() {
        assertFalse(accepts("/missingAccepted", null));
        assertFalse(accepts("/acceptedNotBoolean", null));
    }

    @Test
    void testIsAccepted() {
        assertTrue(ConfiguredContentAccepter.isAccepted(Boolean.TRUE));
        assertFalse(ConfiguredContentAccepter.isAccepted(Boolean.FALSE));
        assertTrue(ConfiguredContentAccepter.isAccepted(Map.of("accepted", true)));
        assertFalse(ConfiguredContentAccepter.isAccepted(Map.of("accepted", false)));
        assertTrue(ConfiguredContentAccepter.isAccepted(new JsonObject().put("accepted", true)));
        assertFalse(ConfiguredContentAccepter.isAccepted(new JsonObject()));
        assertFalse(ConfiguredContentAccepter.isAccepted(Collections.emptyMap()));
        assertFalse(ConfiguredContentAccepter.isAccepted("true"));
        assertFalse(ConfiguredContentAccepter.isAccepted(null));
    }

    private static List<String> received() {
        return List.copyOf(receivedAuthorization);
    }

    private static boolean accepts(String path, Map<String, String> headers) {
        WebhookProvider provider = new WebhookProvider();
        provider.setType("webhook");
        provider.setUrl(baseUrl + path);
        provider.setHeaders(headers);

        ArtifactTypeConfiguration artifactType = new ArtifactTypeConfiguration();
        artifactType.setArtifactType("TEST_WEBHOOK");
        artifactType.setContentAccepter(provider);

        ConfiguredContentAccepter accepter = new ConfiguredContentAccepter(httpClientService, artifactType);
        return accepter.acceptsContent(TypedContent.create("{}", "application/json"), Collections.emptyMap());
    }

    /**
     * Captures the DEBUG-level (JUL {@code FINE}) records logged by {@link ConfiguredContentAccepter} while
     * {@code action} runs, by attaching a {@code java.util.logging} {@link Handler} by logger name and
     * lowering that logger's level for the duration. This works because {@code app/pom.xml} configures
     * {@code java.util.logging.manager=org.jboss.logmanager.LogManager}, through which SLF4J is routed.
     */
    private static List<LogRecord> captureDebug(Runnable action) {
        String loggerName = ConfiguredContentAccepter.class.getName();
        Logger julLogger = LogManager.getLogManager().getLogger(loggerName);
        if (julLogger == null) {
            julLogger = Logger.getLogger(loggerName);
        }
        List<LogRecord> captured = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                if (logRecord.getLevel().intValue() == Level.FINE.intValue()) {
                    captured.add(logRecord);
                }
            }

            @Override
            public void flush() {
                // No-op: nothing is buffered by this test handler.
            }

            @Override
            public void close() {
                // No-op: this test handler holds no resources.
            }
        };
        Level previousLevel = julLogger.getLevel();
        julLogger.setLevel(Level.FINE);
        julLogger.addHandler(handler);
        try {
            action.run();
        } finally {
            julLogger.removeHandler(handler);
            julLogger.setLevel(previousLevel);
        }
        return List.copyOf(captured);
    }
}
