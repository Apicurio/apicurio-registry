package io.apicurio.registry.cli.auth;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.cli.common.CliException;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_RESPONSE_BYTES = 1_048_576; // 1 MB
    private static final Set<Integer> HTTP_REDIRECT_CODES = Set.of(301, 302, 303, 307, 308);
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final int REQUEST_TIMEOUT_MS = 5_000;
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";

    @Inject
    Vertx vertx;

    public String discoverTokenEndpoint(final String authServerUrl) {
        final var baseUri = validateAuthServerUrl(authServerUrl);

        final var basePath = baseUri.getPath();
        final var normalizedPath = basePath != null && basePath.endsWith("/")
                ? basePath.substring(0, basePath.length() - 1)
                : basePath;
        final var discoveryPath = (normalizedPath != null ? normalizedPath : "") + WELL_KNOWN_PATH;
        final var discoveryUri = buildDiscoveryUri(baseUri, discoveryPath);
        log.debugf("Discovering OIDC configuration from: %s", discoveryUri);

        final var responseBody = fetchDiscoveryDocument(discoveryUri);
        return extractTokenEndpoint(responseBody, authServerUrl, baseUri);
    }

    protected static URI validateAuthServerUrl(final String authServerUrl) {
        return validateHttpUrl(authServerUrl, "Invalid --auth-server-url '" + authServerUrl + "'");
    }

    private static URI validateHttpUrl(final String url, final String errorPrefix) {
        final URI uri;
        try {
            uri = new URI(url);
        } catch (Exception ex) {
            throw new CliException(errorPrefix + ": not a valid URL.", ex, VALIDATION_ERROR_RETURN_CODE);
        }
        if (uri.getScheme() == null
                || (!HTTP_SCHEME.equalsIgnoreCase(uri.getScheme()) && !HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme()))) {
            throw new CliException(
                    errorPrefix + ": must start with http:// or https://.", VALIDATION_ERROR_RETURN_CODE);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new CliException(errorPrefix + ": missing hostname.", VALIDATION_ERROR_RETURN_CODE);
        }
        return uri;
    }

    private static URI buildDiscoveryUri(final URI baseUri, final String discoveryPath) {
        try {
            return new URI(baseUri.getScheme(), null, baseUri.getHost(),
                    baseUri.getPort(), discoveryPath, baseUri.getQuery(), null);
        } catch (Exception ex) {
            throw new CliException("Invalid auth server URL: " + baseUri, ex,
                    VALIDATION_ERROR_RETURN_CODE);
        }
    }

    private String fetchDiscoveryDocument(final URI uri) {
        final var clientOptions = new HttpClientOptions()
                .setTrustAll(false)
                .setVerifyHost(true);
        final var httpClient = vertx.createHttpClient(clientOptions);
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

    private void sendDiscoveryRequest(final HttpClient httpClient,
                                      final URI uri, final CompletableFuture<String> future) {
        sendDiscoveryRequestWithRedirects(httpClient, uri, uri, future, 0);
    }

    private void sendDiscoveryRequestWithRedirects(final HttpClient httpClient,
                                                   final URI currentUri, final URI originalUri,
                                                   final CompletableFuture<String> future,
                                                   final int redirectCount) {
        final boolean ssl = HTTPS_SCHEME.equalsIgnoreCase(currentUri.getScheme());
        final int defaultPort = ssl ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
        final int port = currentUri.getPort() != -1 ? currentUri.getPort() : defaultPort;
        final var requestUri = currentUri.getRawPath()
                + (currentUri.getRawQuery() != null ? "?" + currentUri.getRawQuery() : "");
        final var requestOptions = new RequestOptions()
                .setMethod(HttpMethod.GET)
                .setPort(port)
                .setHost(currentUri.getHost())
                .setURI(requestUri)
                .setSsl(ssl)
                .setTimeout(REQUEST_TIMEOUT_MS);

        httpClient.request(requestOptions)
                .onSuccess(req -> {
                    req.putHeader("Accept", "application/json");
                    req.send()
                        .onSuccess(response -> {
                            final int statusCode = response.statusCode();
                            if (HTTP_REDIRECT_CODES.contains(statusCode)) {
                                handleRedirect(httpClient, response, currentUri, originalUri, future, redirectCount);
                            } else {
                                handleResponse(response, currentUri, future);
                            }
                        })
                        .onFailure(future::completeExceptionally);
                })
                .onFailure(future::completeExceptionally);
    }

    private void handleRedirect(final HttpClient httpClient,
                                final HttpClientResponse response,
                                final URI currentUri, final URI originalUri,
                                final CompletableFuture<String> future,
                                final int redirectCount) {
        if (redirectCount >= MAX_REDIRECTS) {
            future.completeExceptionally(new CliException(
                    "OIDC discovery exceeded maximum number of redirects (" + MAX_REDIRECTS + ").",
                    APPLICATION_ERROR_RETURN_CODE));
            return;
        }
        final var location = response.getHeader("Location");
        if (location == null || location.isBlank()) {
            future.completeExceptionally(new CliException(
                    "OIDC discovery received a redirect with no Location header.",
                    APPLICATION_ERROR_RETURN_CODE));
            return;
        }
        final URI redirectUri;
        try {
            redirectUri = currentUri.resolve(location);
        } catch (Exception ex) {
            future.completeExceptionally(new CliException(
                    "OIDC discovery received an invalid redirect Location: " + location,
                    ex, APPLICATION_ERROR_RETURN_CODE));
            return;
        }
        if (!originMatches(originalUri, redirectUri)) {
            future.completeExceptionally(new CliException(
                    "OIDC discovery redirect to '" + redirectUri + "' has a different origin than "
                            + originOf(originalUri) + ". Refusing to follow.",
                    APPLICATION_ERROR_RETURN_CODE));
            return;
        }
        log.debugf("Following OIDC discovery redirect to: %s", redirectUri);
        sendDiscoveryRequestWithRedirects(httpClient, redirectUri, originalUri, future, redirectCount + 1);
    }

    private static void handleResponse(final HttpClientResponse response,
                                       final URI uri, final CompletableFuture<String> future) {
        final int statusCode = response.statusCode();
        if (statusCode != HTTP_OK) {
            future.completeExceptionally(new CliException(
                    "OIDC discovery failed: HTTP " + statusCode + " from " + uri,
                    APPLICATION_ERROR_RETURN_CODE));
            return;
        }
        if (exceedsMaxSizeByContentLength(response, uri, future)) {
            return;
        }
        readBodyWithSizeLimit(response, uri, future);
    }

    private static boolean exceedsMaxSizeByContentLength(final HttpClientResponse response,
                                                         final URI uri, final CompletableFuture<String> future) {
        final var contentLength = response.getHeader("Content-Length");
        if (contentLength == null) {
            return false;
        }
        try {
            if (Long.parseLong(contentLength) > MAX_RESPONSE_BYTES) {
                future.completeExceptionally(new CliException(
                        "OIDC discovery response from " + uri + " exceeds maximum size.",
                        APPLICATION_ERROR_RETURN_CODE));
                return true;
            }
        } catch (NumberFormatException ignored) {
            log.debugf("Malformed Content-Length header '%s', falling back to streaming size check", contentLength);
        }
        return false;
    }

    private static void readBodyWithSizeLimit(final HttpClientResponse response,
                                              final URI uri, final CompletableFuture<String> future) {
        final var accumulated = Buffer.buffer();
        final var cancelled = new AtomicBoolean(false);
        response.handler(chunk -> {
            if (cancelled.get()) {
                return;
            }
            if (accumulated.length() + chunk.length() > MAX_RESPONSE_BYTES) {
                cancelled.set(true);
                future.completeExceptionally(new CliException(
                        "OIDC discovery response from " + uri + " exceeds maximum size.",
                        APPLICATION_ERROR_RETURN_CODE));
                response.request().reset();
            } else {
                accumulated.appendBuffer(chunk);
            }
        });
        response.endHandler(v -> {
            if (!cancelled.get() && !future.isDone()) {
                future.complete(accumulated.toString());
            }
        });
        response.exceptionHandler(future::completeExceptionally);
    }

    private static CliException unwrapOrWrap(final Exception ex, final URI uri) {
        if (ex.getCause() instanceof CliException ce) {
            return ce;
        }
        return new CliException("OIDC discovery failed for '" + uri + "': " + ex.getMessage(), ex,
                APPLICATION_ERROR_RETURN_CODE);
    }

    private static void validateTokenEndpointUrl(final String tokenEndpoint, final String authServerUrl,
                                                    final URI authServerUri) {
        final var tokenUri = validateHttpUrl(tokenEndpoint,
                "OIDC discovery from '" + authServerUrl + "' returned an invalid token_endpoint '"
                        + tokenEndpoint + "'");
        if (!originMatches(authServerUri, tokenUri)) {
            throw new CliException(
                    "OIDC discovery from '" + authServerUrl + "' returned a token_endpoint '"
                            + tokenEndpoint + "' with a different origin. Expected "
                            + originOf(authServerUri) + " but got " + originOf(tokenUri) + ".",
                    APPLICATION_ERROR_RETURN_CODE);
        }
    }

    private static boolean originMatches(final URI a, final URI b) {
        return a.getScheme().equalsIgnoreCase(b.getScheme())
                && a.getHost().equalsIgnoreCase(b.getHost())
                && effectivePort(a) == effectivePort(b);
    }

    private static int effectivePort(final URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme()) ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
    }

    private static String originOf(final URI uri) {
        final int port = effectivePort(uri);
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost() + ":" + port;
    }

    private String extractTokenEndpoint(final String responseBody, final String authServerUrl,
                                        final URI authServerUri) {
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
            validateTokenEndpointUrl(tokenEndpoint, authServerUrl, authServerUri);
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
