package io.apicurio.registry.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegistryServiceTest {

    private HttpServer server;
    private int port;
    private final Map<String, String> lastHeaders = new ConcurrentHashMap<>();
    private volatile String lastUri;

    @BeforeEach
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();

        server.createContext("/", exchange -> {
            lastUri = exchange.getRequestURI().toString();
            exchange.getRequestHeaders().forEach((k, v) -> {
                if (!v.isEmpty()) {
                    lastHeaders.put(k.toLowerCase(), v.get(0));
                }
            });

            String path = exchange.getRequestURI().getPath();

            // Mock OAuth2 token endpoint
            if (path.equals("/oauth/token")) {
                String response = "{\"access_token\":\"mock-token-123\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            // System info GET endpoint (called in init)
            if (path.endsWith("/system/info")) {
                String response = "{\"version\":\"3.0.0\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            if (path.endsWith("/well-known/agents") || path.contains("/well-known/agents?")) {
                String response = "{\"agents\":[{\"name\":\"test-agent\"}],\"count\":1}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            if (path.matches(".*/well-known/agents/g1/a1.*")) {
                String response = "{\"id\":\"a1\",\"name\":\"card-a1\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            if (path.endsWith("/well-known/mcp-tools") || path.contains("/well-known/mcp-tools?")) {
                String response = "{\"tools\":[{\"name\":\"test-tool\"}],\"count\":1}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            if (path.matches(".*/well-known/mcp-tools/g1/m1.*")) {
                String response = "{\"id\":\"m1\",\"name\":\"tool-m1\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            if (path.matches(".*/well-known/(agents|mcp-tools)/err/404.*")) {
                String response = "{\"error_code\":404,\"message\":\"Not Found\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            String response = "{}";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });

        server.start();
    }

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private RegistryService createService(String rawUrl, boolean authEnabled) {
        RegistryService service = new RegistryService();
        service.rawBaseUrl = rawUrl;
        Utils utils = new Utils();
        utils.mapper = new ObjectMapper();
        service.utils = utils;
        service.config = new McpConfig() {
            @Override
            public boolean safeMode() {
                return false;
            }

            @Override
            public Paging paging() {
                return new Paging() {
                    @Override
                    public int limit() {
                        return 200;
                    }

                    @Override
                    public boolean limitError() {
                        return false;
                    }
                };
            }

            @Override
            public Auth auth() {
                return new Auth() {
                    @Override
                    public boolean enabled() {
                        return authEnabled;
                    }

                    @Override
                    public Optional<String> tokenEndpoint() {
                        return Optional.of("http://localhost:" + port + "/oauth/token");
                    }

                    @Override
                    public Optional<String> clientId() {
                        return Optional.of("test-client");
                    }

                    @Override
                    public Optional<String> clientSecret() {
                        return Optional.of("test-secret");
                    }

                    @Override
                    public Optional<String> scope() {
                        return Optional.empty();
                    }
                };
            }

            @Override
            public Tls tls() {
                return new Tls() {
                    @Override
                    public boolean trustAll() {
                        return true;
                    }

                    @Override
                    public boolean verifyHost() {
                        return false;
                    }

                    @Override
                    public Truststore truststore() {
                        return new Truststore() {
                            @Override
                            public Optional<String> type() { return Optional.empty(); }
                            @Override
                            public Optional<String> path() { return Optional.empty(); }
                            @Override
                            public Optional<String> password() { return Optional.empty(); }
                        };
                    }

                    @Override
                    public Keystore keystore() {
                        return new Keystore() {
                            @Override
                            public Optional<String> type() { return Optional.empty(); }
                            @Override
                            public Optional<String> path() { return Optional.empty(); }
                            @Override
                            public Optional<String> password() { return Optional.empty(); }
                        };
                    }
                };
            }
        };

        service.init();
        return service;
    }

    @Test
    public void testOAuth2HeaderAttachment() throws Exception {
        RegistryService service = createService("http://localhost:" + port, true);
        service.searchAgentCards("abc", null, null);
        assertTrue(lastHeaders.containsKey("authorization"));
        assertEquals("Bearer mock-token-123", lastHeaders.get("authorization"));
    }

    @Test
    public void testQueryParameterPropagation() throws Exception {
        RegistryService service = createService("http://localhost:" + port, false);
        service.searchAgentCards("abc", "java", "streaming");
        assertTrue(lastUri.contains("limit=50"));
        assertTrue(lastUri.contains("name=abc"));
        assertTrue(lastUri.contains("skill=java"));
        assertTrue(lastUri.contains("capability=streaming"));

        service.searchMcpTools("mytool", "param1");
        assertTrue(lastUri.contains("limit=50"));
        assertTrue(lastUri.contains("name=mytool"));
        assertTrue(lastUri.contains("parameter=param1"));
    }

    @Test
    public void testIndividualArtifactRetrieval() throws Exception {
        RegistryService service = createService("http://localhost:" + port, false);
        String card = service.getAgentCard("g1", "a1");
        assertNotNull(card);
        assertTrue(card.contains("card-a1"));

        String tool = service.getMcpTool("g1", "m1");
        assertNotNull(tool);
        assertTrue(tool.contains("tool-m1"));
    }

    @Test
    public void testErrorHandlingOn404() {
        RegistryService service = createService("http://localhost:" + port, false);
        assertThrows(Exception.class, () -> service.getAgentCard("err", "404"));
        assertThrows(Exception.class, () -> service.getMcpTool("err", "404"));
    }
}
