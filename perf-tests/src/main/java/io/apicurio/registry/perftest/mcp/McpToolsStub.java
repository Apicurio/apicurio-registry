package io.apicurio.registry.perftest.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * A minimal MCP server over Streamable HTTP (JSON responses only) that lists a fixed set of tools,
 * for measuring the registry's import from a live MCP server. It answers {@code initialize},
 * accepts notifications, and returns every tool on one {@code tools/list} page.
 */
public final class McpToolsStub implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final byte[] toolList;

    public McpToolsStub(int port, int tools) throws IOException {
        ArrayNode list = MAPPER.createArrayNode();
        for (int i = 0; i < tools; i++) {
            ObjectNode properties = MAPPER.createObjectNode();
            properties.putObject("query").put("type", "string").put("maxLength", 256);
            properties.putObject("limit").put("type", "integer").put("minimum", 1).put("maximum", 100);
            ObjectNode tool = list.addObject().put("name", "perf_tool_" + i)
                    .put("description", "Performance test tool " + i);
            ObjectNode input = tool.putObject("inputSchema").put("type", "object");
            input.set("properties", properties);
            input.putArray("required").add("query");
        }
        ObjectNode result = MAPPER.createObjectNode();
        result.set("tools", list);
        toolList = MAPPER.writeValueAsBytes(result);

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/mcp", this::handle);
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            JsonNode message = MAPPER.readTree(exchange.getRequestBody());
            if (!message.has("id")) {
                exchange.sendResponseHeaders(202, -1);
                return;
            }
            String method = message.path("method").asText();
            byte[] resultJson = switch (method) {
                case "initialize" -> MAPPER.writeValueAsBytes(MAPPER.createObjectNode()
                        .put("protocolVersion", message.path("params").path("protocolVersion").asText())
                        .<ObjectNode>set("capabilities", MAPPER.createObjectNode()
                                .set("tools", MAPPER.createObjectNode()))
                        .set("serverInfo", MAPPER.createObjectNode().put("name", "perf-mcp")
                                .put("version", "1.0.0")));
                case "tools/list" -> toolList;
                default -> null;
            };
            String id = MAPPER.writeValueAsString(message.get("id"));
            String answer = resultJson == null
                    ? "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{\"code\":-32601,\"message\":\"not found\"}}"
                    : "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":"
                            + new String(resultJson, StandardCharsets.UTF_8) + "}";
            byte[] body = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
