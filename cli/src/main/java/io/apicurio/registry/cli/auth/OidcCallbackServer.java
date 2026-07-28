package io.apicurio.registry.cli.auth;

import io.apicurio.registry.cli.common.CliException;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;

/**
 * Temporary local HTTP server that receives the OIDC authorization code callback.
 */
@ApplicationScoped
public class OidcCallbackServer {

    private static final String CALLBACK_PATH = "/callback";
    private static final int LOGIN_TIMEOUT_SECONDS = 120;

    private static final String SUCCESS_HTML = """
            <html><body style="font-family:sans-serif;text-align:center;padding:40px">
            <h2>Login successful</h2><p>You may close this browser tab.</p>
            </body></html>""";

    private static final String ERROR_HTML = """
            <html><body style="font-family:sans-serif;text-align:center;padding:40px">
            <h2>Login failed</h2><p>%s</p>
            </body></html>""";

    @Inject
    Vertx vertx;

    /**
     * Starts the callback server and returns a session that can be used to
     * build the redirect URI and await the authorization code.
     */
    public Session start(final String expectedState) {
        final CompletableFuture<String> codeFuture = new CompletableFuture<>();
        final HttpServer server = vertx.createHttpServer();

        server.requestHandler(request -> {
            if (!request.path().equals(CALLBACK_PATH)) {
                request.response().setStatusCode(404).end("Not found");
                return;
            }

            final String error = request.getParam("error");
            if (error != null) {
                final String description = request.getParam("error_description");
                final String message = description != null ? description : error;
                request.response().putHeader("Content-Type", "text/html")
                        .end(ERROR_HTML.formatted(message));
                codeFuture.completeExceptionally(new CliException(
                        "OIDC login failed: " + message, APPLICATION_ERROR_RETURN_CODE));
                return;
            }

            final String code = request.getParam("code");
            final String state = request.getParam("state");

            if (code == null) {
                request.response().putHeader("Content-Type", "text/html")
                        .end(ERROR_HTML.formatted("Missing authorization code."));
                codeFuture.completeExceptionally(new CliException(
                        "Missing authorization code in callback.", APPLICATION_ERROR_RETURN_CODE));
                return;
            }

            if (!expectedState.equals(state)) {
                request.response().putHeader("Content-Type", "text/html")
                        .end(ERROR_HTML.formatted("State parameter mismatch."));
                codeFuture.completeExceptionally(new CliException(
                        "Security error: state parameter mismatch. Please try again.",
                        APPLICATION_ERROR_RETURN_CODE));
                return;
            }

            request.response().putHeader("Content-Type", "text/html").end(SUCCESS_HTML);
            codeFuture.complete(code);
        });

        try {
            final CompletableFuture<Integer> portFuture = new CompletableFuture<>();
            server.listen(0, "127.0.0.1", ar -> {
                if (ar.succeeded()) {
                    portFuture.complete(ar.result().actualPort());
                } else {
                    portFuture.completeExceptionally(ar.cause());
                }
            });
            final int port = portFuture.get(5, TimeUnit.SECONDS);
            return new Session(port, codeFuture, server);
        } catch (Exception ex) {
            server.close();
            throw new CliException("Failed to start callback server: " + ex.getMessage(),
                    APPLICATION_ERROR_RETURN_CODE);
        }
    }

    public static String redirectUri(final int port) {
        return "http://127.0.0.1:" + port + CALLBACK_PATH;
    }

    static class Session implements AutoCloseable {
        private final int port;
        private final CompletableFuture<String> codeFuture;
        private final HttpServer server;

        Session(final int port, final CompletableFuture<String> codeFuture, final HttpServer server) {
            this.port = port;
            this.codeFuture = codeFuture;
            this.server = server;
        }

        int getPort() {
            return port;
        }

        String awaitCode() {
            try {
                return codeFuture.get(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                throw new CliException("Login timed out. No response received within "
                        + LOGIN_TIMEOUT_SECONDS + " seconds.", APPLICATION_ERROR_RETURN_CODE);
            } catch (Exception ex) {
                if (ex.getCause() instanceof CliException cliEx) {
                    throw cliEx;
                }
                throw new CliException("Callback error: " + ex.getMessage(), APPLICATION_ERROR_RETURN_CODE);
            }
        }

        @Override
        public void close() {
            server.close();
        }
    }
}
