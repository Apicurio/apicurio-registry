package io.apicurio.registry.federation;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.SelfSignedCertificate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * A peer registry that answers however a test wants, on a free local port, and remembers the
 * requests it received.
 */
final class StubPeer implements AutoCloseable {

    /** What the peer saw of one request. */
    record Seen(String path, String query, io.vertx.core.MultiMap headers) {
        String header(String name) {
            return headers.get(name);
        }
    }

    private final HttpServer server;
    private final List<Seen> requests = new CopyOnWriteArrayList<>();

    private StubPeer(HttpServer server) {
        this.server = server;
    }

    /** Starts a plain HTTP peer. */
    static StubPeer start(Vertx vertx, BiConsumer<HttpServerRequest, HttpServerResponse> handler)
            throws Exception {
        return start(vertx, new HttpServerOptions(), handler);
    }

    /** Starts an HTTPS peer whose certificate is the given self-signed one. */
    static StubPeer startTls(Vertx vertx, SelfSignedCertificate certificate,
            BiConsumer<HttpServerRequest, HttpServerResponse> handler) throws Exception {
        return start(vertx, new HttpServerOptions().setSsl(true).setKeyCertOptions(certificate.keyCertOptions()),
                handler);
    }

    private static StubPeer start(Vertx vertx, HttpServerOptions options,
            BiConsumer<HttpServerRequest, HttpServerResponse> handler) throws Exception {
        StubPeer[] holder = new StubPeer[1];
        HttpServer server = vertx.createHttpServer(options.setHost("127.0.0.1").setPort(0))
                .requestHandler(request -> {
                    holder[0].requests.add(new Seen(request.path(), request.query(), request.headers()));
                    handler.accept(request, request.response());
                }).listen().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        holder[0] = new StubPeer(server);
        return holder[0];
    }

    int port() {
        return server.actualPort();
    }

    String url() {
        return "http://127.0.0.1:" + port();
    }

    List<Seen> requests() {
        return requests;
    }

    @Override
    public void close() throws Exception {
        server.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    /** A JSON answer that confirms the public-only mode. */
    static void publicAgents(HttpServerResponse response, int count, String... agentNames) {
        StringBuilder agents = new StringBuilder();
        for (int i = 0; i < agentNames.length; i++) {
            if (i > 0) {
                agents.append(',');
            }
            agents.append("{\"groupId\":\"remote\",\"artifactId\":\"").append(agentNames[i])
                    .append("\",\"name\":\"").append(agentNames[i]).append("\",\"skills\":[\"remote-skill\"]}");
        }
        json(response, "{\"count\":" + count + ",\"publicOnly\":true,\"agents\":[" + agents + "]}");
    }

    static void json(HttpServerResponse response, String body) {
        response.putHeader("Content-Type", "application/json").end(body);
    }
}
