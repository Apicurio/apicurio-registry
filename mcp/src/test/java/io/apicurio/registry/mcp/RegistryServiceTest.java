package io.apicurio.registry.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.kiota.serialization.ValuedEnum;
import com.sun.net.httpserver.HttpServer;
import io.apicurio.registry.rest.client.models.ArtifactSortBy;
import io.apicurio.registry.rest.client.models.GroupSortBy;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.rest.client.models.VersionSortBy;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegistryServiceTest {

    private static final int DEFAULT_PAGING_LIMIT = 200;

    private HttpServer server;
    private int port;
    private final Map<String, String> lastHeaders = new ConcurrentHashMap<>();
    private volatile String lastUri;
    private volatile String lastRequestBody;

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
            String query = exchange.getRequestURI().getQuery();

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

            // System info GET endpoint (called in resolver init)
            if (path.endsWith("/system/info")) {
                String response = "{\"version\":\"3.0.0\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            // "overflow" search name is a signal to the mock server to return count > limit (count=3)
            if (path.endsWith("/well-known/agents") || path.contains("/well-known/agents?")) {
                int count = (query != null && query.contains("name=overflow")) ? 3 : 1;
                String response = "{\"agents\":[{\"name\":\"test-agent\"}],\"count\":" + count + "}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            if (path.contains("/well-known/agents/g1/a1")) {
                String response = "{\"id\":\"a1\",\"name\":\"card-a1\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            if (path.endsWith("/well-known/mcp-tools") || path.contains("/well-known/mcp-tools?")) {
                int count = (query != null && query.contains("name=overflow")) ? 3 : 1;
                String response = "{\"tools\":[{\"name\":\"test-tool\"}],\"count\":" + count + "}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            if (path.contains("/well-known/mcp-tools/g1/m1")) {
                String response = "{\"id\":\"m1\",\"name\":\"tool-m1\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            if (path.endsWith("/well-known/ard/search") && "POST".equals(exchange.getRequestMethod())) {
                byte[] body = exchange.getRequestBody().readAllBytes();
                lastRequestBody = new String(body, StandardCharsets.UTF_8);
                boolean disabled = lastRequestBody.contains("ard-disabled");
                if (disabled) {
                    String response = "{\"error_code\":404,\"message\":\"ARD support is disabled\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(404, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes(StandardCharsets.UTF_8));
                    }
                    return;
                }
                String response = "{\"results\":[{\"identifier\":\"air://example.com/system/entry-1\","
                        + "\"displayName\":\"Test Agent\",\"type\":\"application/agent-card+json\","
                        + "\"url\":\"http://example.com/agent\"}],\"pageToken\":null}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            if (path.contains("/well-known/agents/err/404") || path.contains("/well-known/mcp-tools/err/404")) {
                String response = "{\"error_code\":404,\"message\":\"Not Found\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                if (path.contains("/state") && exchange.getRequestMethod().equals("PUT")) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            if (path.endsWith("/groups")) {
                String response = "{\"groups\":[],\"count\":0}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            if (path.endsWith("/artifacts")) {
                String response = "{\"artifacts\":[],\"count\":0}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            if (path.endsWith("/versions")) {
                String response = "{\"versions\":[],\"count\":0}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
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
        return createService(rawUrl, authEnabled, DEFAULT_PAGING_LIMIT, true);
    }

    private RegistryService createService(String rawUrl, boolean authEnabled, int pagingLimit, boolean limitError) {
        McpConfig config = new McpConfig() {
            @Override
            public boolean safeMode() {
                return false;
            }

            @Override
            public Paging paging() {
                return new Paging() {
                    @Override
                    public int limit() {
                        return pagingLimit;
                    }

                    @Override
                    public boolean limitError() {
                        return limitError;
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
            public Http http() {
                return new Http() {
                    @Override
                    public boolean enabled() {
                        return false;
                    }

                    @Override
                    public boolean forwardToken() {
                        return true;
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

        RegistryClientResolver resolver = new RegistryClientResolver();
        resolver.rawBaseUrl = rawUrl;
        resolver.config = config;
        resolver.securityIdentity = new UnresolvableInstance<>();
        resolver.jwt = new UnresolvableInstance<>();
        resolver.init();

        RegistryService service = new RegistryService();
        Utils utils = new Utils();
        utils.mapper = new ObjectMapper();
        service.utils = utils;
        service.config = config;
        service.clientResolver = resolver;
        return service;
    }

    private static final class UnresolvableInstance<T> implements Instance<T> {
        @Override
        public Instance<T> select(Annotation... qualifiers) {
            return this;
        }

        @Override
        public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isUnsatisfied() {
            return true;
        }

        @Override
        public boolean isAmbiguous() {
            return false;
        }

        @Override
        public Handle<T> getHandle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<? extends Handle<T>> handles() {
            return Collections.emptyList();
        }

        @Override
        public T get() {
            return null;
        }

        @Override
        public Iterator<T> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public void destroy(T instance) {
            // no-op
        }
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
        int expectedLimit = service.config.paging().limit() + 1;
        service.searchAgentCards("abc", "java", "streaming");
        assertTrue(lastUri.contains("limit=" + expectedLimit));
        assertTrue(lastUri.contains("name=abc"));
        assertTrue(lastUri.contains("skill=java"));
        assertTrue(lastUri.contains("capability=streaming"));

        service.searchMcpTools("mytool", "param1");
        assertTrue(lastUri.contains("limit=" + expectedLimit));
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

    @Test
    public void testSearchAgentCardsThrowsWhenPagingLimitExceeded() {
        RegistryService service = createService("http://localhost:" + port, false, 2, true);
        assertThrows(ToolCallException.class, () -> service.searchAgentCards("overflow", null, null));
    }

    @Test
    public void testSearchAgentCardsDoesNotThrowWhenCountWithinLimit() throws Exception {
        RegistryService service = createService("http://localhost:" + port, false, 2, true);
        String result = service.searchAgentCards("normal", null, null);
        assertNotNull(result);
    }

    @Test
    public void testSearchMcpToolsThrowsWhenPagingLimitExceeded() {
        RegistryService service = createService("http://localhost:" + port, false, 2, true);
        assertThrows(ToolCallException.class, () -> service.searchMcpTools("overflow", null));
    }

    @Test
    public void testSearchMcpToolsDoesNotThrowWhenCountWithinLimit() throws Exception {
        RegistryService service = createService("http://localhost:" + port, false, 2, true);
        String result = service.searchMcpTools("normal", null);
        assertNotNull(result);
    }
      
    @Test
    public void testUpdateVersionStateValidation() throws Exception {
        RegistryService service = createService("http://localhost:" + port, false);

        // Valid lowercase and mixed-case inputs resolve cleanly to VersionState
        service.updateVersionState("g1", "a1", "1", "enabled");
        service.updateVersionState("g1", "a1", "1", "Enabled");
        service.updateVersionState("g1", "a1", "1", "DEPRECATED");

        // Invalid or null state strings throw ToolCallException (not raw IllegalArgumentException / NPE)
        ToolCallException exInvalid = assertThrows(ToolCallException.class,
                () -> service.updateVersionState("g1", "a1", "1", "invalid_state"));
        assertTrue(exInvalid.getMessage().contains("Invalid version state: 'invalid_state'"));
        assertTrue(exInvalid.getMessage().contains("Accepted values (case-insensitive):"));

        ToolCallException exNull = assertThrows(ToolCallException.class,
                () -> service.updateVersionState("g1", "a1", "1", null));
        assertTrue(exNull.getMessage().contains("Invalid version state: 'null'"));

        ToolCallException exEmpty = assertThrows(ToolCallException.class,
                () -> service.updateVersionState("g1", "a1", "1", "  "));
        assertTrue(exEmpty.getMessage().contains("Invalid version state: '  '"));
    }

    @Test
    public void testArdSearchSendsQueryTextAndFilters() throws Exception {
        RegistryService service = createService("http://localhost:" + port, false);
        String result = service.ardSearch("discover agents", "application/agent-card+json",
                "streaming,production", "java", "example.com", null, 5);

        assertNotNull(result);
        assertTrue(result.contains("Test Agent"));
        assertTrue(result.contains("air://example.com/system/entry-1"));

        assertNotNull(lastRequestBody);
        assertTrue(lastRequestBody.contains("\"text\":\"discover agents\""));
        assertTrue(lastRequestBody.contains("\"type\""));
        assertTrue(lastRequestBody.contains("application/agent-card+json"));
        assertTrue(lastRequestBody.contains("\"tags\""));
        assertTrue(lastRequestBody.contains("streaming"));
        assertTrue(lastRequestBody.contains("production"));
        assertTrue(lastRequestBody.contains("\"capabilities\""));
        assertTrue(lastRequestBody.contains("java"));
        assertTrue(lastRequestBody.contains("\"publisher\""));
        assertTrue(lastRequestBody.contains("example.com"));
        assertTrue(lastRequestBody.contains("\"pageSize\":5"));
    }

    @Test
    public void testArdSearchOmitsFilterWhenNoStructuredFiltersProvided() throws Exception {
        RegistryService service = createService("http://localhost:" + port, false);
        String result = service.ardSearch("discover agents", null, null, null, null, null, null);

        assertNotNull(result);
        assertNotNull(lastRequestBody);
        assertTrue(lastRequestBody.contains("\"text\":\"discover agents\""));
        assertTrue(!lastRequestBody.contains("\"filter\""));
    }

    @Test
    public void testArdSearchThrowsWhenArdDisabled() {
        RegistryService service = createService("http://localhost:" + port, false);
        assertThrows(Exception.class,
                () -> service.ardSearch("ard-disabled", null, null, null, null, null, null));
    }

    @Test
    public void testSortEnumValidation() {
        RegistryService service = createService("http://localhost:" + port, false);

        // SortOrder — check all constants (wire value, uppercase, whitespace-padded)
        for (SortOrder constant : SortOrder.values()) {
            assertEquals(constant, service.parseSortOrder(constant.getValue()));
            assertEquals(constant, service.parseSortOrder(constant.getValue().toUpperCase()));
            assertEquals(constant, service.parseSortOrder("  " + constant.getValue() + "  "));
        }
        assertNull(service.parseSortOrder(null));
        assertNull(service.parseSortOrder(""));
        assertNull(service.parseSortOrder("   "));

        ToolCallException exOrder = assertThrows(ToolCallException.class,
                () -> service.parseSortOrder("INVALID"));
        assertTrue(exOrder.getMessage().contains("Invalid sort order: 'INVALID'"));
        assertTrue(exOrder.getMessage().contains("Accepted values (case-insensitive): "
                + Arrays.stream(SortOrder.values()).map(ValuedEnum::getValue).toList()));

        // GroupSortBy — check all constants
        for (GroupSortBy constant : GroupSortBy.values()) {
            assertEquals(constant, service.parseGroupSortBy(constant.getValue()));
            assertEquals(constant, service.parseGroupSortBy(constant.getValue().toUpperCase()));
            assertEquals(constant, service.parseGroupSortBy("  " + constant.getValue() + "  "));
        }
        assertNull(service.parseGroupSortBy(null));
        assertNull(service.parseGroupSortBy(""));
        assertNull(service.parseGroupSortBy("   "));

        ToolCallException exGroup = assertThrows(ToolCallException.class,
                () -> service.parseGroupSortBy("badvalue"));
        assertTrue(exGroup.getMessage().contains("Invalid group order-by: 'badvalue'"));
        assertTrue(exGroup.getMessage().contains("Accepted values (case-insensitive): "
                + Arrays.stream(GroupSortBy.values()).map(ValuedEnum::getValue).toList()));

        // ArtifactSortBy — check all constants
        for (ArtifactSortBy constant : ArtifactSortBy.values()) {
            assertEquals(constant, service.parseArtifactSortBy(constant.getValue()));
            assertEquals(constant, service.parseArtifactSortBy(constant.getValue().toUpperCase()));
            assertEquals(constant, service.parseArtifactSortBy("  " + constant.getValue() + "  "));
        }
        assertNull(service.parseArtifactSortBy(null));
        assertNull(service.parseArtifactSortBy(""));
        assertNull(service.parseArtifactSortBy("   "));

        ToolCallException exArtifact = assertThrows(ToolCallException.class,
                () -> service.parseArtifactSortBy("xyz"));
        assertTrue(exArtifact.getMessage().contains("Invalid artifact order-by: 'xyz'"));
        assertTrue(exArtifact.getMessage().contains("Accepted values (case-insensitive): "
                + Arrays.stream(ArtifactSortBy.values()).map(ValuedEnum::getValue).toList()));

        // VersionSortBy — check all constants
        for (VersionSortBy constant : VersionSortBy.values()) {
            assertEquals(constant, service.parseVersionSortBy(constant.getValue()));
            assertEquals(constant, service.parseVersionSortBy(constant.getValue().toUpperCase()));
            assertEquals(constant, service.parseVersionSortBy("  " + constant.getValue() + "  "));
        }
        assertNull(service.parseVersionSortBy(null));
        assertNull(service.parseVersionSortBy(""));
        assertNull(service.parseVersionSortBy("   "));

        ToolCallException exVersion = assertThrows(ToolCallException.class,
                () -> service.parseVersionSortBy("unknown"));
        assertTrue(exVersion.getMessage().contains("Invalid version order-by: 'unknown'"));
        assertTrue(exVersion.getMessage().contains("Accepted values (case-insensitive): "
                + Arrays.stream(VersionSortBy.values()).map(ValuedEnum::getValue).toList()));
    }

    @Test
    public void testSortEnumEndpointWiring() {
        RegistryService service = createService("http://localhost:" + port, false);

        // 1. listGroups with mis-cased sort parameters
        service.listGroups("DESC", "GROUPID");
        assertNotNull(lastUri);
        assertTrue(lastUri.contains("order=desc"), "listGroups should forward order=desc, got: " + lastUri);
        assertTrue(lastUri.contains("orderby=groupId"), "listGroups should forward orderby=groupId, got: " + lastUri);

        // 2. listArtifacts with mis-cased sort parameters
        service.listArtifacts("g1", "ASC", "ARTIFACTID");
        assertNotNull(lastUri);
        assertTrue(lastUri.contains("order=asc"), "listArtifacts should forward order=asc, got: " + lastUri);
        assertTrue(lastUri.contains("orderby=artifactId"), "listArtifacts should forward orderby=artifactId, got: " + lastUri);

        // 3. listVersions with mis-cased sort parameters
        service.listVersions("g1", "a1", "DESC", "GLOBALID");
        assertNotNull(lastUri);
        assertTrue(lastUri.contains("order=desc"), "listVersions should forward order=desc, got: " + lastUri);
        assertTrue(lastUri.contains("orderby=globalId"), "listVersions should forward orderby=globalId, got: " + lastUri);

        // 4. searchGroups with mis-cased sort parameters
        service.searchGroups(null, null, null, "DESC", "GROUPID");
        assertNotNull(lastUri);
        assertTrue(lastUri.contains("order=desc"), "searchGroups should forward order=desc, got: " + lastUri);
        assertTrue(lastUri.contains("orderby=groupId"), "searchGroups should forward orderby=groupId, got: " + lastUri);

        // 5. searchArtifacts with mis-cased sort parameters
        service.searchArtifacts(null, null, null, null, null, null, "ASC", "NAME");
        assertNotNull(lastUri);
        assertTrue(lastUri.contains("order=asc"), "searchArtifacts should forward order=asc, got: " + lastUri);
        assertTrue(lastUri.contains("orderby=name"), "searchArtifacts should forward orderby=name, got: " + lastUri);

        // 6. searchVersions with mis-cased sort parameters
        service.searchVersions(null, null, null, null, null, null, "DESC", "VERSION");
        assertNotNull(lastUri);
        assertTrue(lastUri.contains("order=desc"), "searchVersions should forward order=desc, got: " + lastUri);
        assertTrue(lastUri.contains("orderby=version"), "searchVersions should forward orderby=version, got: " + lastUri);

        // Endpoints reject invalid sort / orderBy parameters with ToolCallException
        assertThrows(ToolCallException.class, () -> service.listGroups("INVALID", "groupId"));
        assertThrows(ToolCallException.class, () -> service.listGroups("asc", "INVALID"));
        assertThrows(ToolCallException.class, () -> service.listArtifacts("g1", "INVALID", "artifactId"));
        assertThrows(ToolCallException.class, () -> service.listArtifacts("g1", "asc", "INVALID"));
        assertThrows(ToolCallException.class, () -> service.listVersions("g1", "a1", "INVALID", "globalId"));
        assertThrows(ToolCallException.class, () -> service.listVersions("g1", "a1", "desc", "INVALID"));
        assertThrows(ToolCallException.class, () -> service.searchGroups(null, null, null, "INVALID", "groupId"));
        assertThrows(ToolCallException.class, () -> service.searchGroups(null, null, null, "desc", "INVALID"));
        assertThrows(ToolCallException.class, () -> service.searchArtifacts(null, null, null, null, null, null, "INVALID", "name"));
        assertThrows(ToolCallException.class, () -> service.searchArtifacts(null, null, null, null, null, null, "asc", "INVALID"));
        assertThrows(ToolCallException.class, () -> service.searchVersions(null, null, null, null, null, null, "INVALID", "version"));
        assertThrows(ToolCallException.class, () -> service.searchVersions(null, null, null, null, null, null, "desc", "INVALID"));
    }
}
