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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests a content accepter configured as a webhook: the configured headers are sent with the request,
 * and the response is read as the {@code ContentAccepterResponse} of the webhook contract
 * ({@code {"accepted": boolean}}), with a bare JSON boolean still accepted.
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
        assertEquals(List.of(TOKEN), receivedAuthorization);
    }

    @Test
    void testWebhookRequiringTokenRejectsRequestWithoutHeaders() {
        assertFalse(accepts("/requiresToken", null));
        assertEquals(List.of("<none>"), receivedAuthorization);
    }

    @Test
    void testNoHeadersConfiguredSendsNoAuthorization() {
        assertTrue(accepts("/acceptedTrue", null));
        assertEquals(List.of("<none>"), receivedAuthorization);
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
}
