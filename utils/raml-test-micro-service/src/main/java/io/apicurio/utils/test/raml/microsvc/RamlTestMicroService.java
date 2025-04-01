package io.apicurio.utils.test.raml.microsvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.webhooks.beans.ContentAccepterRequest;
import io.apicurio.registry.types.webhooks.beans.ResolvedReference;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class RamlTestMicroService extends AbstractVerticle {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final int port;
    private HttpServer server;

    public RamlTestMicroService(int port) {
        this.port = port;
    }

    @Override
    public void start() {
        server = vertx.createHttpServer();

        server.requestHandler(req -> {
            if (!"POST".equals(req.method().name())) {
                req.response()
                        .setStatusCode(405)
                        .putHeader("content-type", "text/plain")
                        .end("Method Not Allowed");
                return;
            }

            if (!"application/json".equalsIgnoreCase(req.getHeader("content-type"))) {
                req.response()
                        .setStatusCode(400)
                        .putHeader("content-type", "text/plain")
                        .end("Bad Request: Content-Type must be application/json");
                return;
            }

            req.bodyHandler(body -> {
                handleRequest(req, body.toString());
            });
        });

        server.listen(port, result -> {
            if (result.succeeded()) {
                System.out.println("Server started on port " + port);
            } else {
                System.out.println("Failed to start server: " + result.cause());
            }
        });
    }

    public void stopServer() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (server != null) {
            server.close(ar -> {
                if (ar.succeeded()) {
                    future.complete(null);
                } else {
                    future.completeExceptionally(ar.cause());
                }
            });
        } else {
            future.complete(null);
        }
    }

    private void handleRequest(HttpServerRequest req, String body) {
        String path = req.path();
        try {
            switch (path) {
                case "/contentAccepter":
                    handleContentAccepter(req, body);
                    break;
                case "/compatibilityChecker":
                    handleCompatibilityChecker(req, body);
                    break;
                case "/contentCanonicalizer":
                    handleContentCanonicalizer(req, body);
                    break;
                case "/contentValidator":
                    handleContentValidator(req, body);
                    break;
                case "/contentExtractor":
                    handleContentExtractor(req, body);
                    break;
                case "/contentDereferencer":
                    handleContentDereferencer(req, body);
                    break;
                case "/referenceFinder":
                    handleReferenceFinder(req, body);
                    break;
                default:
                    req.response()
                            .setStatusCode(404)
                            .putHeader("content-type", "text/plain")
                            .end("Not Found");
            }
        } catch (Exception e) {
            req.response()
                    .setStatusCode(500)
                    .putHeader("content-type", "text/plain")
                    .end("Server error: " + e.getMessage()); // TODO include the stack trace in the response
        }
    }

    private void handleContentAccepter(HttpServerRequest req, String body) throws Exception {
        ContentAccepterRequest request = objectMapper.readValue(body, ContentAccepterRequest.class);
        RamlContentAccepter accepter = new RamlContentAccepter();
        boolean accepted = accepter.acceptsContent(toServerBean(request.getTypedContent()), toServerBean(request.getResolvedReferences()));
        req.response().putHeader("content-type", "application/json").end(String.valueOf(accepted));
    }

    private void handleCompatibilityChecker(HttpServerRequest req, String body) {
        req.response().putHeader("content-type", "application/json").end("{}");
    }

    private void handleContentCanonicalizer(HttpServerRequest req, String body) {
        req.response().putHeader("content-type", "application/json").end("{}");
    }

    private void handleContentValidator(HttpServerRequest req, String body) {
        req.response().putHeader("content-type", "application/json").end("{}");
    }

    private void handleContentExtractor(HttpServerRequest req, String body) {
        req.response().putHeader("content-type", "application/json").end("{}");
    }

    private void handleContentDereferencer(HttpServerRequest req, String body) {
        req.response().putHeader("content-type", "application/json").end("{}");
    }

    private void handleReferenceFinder(HttpServerRequest req, String body) {
        req.response().putHeader("content-type", "application/json").end("{}");
    }

    private TypedContent toServerBean(io.apicurio.registry.types.webhooks.beans.TypedContent typedContent) {
        return TypedContent.create(typedContent.getContent(), typedContent.getContentType());
    }

    private Map<String, TypedContent> toServerBean(List<ResolvedReference> resolvedReferences) {
        Map<String, TypedContent> rval = new HashMap<>();
        for (ResolvedReference ref : resolvedReferences) {
            rval.put(ref.getName(), TypedContent.create(ref.getContent(), ref.getContentType()));
        }
        return rval;
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 6060;
        Vertx vertx = Vertx.vertx();
        RamlTestMicroService verticle = new RamlTestMicroService(port);
        vertx.deployVerticle(verticle);
    }
}
