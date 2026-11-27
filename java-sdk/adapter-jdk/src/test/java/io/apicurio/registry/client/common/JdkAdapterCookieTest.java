package io.apicurio.registry.client.common;

import com.microsoft.kiota.HttpMethod;
import com.microsoft.kiota.RequestAdapter;
import com.microsoft.kiota.RequestInformation;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the JDK adapter supports {@link RegistryClientOptions#enableCookies()}: a cookie set on
 * the first response is sent back on the second request when cookies are enabled, and not when they
 * aren't.
 */
class JdkAdapterCookieTest {

    private HttpServer server;
    private final List<String> receivedCookieHeaders = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/apis/registry/v3/system/info", exchange -> {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            receivedCookieHeaders.add(cookieHeader);
            exchange.getResponseHeaders().add("Set-Cookie", "ROUTEID=server-1; Path=/");
            byte[] body = "{}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void cookiesSentBackOnSecondRequestWhenEnabled() {
        RequestAdapter adapter = createAdapter(RegistryClientOptions.create()
                .registryUrl(serverUrl())
                .httpAdapter(HttpAdapterType.JDK)
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
                .httpAdapter(HttpAdapterType.JDK));

        adapter.sendPrimitive(requestInfo(), null, String.class);
        adapter.sendPrimitive(requestInfo(), null, String.class);

        assertNoCookieOnFirstRequest();
        assertFalse(receivedCookieHeaders.get(1) != null && receivedCookieHeaders.get(1).contains("ROUTEID"),
                "Expected the second request not to carry the cookie since cookies are disabled, got: "
                        + receivedCookieHeaders);
    }

    private void assertNoCookieOnFirstRequest() {
        assertFalse(receivedCookieHeaders.get(0) != null && receivedCookieHeaders.get(0).contains("ROUTEID"),
                "Expected no cookie on the first request, got: " + receivedCookieHeaders);
    }

    private String serverUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private RequestAdapter createAdapter(RegistryClientOptions options) {
        RequestAdapter adapter = RegistryClientRequestAdapterFactory.createRequestAdapter(options, Version.V3);
        adapter.setBaseUrl(serverUrl());
        return adapter;
    }

    private RequestInformation requestInfo() {
        RequestInformation info = new RequestInformation();
        info.httpMethod = HttpMethod.GET;
        info.setUri(URI.create(serverUrl() + "/apis/registry/v3/system/info"));
        return info;
    }
}
