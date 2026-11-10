package io.apicurio.registry.client.common.auth;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link VertXAuthFactory#buildOIDCWebClient} configures the OAuth2 client-credentials flow
 * on {@code Oauth2Credentials} (rather than only on {@code OAuth2Options}, whose {@code setFlow} method was
 * removed in Vert.x 5) by exercising real HTTP round trips against an embedded token endpoint.
 */
class VertXAuthFactoryTest {

    private static final String TOKEN_PATH = "/token";

    private static final String PROTECTED_PATH = "/protected";

    private Vertx vertx;
    private HttpServer server;
    private int port;

    private final AtomicReference<String> capturedTokenRequestBody = new AtomicReference<>();
    private final AtomicReference<String> capturedAuthorizationHeader = new AtomicReference<>();
    private volatile boolean rejectTokenRequests;

    @BeforeEach
    void startServer() throws Exception {
        vertx = Vertx.vertx();
        server = vertx.createHttpServer().requestHandler(request -> request.body().onSuccess(body -> {
            if (TOKEN_PATH.equals(request.path())) {
                capturedTokenRequestBody.set(body.toString());
                if (rejectTokenRequests) {
                    request.response().setStatusCode(401).putHeader("Content-Type", "application/json")
                            .end("{\"error\":\"invalid_client\"}");
                } else {
                    request.response().putHeader("Content-Type", "application/json").end(
                            "{\"access_token\":\"tok\",\"token_type\":\"bearer\",\"expires_in\":3600}");
                }
                return;
            }
            capturedAuthorizationHeader.set(request.getHeader("Authorization"));
            request.response().setStatusCode(200).end("ok");
        })).listen(0).toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
        port = server.actualPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    void sendsClientCredentialsGrantWithScope() throws Exception {
        WebClient client = VertXAuthFactory.buildOIDCWebClient(vertx, tokenUrl(), "my-client", "my-secret",
                "my-scope");
        try {
            callProtectedEndpoint(client);
        } finally {
            client.close();
        }

        String tokenRequestBody = capturedTokenRequestBody.get();
        assertTrue(tokenRequestBody.contains("grant_type=client_credentials"), tokenRequestBody);
        assertTrue(tokenRequestBody.contains("scope=my-scope"), tokenRequestBody);
        assertEquals("Bearer tok", capturedAuthorizationHeader.get());
    }

    @Test
    void sendsClientCredentialsGrantWithoutScope() throws Exception {
        WebClient client = VertXAuthFactory.buildOIDCWebClient(vertx, tokenUrl(), "my-client", "my-secret");
        try {
            callProtectedEndpoint(client);
        } finally {
            client.close();
        }

        String tokenRequestBody = capturedTokenRequestBody.get();
        assertTrue(tokenRequestBody.contains("grant_type=client_credentials"), tokenRequestBody);
        assertFalse(tokenRequestBody.contains("scope="), tokenRequestBody);
        assertEquals("Bearer tok", capturedAuthorizationHeader.get());
    }

    @Test
    void rejectedClientCredentialsFailTheCall() {
        rejectTokenRequests = true;
        WebClient client = VertXAuthFactory.buildOIDCWebClient(vertx, tokenUrl(), "my-client", "wrong-secret");
        try {
            ExecutionException exception = assertThrows(ExecutionException.class,
                    () -> callProtectedEndpoint(client));
            assertTrue(exception.getCause().getMessage().contains("invalid_client"),
                    exception.getCause().getMessage());
            assertFalse(capturedTokenRequestBody.get() == null);
        } finally {
            client.close();
        }
    }

    private void callProtectedEndpoint(WebClient client) throws Exception {
        client.get(port, "localhost", PROTECTED_PATH).send().toCompletionStage().toCompletableFuture()
                .get(30, TimeUnit.SECONDS);
    }

    private String tokenUrl() {
        return "http://localhost:" + port + TOKEN_PATH;
    }
}
