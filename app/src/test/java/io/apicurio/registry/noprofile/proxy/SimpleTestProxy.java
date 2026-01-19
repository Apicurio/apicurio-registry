package io.apicurio.registry.noprofile.proxy;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static io.apicurio.registry.utils.ConcurrentUtil.blockOn;
import static io.apicurio.registry.utils.ConcurrentUtil.toJavaFuture;

/**
 * Simple HTTP proxy server for testing proxy functionality.
 * This proxy forwards all requests to a destination server and tracks request counts.
 */
public class SimpleTestProxy {

    private static final Logger logger = LoggerFactory.getLogger(SimpleTestProxy.class);

    private final Vertx vertx;
    private final int port;
    private final String destinationHost;
    private final int destinationPort;
    private final AtomicInteger requestCount = new AtomicInteger(0);

    private HttpServer server;
    private HttpClient client;

    public SimpleTestProxy(int port, String destinationHost, int destinationPort) {
        this.vertx = Vertx.vertx();
        this.port = port;
        this.destinationHost = destinationHost;
        this.destinationPort = destinationPort;
        this.client = vertx.createHttpClient(new HttpClientOptions());
    }

    public CompletableFuture<Void> start() {
        server = vertx.createHttpServer(new HttpServerOptions().setPort(port))
                .requestHandler(this::proxyRequest);

        return toJavaFuture(server.listen())
                .whenComplete((r, t) -> {
                    if (t == null) {
                        logger.info("Test proxy server started on port {}", port);
                        logger.info("Proxying to {}:{}", destinationHost, destinationPort);
                    } else {
                        logger.error("Error starting proxy server", t);
                    }
                }).thenApply(_ignored -> null);
    }

    public void stop() {
        if (server != null) {
            blockOn(toJavaFuture(server.close()));
        }
        if (client != null) {
            blockOn(toJavaFuture(client.close()));
        }
        blockOn(toJavaFuture(vertx.close()));
    }

    public int getRequestCount() {
        return requestCount.get();
    }

    public void resetRequestCount() {
        requestCount.set(0);
    }

    private void proxyRequest(HttpServerRequest req) {
        requestCount.incrementAndGet();
        logger.info("Proxying request: {} {}", req.method(), req.uri());

        req.pause();

        // Extract path from URI - the URI might be absolute (http://host:port/path) or relative (/path)
        String requestUri = req.uri();
        String path = requestUri;

        // If URI is absolute (starts with http:// or https://), extract just the path
        if (requestUri.startsWith("http://") || requestUri.startsWith("https://")) {
            try {
                java.net.URI uri = java.net.URI.create(requestUri);
                path = uri.getRawPath();
                if (uri.getRawQuery() != null) {
                    path += "?" + uri.getRawQuery();
                }
            } catch (Exception e) {
                logger.error("Error parsing URI: {}", requestUri, e);
            }
        }

        logger.info("Forwarding to {}:{}{}", destinationHost, destinationPort, path);

        client.request(req.method(), destinationPort, destinationHost, path)
                .onSuccess(clientReq -> executeProxy(clientReq, req))
                .onFailure(throwable -> {
                    logger.error("Error creating proxy request", throwable);
                    req.response().setStatusCode(502).end();
                });
    }

    private void executeProxy(HttpClientRequest clientReq, HttpServerRequest req) {
        clientReq.response(reqResult -> {
            if (reqResult.succeeded()) {
                HttpClientResponse clientRes = reqResult.result();
                req.response().setChunked(true);
                req.response().setStatusCode(clientRes.statusCode());
                req.response().headers().setAll(clientRes.headers());
                clientRes.handler(data -> req.response().write(data));
                clientRes.endHandler((v) -> req.response().end());
                clientRes.exceptionHandler(e -> logger.error("Error in client response", e));
                req.response().exceptionHandler(e -> logger.error("Error in server response", e));
            } else {
                logger.error("Error in proxy response", reqResult.cause());
                req.response().setStatusCode(502).end();
            }
        });

        clientReq.setChunked(true);
        clientReq.headers().setAll(req.headers());

        if (req.isEnded()) {
            clientReq.end();
        } else {
            req.handler(data -> clientReq.write(data));
            req.endHandler((v) -> clientReq.end());
            clientReq.exceptionHandler(e -> {
                logger.error("Error in proxy request", e);
                req.response().setStatusCode(502).end();
            });
        }

        req.resume();
        req.exceptionHandler(e -> logger.error("Error in server request", e));
    }
}
