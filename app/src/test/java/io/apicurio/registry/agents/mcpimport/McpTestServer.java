package io.apicurio.registry.agents.mcpimport;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * A minimal MCP server for tests: the Streamable HTTP transport answering each request with a
 * single JSON response, which is enough for the initialize handshake and tools/list.
 * <p>
 * Each test configures how it behaves through the public fields, which are read on every request.
 */
public final class McpTestServer implements AutoCloseable {

    /** The tools of each tools/list page, by cursor; the empty cursor is the first page. */
    public final Map<String, List<JsonObject>> pages = new ConcurrentHashMap<>();
    /** The cursor of the page after each page, by cursor. */
    public final Map<String, String> nextCursors = new ConcurrentHashMap<>();
    /** Headers of every request received. */
    public final List<Map<String, String>> receivedHeaders = new CopyOnWriteArrayList<>();
    /** Requests received, of any kind. */
    public final AtomicInteger requests = new AtomicInteger();

    /** When set, every request is answered with this redirect target instead. */
    public volatile String redirectTo;
    /** When set, each response is held back this long. */
    public volatile long delayMillis;
    /** When set, tools/list answers with a body padded to this many bytes. */
    public volatile int paddingBytes;
    /** When set, replaces the handling of tools/list entirely, given the cursor. */
    public volatile Function<String, JsonObject> listOverride;

    private final Vertx vertx = Vertx.vertx();
    private final HttpServer server;

    public McpTestServer() throws Exception {
        server = vertx.createHttpServer().requestHandler(request -> request.body().onSuccess(body -> {
            requests.incrementAndGet();
            Map<String, String> headers = new ConcurrentHashMap<>();
            request.headers().forEach(h -> headers.put(h.getKey().toLowerCase(Locale.ROOT), h.getValue()));
            receivedHeaders.add(headers);
            var response = request.response();
            if (redirectTo != null) {
                response.setStatusCode(307).putHeader("Location", redirectTo).end();
                return;
            }
            if (!"POST".equals(request.method().name())) {
                response.setStatusCode(405).end();
                return;
            }
            JsonObject message = body.toJsonObject();
            String method = message.getString("method");
            if (!message.containsKey("id")) {
                response.setStatusCode(202).end();
                return;
            }
            JsonObject result = switch (method) {
                case "initialize" -> new JsonObject()
                        .put("protocolVersion", message.getJsonObject("params").getString("protocolVersion"))
                        .put("capabilities", new JsonObject().put("tools", new JsonObject()))
                        .put("serverInfo", new JsonObject().put("name", "test-server").put("version", "1.0.0"));
                case "tools/list" -> list(message.getJsonObject("params"));
                default -> null;
            };
            JsonObject answer = new JsonObject().put("jsonrpc", "2.0").put("id", message.getValue("id"));
            if (result == null) {
                answer.put("error", new JsonObject().put("code", -32601).put("message", "not found"));
            } else {
                answer.put("result", result);
            }
            Runnable send = () -> response.putHeader("Content-Type", "application/json").end(answer.encode());
            if (delayMillis > 0) {
                vertx.setTimer(delayMillis, id -> send.run());
            } else {
                send.run();
            }
        })).listen(0, "127.0.0.1").toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    private JsonObject list(JsonObject params) {
        String cursor = params == null || params.getString("cursor") == null ? "" : params.getString("cursor");
        if (listOverride != null) {
            return listOverride.apply(cursor);
        }
        JsonObject result = new JsonObject().put("tools", new JsonArray(pages.getOrDefault(cursor, List.of())));
        if (nextCursors.containsKey(cursor)) {
            result.put("nextCursor", nextCursors.get(cursor));
        }
        if (paddingBytes > 0) {
            result.put("_meta", new JsonObject().put("padding", "x".repeat(paddingBytes)));
        }
        return result;
    }

    /** Lists exactly these tools, on one page. */
    public void tools(JsonObject... tools) {
        pages.clear();
        nextCursors.clear();
        pages.put("", List.of(tools));
    }

    public String url() {
        return "http://127.0.0.1:" + server.actualPort() + "/mcp";
    }

    public static JsonObject tool(String name, String queryType) {
        return new JsonObject().put("name", name).put("description", "The " + name + " tool")
                .put("inputSchema", new JsonObject().put("type", "object")
                        .put("properties", new JsonObject().put("query", new JsonObject().put("type", queryType)))
                        .put("required", new JsonArray().add("query")));
    }

    @Override
    public void close() {
        try {
            vertx.close().toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("The test MCP server did not stop", e);
        }
    }
}
