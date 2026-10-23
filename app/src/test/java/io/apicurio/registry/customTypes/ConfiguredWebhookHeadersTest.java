package io.apicurio.registry.customTypes;

import io.apicurio.registry.config.artifactTypes.ArtifactTypeConfiguration;
import io.apicurio.registry.config.artifactTypes.WebhookProvider;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.http.HttpClientService;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.provider.configured.ConfiguredContentAccepter;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that headers configured on a {@link WebhookProvider} are actually applied to the
 * outbound request made by the configured artifact type providers.
 *
 * <p>This exercises the full wiring (provider configuration through
 * {@code AbstractWebhookDelegate.invokeHook} to {@code HttpClientService}) rather than the
 * header-sending mechanism alone, so it fails if the provider's headers stop being passed through.
 */
@QuarkusTest
class ConfiguredWebhookHeadersTest {

    private static Vertx vertx;
    private static HttpServer stubServer;
    private static String baseUrl;

    /** Headers received by the stub webhook, keyed by request path. Header names are lower-cased. */
    private static final ConcurrentHashMap<String, Map<String, String>> capturedHeaders = new ConcurrentHashMap<>();

    /** CDI-managed bean, so the fault tolerance interceptors are active. */
    @Inject
    HttpClientService httpClientService;

    @BeforeAll
    static void startStubWebhook() throws Exception {
        vertx = Vertx.vertx();
        CompletableFuture<Void> ready = new CompletableFuture<>();

        vertx.createHttpServer().requestHandler(req -> {
            Map<String, String> received = new HashMap<>();
            req.headers().forEach(entry -> received.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue()));
            capturedHeaders.put(req.path(), received);
            // The content accepter webhook returns a bare boolean.
            req.response().setStatusCode(200)
                    .putHeader("content-type", "application/json")
                    .end("true");
        }).listen(0, result -> {
            if (result.succeeded()) {
                stubServer = result.result();
                baseUrl = "http://localhost:" + stubServer.actualPort();
                ready.complete(null);
            } else {
                ready.completeExceptionally(result.cause());
            }
        });

        ready.get(10, TimeUnit.SECONDS);
    }

    @AfterAll
    static void stopStubWebhook() throws Exception {
        if (stubServer != null) {
            stubServer.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void testConfiguredProviderSendsWebhookHeaders() {
        String path = "/contentAccepter/withHeaders";
        ConfiguredContentAccepter accepter = new ConfiguredContentAccepter(httpClientService,
                webhookArtifactType(path, Map.of(
                        "X-Api-Key", "shared-secret",
                        "Authorization", "Bearer configured-token")));

        boolean accepted = accepter.acceptsContent(jsonContent(), Map.of());

        // acceptsContent swallows webhook failures and returns false, so a true result also
        // confirms the request round-tripped successfully.
        assertTrue(accepted, "Stub webhook returned true, so the content must be accepted");

        Map<String, String> received = capturedHeaders.get(path);
        assertNotNull(received, "Stub webhook should have received a request");
        assertEquals("shared-secret", received.get("x-api-key"),
                "Header configured on the webhook provider must reach the endpoint");
        assertEquals("Bearer configured-token", received.get("authorization"));
        assertEquals("application/json", received.get("content-type"));
    }

    @Test
    void testConfiguredProviderWithoutHeadersSendsOnlyContentType() {
        // A provider that omits "headers" deserializes to a null map, the common case for the
        // six configured providers, and it must not break the call.
        String path = "/contentAccepter/noHeaders";
        ConfiguredContentAccepter accepter = new ConfiguredContentAccepter(httpClientService,
                webhookArtifactType(path, null));

        boolean accepted = accepter.acceptsContent(jsonContent(), Map.of());

        assertTrue(accepted, "A provider without configured headers must still invoke the webhook");
        Map<String, String> received = capturedHeaders.get(path);
        assertNotNull(received, "Stub webhook should have received a request");
        assertEquals("application/json", received.get("content-type"));
        assertFalse(received.containsKey("x-api-key"));
        assertFalse(received.containsKey("authorization"));
    }

    private ArtifactTypeConfiguration webhookArtifactType(String path, Map<String, String> headers) {
        WebhookProvider provider = new WebhookProvider();
        provider.setType("webhook");
        provider.setUrl(baseUrl + path);
        provider.setHeaders(headers);

        ArtifactTypeConfiguration artifactType = new ArtifactTypeConfiguration();
        artifactType.setArtifactType("RAML");
        artifactType.setName("RAML");
        artifactType.setContentTypes(List.of(ContentTypes.APPLICATION_JSON));
        artifactType.setContentAccepter(provider);
        return artifactType;
    }

    private TypedContent jsonContent() {
        return TypedContent.create(ContentHandle.create("{}"), ContentTypes.APPLICATION_JSON);
    }
}
