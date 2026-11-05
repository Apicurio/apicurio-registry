package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.agents.mcpimport.McpImportException.Reason;
import io.apicurio.registry.agents.mcpimport.McpToolsClient.ServerTools;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolsClientTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final McpToolsClient client = new McpToolsClient();
    private McpTestServer server;

    @BeforeEach
    void start() throws Exception {
        server = new McpTestServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private ServerTools list(Map<String, String> headers, int maxMessage, int maxTools) {
        return client.listTools(URI.create(server.url()), headers, TIMEOUT, maxMessage, maxTools);
    }

    private McpImportException failure(int maxMessage, int maxTools) {
        return assertThrows(McpImportException.class, () -> list(Map.of(), maxMessage, maxTools));
    }

    @Test
    void listsEveryPageAndSendsHeadersOnEveryRequest() {
        server.pages.put("", List.of(McpTestServer.tool("a", "string")));
        server.nextCursors.put("", "p2");
        server.pages.put("p2", List.of(McpTestServer.tool("b", "string"), McpTestServer.tool("c", "integer")));

        ServerTools tools = list(Map.of("Authorization", "Bearer secret-token"), 65536, 10);

        assertEquals("test-server", tools.serverInfo().name());
        assertEquals(List.of("a", "b", "c"), tools.tools().stream().map(t -> t.name()).toList());
        // initialize, notifications/initialized, two tools/list pages; each carried the header.
        assertTrue(server.receivedHeaders.size() >= 4, () -> "requests: " + server.receivedHeaders.size());
        assertTrue(server.receivedHeaders.stream()
                .allMatch(h -> "Bearer secret-token".equals(h.get("authorization"))));
    }

    @Test
    void keepsNumbersAsSent() {
        server.tools(new JsonObject().put("name", "precise").put("inputSchema", new JsonObject()
                .put("type", "object").put("properties", new JsonObject().put("x", new JsonObject()
                        .put("type", "number").put("maximum", new BigDecimal("9007199254740993.5"))))));

        ServerTools tools = list(Map.of(), 65536, 10);

        @SuppressWarnings("unchecked")
        Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) tools.tools().get(0).inputSchema()
                .get("properties")).get("x");
        assertEquals(new BigDecimal("9007199254740993.5"), x.get("maximum"));
    }

    @Test
    void doesNotFollowRedirects() throws Exception {
        try (McpTestServer target = new McpTestServer()) {
            target.tools(McpTestServer.tool("hidden", "string"));
            server.redirectTo = target.url();

            McpImportException e = failure(65536, 10);

            assertEquals(Reason.PROTOCOL, e.getReason());
            assertEquals(0, target.requests.get(), "the redirect target must never be contacted");
        }
    }

    @Test
    void slowServerTimesOut() {
        server.tools(McpTestServer.tool("a", "string"));
        server.delayMillis = 4000;

        McpImportException e = failure(65536, 10);

        assertEquals(Reason.TIMEOUT, e.getReason());
        assertEquals("The MCP server did not respond in time.", e.getMessage());
    }

    @Test
    void oversizeMessageIsRefusedByTheSdkBound() {
        server.tools(McpTestServer.tool("a", "string"));
        server.paddingBytes = 20_000;

        McpImportException e = failure(10_000, 10);

        // Pins how the SDK reports a size violation; see McpToolsClient.SDK_MAX_SIZE_PHRASE.
        assertEquals(Reason.OVERSIZE, e.getReason());
    }

    @Test
    void tooManyToolsIsRefused() {
        server.tools(McpTestServer.tool("a", "string"), McpTestServer.tool("b", "string"),
                McpTestServer.tool("c", "string"));

        McpImportException e = failure(65536, 2);

        assertEquals(Reason.TOO_MANY_TOOLS, e.getReason());
        assertTrue(e.getMessage().contains("(2)"), e.getMessage());
    }

    @Test
    void repeatedCursorIsRefusedInsteadOfPagingForever() {
        server.pages.put("", List.of(McpTestServer.tool("a", "string")));
        server.nextCursors.put("", "again");
        server.pages.put("again", List.of(McpTestServer.tool("b", "string")));
        server.nextCursors.put("again", "again");

        McpImportException e = failure(65536, 100);

        assertEquals(Reason.PROTOCOL, e.getReason());
        assertEquals("The MCP server's tool list does not end.", e.getMessage());
    }

    @Test
    void unreachableServerIsAProtocolFailureWithoutDetails() {
        server.close();

        McpImportException e = failure(65536, 10);

        assertEquals(Reason.PROTOCOL, e.getReason());
        assertFalse(e.getMessage().contains("127.0.0.1"), e.getMessage());
        assertNull(e.getCause(), "the cause would reach the client through the REST error mapper");
    }
}
