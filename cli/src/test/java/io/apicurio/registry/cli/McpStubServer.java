package io.apicurio.registry.cli;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * A minimal MCP server for CLI tests: Streamable HTTP answering each request with one JSON
 * response, enough for the initialize handshake and a single tools/list page.
 */
public final class McpStubServer implements AutoCloseable {

    /** Headers of every request received, with lower-case names. */
    public final List<Map<String, String>> receivedHeaders = new CopyOnWriteArrayList<>();

    private volatile List<JsonObject> tools = List.of();

    private final Vertx vertx = Vertx.vertx();
    private final HttpServer server;

    public McpStubServer() throws Exception {
        server = vertx.createHttpServer().requestHandler(request -> request.body().onSuccess(body -> {
            Map<String, String> headers = new ConcurrentHashMap<>();
            request.headers().forEach(h -> headers.put(h.getKey().toLowerCase(Locale.ROOT), h.getValue()));
            receivedHeaders.add(headers);
            var response = request.response();
            JsonObject message = body.length() == 0 ? new JsonObject() : body.toJsonObject();
            if (!message.containsKey("id")) {
                response.setStatusCode(202).end();
                return;
            }
            JsonObject result = switch (message.getString("method", "")) {
                case "initialize" -> new JsonObject()
                        .put("protocolVersion", message.getJsonObject("params").getString("protocolVersion"))
                        .put("capabilities", new JsonObject().put("tools", new JsonObject()))
                        .put("serverInfo", new JsonObject().put("name", "stub").put("version", "1.0.0"));
                case "tools/list" -> new JsonObject().put("tools", new JsonArray(tools));
                default -> null;
            };
            JsonObject answer = new JsonObject().put("jsonrpc", "2.0").put("id", message.getValue("id"));
            if (result == null) {
                answer.put("error", new JsonObject().put("code", -32601).put("message", "not found"));
            } else {
                answer.put("result", result);
            }
            response.putHeader("Content-Type", "application/json").end(answer.encode());
        })).listen(0, "0.0.0.0").toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    public int port() {
        return server.actualPort();
    }

    /** Lists exactly these tools. */
    public void tools(JsonObject... listed) {
        tools = List.of(listed);
    }

    public static JsonObject tool(String name, String queryType) {
        return new JsonObject().put("name", name).put("description", "The " + name + " tool")
                .put("inputSchema", new JsonObject().put("type", "object")
                        .put("properties", new JsonObject().put("query", new JsonObject().put("type", queryType)))
                        .put("required", new JsonArray().add("query")));
    }

    @Override
    public void close() throws Exception {
        vertx.close().toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
