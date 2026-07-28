package io.apicurio.registry.cli.auth;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.utils.Mapper;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;

/**
 * Discovers OIDC provider endpoints from .well-known/openid-configuration.
 */
@ApplicationScoped
public class OidcDiscovery {

    private static final String WELL_KNOWN_PATH = "/.well-known/openid-configuration";
    private static final int TIMEOUT_SECONDS = 15;

    @Inject
    Vertx vertx;

    public OidcEndpoints discover(final String issuerUrl) {
        final String discoveryUrl = issuerUrl.endsWith("/")
                ? issuerUrl.substring(0, issuerUrl.length() - 1) + WELL_KNOWN_PATH
                : issuerUrl + WELL_KNOWN_PATH;

        final CompletableFuture<Buffer> future = new CompletableFuture<>();
        final WebClient client = WebClient.create(vertx);

        try {
            client.getAbs(discoveryUrl).send(ar -> {
                if (ar.failed()) {
                    future.completeExceptionally(ar.cause());
                } else if (ar.result().statusCode() != 200) {
                    future.completeExceptionally(new CliException(
                            "OIDC discovery failed with HTTP " + ar.result().statusCode() + " from '" + discoveryUrl + "'.",
                            APPLICATION_ERROR_RETURN_CODE));
                } else {
                    future.complete(ar.result().body());
                }
            });

            final Buffer body = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            final JsonNode json = Mapper.MAPPER.readTree(body.getBytes());

            final String authorizationEndpoint = requireField(json, "authorization_endpoint", discoveryUrl);
            final String tokenEndpoint = requireField(json, "token_endpoint", discoveryUrl);

            return new OidcEndpoints(authorizationEndpoint, tokenEndpoint);
        } catch (CliException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CliException("Could not connect to OIDC issuer at '" + issuerUrl + "': " + ex.getMessage(),
                    APPLICATION_ERROR_RETURN_CODE);
        } finally {
            client.close();
        }
    }

    private static String requireField(final JsonNode json, final String field, final String discoveryUrl) {
        final JsonNode node = json.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            throw new CliException("OIDC discovery at '" + discoveryUrl + "' is missing required field '" + field + "'.",
                    APPLICATION_ERROR_RETURN_CODE);
        }
        return node.asText();
    }
}
