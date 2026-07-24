package io.apicurio.registry.cli.auth;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.cli.common.CliException;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jboss.logging.Logger;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

@ApplicationScoped
public class OidcDiscovery {

    private static final Logger log = Logger.getLogger(OidcDiscovery.class);
    private static final String WELL_KNOWN_PATH = "/.well-known/openid-configuration";
    private static final String TOKEN_ENDPOINT_FIELD = "token_endpoint";
    private static final int DISCOVERY_TIMEOUT_SECONDS = 10;
    private static final int HTTP_OK = 200;
    private static final int HTTP_REDIRECT_MIN = 300;
    private static final int HTTP_REDIRECT_MAX = 400;
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";

    @Inject
    Vertx vertx;

    public String discoverTokenEndpoint(String authServerUrl) {
        final var baseUri = validateAuthServerUrl(authServerUrl);

        final var normalizedPath = baseUri.getPath() != null && baseUri.getPath().endsWith("/")
                ? baseUri.getPath().substring(0, baseUri.getPath().length() - 1)
                : baseUri.getPath();
        final var discoveryPath = (normalizedPath != null ? normalizedPath : "") + WELL_KNOWN_PATH;
        final var discoveryUri = buildDiscoveryUri(baseUri, discoveryPath);
        log.debugf("Discovering OIDC configuration from: %s", discoveryUri);

        final var responseBody = fetchDiscoveryDocument(discoveryUri);
        return extractTokenEndpoint(responseBody, authServerUrl);
    }

    protected static URI validateAuthServerUrl(String authServerUrl) {
        final URI uri;
        try {
            uri = new URI(authServerUrl);
        } catch (Exception ex) {
            throw new CliException(
                    "Invalid --auth-server-url '" + authServerUrl + "': not a valid URL.",
                    ex, VALIDATION_ERROR_RETURN_CODE);
        }
        if (uri.getScheme() == null
                || (!HTTP_SCHEME.equalsIgnoreCase(uri.getScheme()) && !HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme()))) {
            throw new CliException(
                    "Invalid --auth-server-url '" + authServerUrl + "': must start with http:// or https://.",
                    VALIDATION_ERROR_RETURN_CODE);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new CliException(
                    "Invalid --auth-server-url '" + authServerUrl + "': missing hostname.",
                    VALIDATION_ERROR_RETURN_CODE);
        }
        return uri;
    }

    private static URI buildDiscoveryUri(URI baseUri, String discoveryPath) {
        try {
            return new URI(baseUri.getScheme(), baseUri.getUserInfo(), baseUri.getHost(),
                    baseUri.getPort(), discoveryPath, baseUri.getQuery(), null);
        } catch (Exception ex) {
            throw new CliException("Invalid auth server URL: " + baseUri, ex,
                    VALIDATION_ERROR_RETURN_CODE);
        }
    }

    private String fetchDiscoveryDocument(URI uri) {
        final var httpClient = vertx.createHttpClient();
        try {
            final var future = new CompletableFuture<String>();
            sendDiscoveryRequest(httpClient, uri, future);
            return future.get(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CliException("OIDC discovery interrupted.", ex, APPLICATION_ERROR_RETURN_CODE);
        } catch (TimeoutException ex) {
            throw new CliException("OIDC discovery timed out after " + DISCOVERY_TIMEOUT_SECONDS
                    + " seconds. Check that --auth-server-url is correct.", ex,
                    APPLICATION_ERROR_RETURN_CODE);
        } catch (CliException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unwrapOrWrap(ex, uri);
        } finally {
            httpClient.close();
        }
    }

    private void sendDiscoveryRequest(io.vertx.core.http.HttpClient httpClient,
                                      URI uri, CompletableFuture<String> future) {
        final boolean ssl = HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme());
        final int defaultPort = ssl ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
        final int port = uri.getPort() != -1 ? uri.getPort() : defaultPort;
        final var requestUri = uri.getRawPath()
                + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
        final var requestOptions = new RequestOptions()
                .setMethod(HttpMethod.GET)
                .setPort(port)
                .setHost(uri.getHost())
                .setURI(requestUri)
                .setSsl(ssl);

        httpClient.request(requestOptions)
                .onSuccess(req -> {
                    req.putHeader("Accept", "application/json");
                    req.send()
                        .onSuccess(response -> handleResponse(response, uri, future))
                        .onFailure(future::completeExceptionally);
                })
                .onFailure(future::completeExceptionally);
    }

    private static void handleResponse(io.vertx.core.http.HttpClientResponse response,
                                       URI uri, CompletableFuture<String> future) {
        final int statusCode = response.statusCode();
        if (statusCode >= HTTP_REDIRECT_MIN && statusCode < HTTP_REDIRECT_MAX) {
            final var location = response.getHeader("Location");
            future.completeExceptionally(new CliException(
                    "OIDC discovery endpoint returned a redirect (HTTP "
                            + statusCode + "). Use the canonical URL"
                            + (location != null ? ": " + location : "."),
                    APPLICATION_ERROR_RETURN_CODE));
        } else if (statusCode != HTTP_OK) {
            future.completeExceptionally(new CliException(
                    "OIDC discovery failed: HTTP " + statusCode + " from " + uri,
                    APPLICATION_ERROR_RETURN_CODE));
        } else {
            response.body()
                    .onSuccess(buffer -> future.complete(buffer.toString()))
                    .onFailure(future::completeExceptionally);
        }
    }

    private static CliException unwrapOrWrap(Exception ex, URI uri) {
        if (ex.getCause() instanceof CliException ce) {
            return ce;
        }
        return new CliException("OIDC discovery failed for '" + uri + "': " + ex.getMessage(), ex,
                APPLICATION_ERROR_RETURN_CODE);
    }

    private String extractTokenEndpoint(String responseBody, String authServerUrl) {
        try {
            final JsonNode root = MAPPER.readTree(responseBody);
            final JsonNode tokenEndpointNode = root.get(TOKEN_ENDPOINT_FIELD);
            if (tokenEndpointNode == null || tokenEndpointNode.isNull() || tokenEndpointNode.asText().isBlank()) {
                throw new CliException(
                        "OIDC discovery response from '" + authServerUrl
                                + "' does not contain a '" + TOKEN_ENDPOINT_FIELD + "' field.",
                        APPLICATION_ERROR_RETURN_CODE);
            }
            final var tokenEndpoint = tokenEndpointNode.asText();
            log.debugf("Discovered token endpoint: %s", tokenEndpoint);
            return tokenEndpoint;
        } catch (CliException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CliException(
                    "Failed to parse OIDC discovery response from '" + authServerUrl + "': " + ex.getMessage(),
                    ex, APPLICATION_ERROR_RETURN_CODE);
        }
    }
}
