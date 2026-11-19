package io.apicurio.registry.mcp;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ClientCredentialsStartupCheck} against a local stub token endpoint.
 */
class ClientCredentialsStartupCheckTest {

    private static final String CLIENT_ID = "admin-client";
    private static final String SECRET = "s3cr3t-value-never-logged";
    private static final String KEYCLOAK_401 =
            "{\"error\":\"unauthorized_client\",\"error_description\":\"Invalid client or Invalid client credentials\"}";

    private HttpServer server;
    private String tokenEndpoint;
    private final List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
    private final List<String> bodies = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger requests = new AtomicInteger();
    private final List<String> logMessages = Collections.synchronizedList(new ArrayList<>());
    private Logger julLogger;
    private Handler logHandler;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/token", exchange -> {
            int n = requests.getAndIncrement();
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(requestBody);
            int idx = Math.min(n, statuses.size() - 1);
            int status = statuses.get(idx);
            String body = status == 200 ? "{\"access_token\":\"abc\",\"expires_in\":300}"
                    : status == 401 ? KEYCLOAK_401 : "unavailable";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        tokenEndpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/token";

        julLogger = Logger.getLogger(ClientCredentialsStartupCheck.class.getName());
        logHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logMessages.add(String.valueOf(record.getMessage()));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        julLogger.addHandler(logHandler);
    }

    @AfterEach
    void tearDown() {
        julLogger.removeHandler(logHandler);
        server.stop(0);
    }

    @Test
    void wrongSecretFailsAtOnceNamingEndpointAndClientButNotSecret() {
        statuses.add(401);
        var check = newCheck(true, tokenEndpoint);

        var e = assertThrows(IllegalStateException.class, check::verify);

        assertEquals("Failed to obtain an OAuth2 token from " + tokenEndpoint + " for client 'admin-client': "
                + "HTTP 401 (unauthorized_client: Invalid client or Invalid client credentials). "
                + "Check APICURIO_MCP_AUTH_CLIENT_ID and APICURIO_MCP_AUTH_CLIENT_SECRET.", e.getMessage());
        assertFalse(e.getMessage().contains(SECRET));
        assertEquals(1, requests.get(), "a 4xx answer must not be retried");
        assertTrue(bodies.get(0).contains("client_secret=" + SECRET), "the secret is sent to the endpoint");
        assertTrue(logMessages.contains(e.getMessage()), "the error is logged: " + logMessages);
        assertTrue(logMessages.stream().noneMatch(m -> m.contains(SECRET)), "secret leaked: " + logMessages);
    }

    @Test
    void rightSecretPasses() {
        statuses.add(200);
        var check = newCheck(true, tokenEndpoint);

        assertDoesNotThrow(check::verify);
        assertEquals(1, requests.get());
        assertTrue(bodies.get(0).startsWith("grant_type=client_credentials&client_id=admin-client&client_secret="));
    }

    @Test
    void transientErrorsAreRetriedThenSucceed() {
        statuses.add(503);
        statuses.add(503);
        statuses.add(200);
        var check = newCheck(true, tokenEndpoint);

        assertDoesNotThrow(check::verify);
        assertEquals(3, requests.get());
    }

    @Test
    void persistentErrorsFailAfterBoundedRetries() {
        statuses.add(503);
        var check = newCheck(true, tokenEndpoint);

        var e = assertThrows(IllegalStateException.class, check::verify);

        assertEquals("Failed to obtain an OAuth2 token from " + tokenEndpoint
                + " for client 'admin-client' after 3 attempts: HTTP 503", e.getMessage());
        assertEquals(3, requests.get());
        assertTrue(logMessages.stream().noneMatch(m -> m.contains(SECRET)), "secret leaked: " + logMessages);
    }

    @Test
    void unreachableEndpointFailsAfterBoundedRetries() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            freePort = socket.getLocalPort();
        }
        String endpoint = "http://127.0.0.1:" + freePort + "/token";
        var check = newCheck(true, endpoint);

        var e = assertThrows(IllegalStateException.class, check::verify);

        assertTrue(e.getMessage().startsWith("Failed to obtain an OAuth2 token from " + endpoint
                + " for client 'admin-client' after 3 attempts: ConnectException"), e.getMessage());
        assertFalse(e.getMessage().contains(SECRET));
    }

    @Test
    void authDisabledMakesNoRequest() {
        statuses.add(401);
        var check = newCheck(false, tokenEndpoint);

        assertDoesNotThrow(check::verify);
        assertEquals(0, requests.get());
    }

    @Test
    void missingSettingsFail() {
        var check = newCheck(true, null);

        var e = assertThrows(IllegalStateException.class, check::verify);

        assertTrue(e.getMessage().contains("'apicurio.mcp.auth.token-endpoint'"), e.getMessage());
        assertEquals(0, requests.get());
    }

    private static ClientCredentialsStartupCheck newCheck(boolean authEnabled, String endpoint) {
        return newCheck(authEnabled, endpoint, CLIENT_ID, SECRET);
    }

    static ClientCredentialsStartupCheck newCheck(boolean authEnabled, String endpoint, String clientId,
            String secret) {
        var check = new ClientCredentialsStartupCheck();
        check.rawBaseUrl = "http://localhost:8080";
        check.maxAttempts = 3;
        check.initialDelayMs = 10;
        check.config = proxy(McpConfig.class, Map.of(
                "auth", proxy(McpConfig.Auth.class, Map.of(
                        "enabled", authEnabled,
                        "tokenEndpoint", Optional.ofNullable(endpoint),
                        "clientId", Optional.of(clientId),
                        "clientSecret", Optional.of(secret),
                        "scope", Optional.empty())),
                "tls", proxy(McpConfig.Tls.class, Map.of(
                        "trustAll", false,
                        "verifyHost", true,
                        "truststore", proxy(McpConfig.Truststore.class, Map.of(
                                "type", Optional.empty(), "path", Optional.empty(), "password", Optional.empty())),
                        "keystore", proxy(McpConfig.Keystore.class, Map.of(
                                "type", Optional.empty(), "path", Optional.empty(), "password", Optional.empty()))))));
        return check;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (p, method, args) -> {
            if (!values.containsKey(method.getName())) {
                throw new UnsupportedOperationException(method.getName());
            }
            return values.get(method.getName());
        });
    }
}
