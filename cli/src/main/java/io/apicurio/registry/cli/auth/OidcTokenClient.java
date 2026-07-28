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
 * Exchanges authorization codes and refresh tokens with the OIDC token endpoint.
 */
@ApplicationScoped
public class OidcTokenClient {

    private static final int TIMEOUT_SECONDS = 15;

    private static final String PARAM_GRANT_TYPE = "grant_type";
    private static final String PARAM_CODE = "code";
    private static final String PARAM_REDIRECT_URI = "redirect_uri";
    private static final String PARAM_CLIENT_ID = "client_id";
    private static final String PARAM_CODE_VERIFIER = "code_verifier";
    private static final String PARAM_REFRESH_TOKEN = "refresh_token";
    private static final String PARAM_SCOPE = "scope";
    private static final String GRANT_AUTHORIZATION_CODE = "authorization_code";
    private static final String GRANT_REFRESH_TOKEN = "refresh_token";

    @Inject
    Vertx vertx;

    public TokenResponse exchangeCode(final String tokenEndpoint, final String code,
                                      final String redirectUri, final String clientId,
                                      final String codeVerifier) {
        final String body = PARAM_GRANT_TYPE + "=" + GRANT_AUTHORIZATION_CODE
                + "&" + PARAM_CODE + "=" + encode(code)
                + "&" + PARAM_REDIRECT_URI + "=" + encode(redirectUri)
                + "&" + PARAM_CLIENT_ID + "=" + encode(clientId)
                + "&" + PARAM_CODE_VERIFIER + "=" + encode(codeVerifier);
        return postTokenRequest(tokenEndpoint, body);
    }

    public TokenResponse refreshToken(final String tokenEndpoint, final String refreshToken,
                                      final String clientId, final String scope) {
        var body = PARAM_GRANT_TYPE + "=" + GRANT_REFRESH_TOKEN
                + "&" + PARAM_REFRESH_TOKEN + "=" + encode(refreshToken)
                + "&" + PARAM_CLIENT_ID + "=" + encode(clientId);
        if (scope != null && !scope.isBlank()) {
            body += "&" + PARAM_SCOPE + "=" + encode(scope);
        }
        return postTokenRequest(tokenEndpoint, body);
    }

    private TokenResponse postTokenRequest(final String tokenEndpoint, final String formBody) {
        final CompletableFuture<Buffer> future = new CompletableFuture<>();
        final WebClient client = WebClient.create(vertx);

        try {
            client.postAbs(tokenEndpoint)
                    .putHeader("Content-Type", "application/x-www-form-urlencoded")
                    .sendBuffer(Buffer.buffer(formBody), ar -> {
                        if (ar.failed()) {
                            future.completeExceptionally(ar.cause());
                        } else if (ar.result().statusCode() != 200) {
                            future.completeExceptionally(new CliException(
                                    "Token request failed with HTTP " + ar.result().statusCode()
                                            + ": " + ar.result().bodyAsString(),
                                    APPLICATION_ERROR_RETURN_CODE));
                        } else {
                            future.complete(ar.result().body());
                        }
                    });

            final Buffer responseBody = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            final JsonNode json = Mapper.MAPPER.readTree(responseBody.getBytes());

            final String accessToken = requireField(json, "access_token");
            final String newRefreshToken = json.has("refresh_token") ? json.get("refresh_token").asText() : null;
            final long expiresIn = json.has("expires_in") ? json.get("expires_in").asLong() : 3600;

            return new TokenResponse(accessToken, newRefreshToken, expiresIn);
        } catch (CliException ex) {
            throw ex;
        } catch (Exception ex) {
            if (ex.getCause() instanceof CliException cliEx) {
                throw cliEx;
            }
            throw new CliException("Failed to exchange tokens: " + ex.getMessage(),
                    APPLICATION_ERROR_RETURN_CODE);
        } finally {
            client.close();
        }
    }

    private static String requireField(final JsonNode json, final String field) {
        final JsonNode node = json.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            throw new CliException("Token response is missing required field '" + field + "'.",
                    APPLICATION_ERROR_RETURN_CODE);
        }
        return node.asText();
    }

    private static String encode(final String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
