package io.apicurio.registry.client.common;

import com.microsoft.kiota.HttpMethod;
import com.microsoft.kiota.RequestAdapter;
import com.microsoft.kiota.RequestInformation;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies that {@link VertxAdapterFactory} honours {@link RegistryClientOptions#enableCookies()}: a
 * cookie set on the first response is sent back on the second request when enabled, and not when
 * disabled or left at the default. Also verifies that a client which is already a
 * {@code WebClientSession} (basic auth, or a caller-supplied one) is left alone and keeps working.
 */
class VertxAdapterCookieTest {

    private static final String PATH = "/apis/registry/v3/system/info";

    private Vertx vertx;
    private HttpServer server;
    private int port;
    private final List<String> receivedCookieHeaders = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        vertx = Vertx.vertx();
        server = vertx.createHttpServer().requestHandler(request -> request.body().onSuccess(body -> {
            receivedCookieHeaders.add(request.getHeader("Cookie"));
            request.response()
                    .putHeader("Set-Cookie", "ROUTEID=server-1; Path=/")
                    .end("{}");
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
    void cookiesSentBackOnSecondRequestWhenEnabled() {
        RequestAdapter adapter = createAdapter(RegistryClientOptions.create()
                .registryUrl(serverUrl())
                .httpAdapter(HttpAdapterType.VERTX)
                .vertx(vertx)
                .enableCookies());

        adapter.sendPrimitive(requestInfo(), null, String.class);
        adapter.sendPrimitive(requestInfo(), null, String.class);

        assertNoCookieOnFirstRequest();
        assertTrue(receivedCookieHeaders.get(1) != null && receivedCookieHeaders.get(1).contains("ROUTEID=server-1"),
                "Expected the second request to send back the cookie set by the first response, got: "
                        + receivedCookieHeaders);
    }

    @Test
    void cookiesNotSentWhenDisabled() {
        RequestAdapter adapter = createAdapter(RegistryClientOptions.create()
                .registryUrl(serverUrl())
                .httpAdapter(HttpAdapterType.VERTX)
                .vertx(vertx));

        adapter.sendPrimitive(requestInfo(), null, String.class);
        adapter.sendPrimitive(requestInfo(), null, String.class);

        assertNoCookieOnFirstRequest();
        assertFalse(receivedCookieHeaders.get(1) != null && receivedCookieHeaders.get(1).contains("ROUTEID"),
                "Expected the second request not to carry the cookie since cookies are disabled, got: "
                        + receivedCookieHeaders);
    }

    @Test
    void enablingCookiesWithAnAlreadySessionedClientDoesNotThrowAndStillSendsTheCookie() {
        WebClient sessionClient = WebClientSession.create(WebClient.create(vertx));
        try {
            RequestAdapter adapter = assertDoesNotThrow(() -> createAdapter(RegistryClientOptions.create()
                    .registryUrl(serverUrl())
                    .httpAdapter(HttpAdapterType.VERTX)
                    .customWebClient(sessionClient)
                    .enableCookies()));

            adapter.sendPrimitive(requestInfo(), null, String.class);
            adapter.sendPrimitive(requestInfo(), null, String.class);

            assertNoCookieOnFirstRequest();
            assertTrue(
                    receivedCookieHeaders.get(1) != null && receivedCookieHeaders.get(1).contains("ROUTEID=server-1"),
                    "Expected the second request to still send back the cookie via the client's own session, got: "
                            + receivedCookieHeaders);
        } finally {
            sessionClient.close();
        }
    }

    private void assertNoCookieOnFirstRequest() {
        assertFalse(receivedCookieHeaders.get(0) != null && receivedCookieHeaders.get(0).contains("ROUTEID"),
                "Expected no cookie on the first request, got: " + receivedCookieHeaders);
    }

    private String serverUrl() {
        return "http://localhost:" + port;
    }

    private RequestAdapter createAdapter(RegistryClientOptions options) {
        RequestAdapter adapter = RegistryClientRequestAdapterFactory.createRequestAdapter(options, Version.V3);
        adapter.setBaseUrl(serverUrl());
        return adapter;
    }

    private RequestInformation requestInfo() {
        RequestInformation info = new RequestInformation();
        info.httpMethod = HttpMethod.GET;
        info.setUri(URI.create(serverUrl() + PATH));
        return info;
    }
}
