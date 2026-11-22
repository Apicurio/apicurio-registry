package io.apicurio.registry.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.client.common.ssl.JdkSslContextFactory;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Requests an OAuth2 client-credentials token at startup when {@code apicurio.mcp.auth.enabled}
 * is true, so that wrong credentials stop the server instead of failing every tool call later.
 * <p>
 * A 4xx answer from the token endpoint (wrong client ID or secret, disabled client) fails at once.
 * Connection errors, timeouts and 5xx answers are retried within an overall deadline of
 * {@link #DEFAULT_DEADLINE_MS} ms, because the identity provider may
 * still be starting next to this server (for example in the same compose file or pod).
 */
@ApplicationScoped
public class ClientCredentialsStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(ClientCredentialsStartupCheck.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final int DEFAULT_MAX_ATTEMPTS = 6;
    static final long DEFAULT_INITIAL_DELAY_MS = 1000;
    static final long MAX_DELAY_MS = 8000;
    static final long DEFAULT_REQUEST_TIMEOUT_MS = 30_000;
    /** Upper bound for the whole check: requests, body reads and backoff. */
    static final long DEFAULT_DEADLINE_MS = 60_000;

    @ConfigProperty(name = "registry.url", defaultValue = "localhost:8080")
    String rawBaseUrl;

    @Inject
    McpConfig config;

    int maxAttempts = DEFAULT_MAX_ATTEMPTS;
    long initialDelayMs = DEFAULT_INITIAL_DELAY_MS;
    long requestTimeoutMs = DEFAULT_REQUEST_TIMEOUT_MS;
    long deadlineMs = DEFAULT_DEADLINE_MS;

    void onStart(@Observes StartupEvent event) {
        verify();
    }

    /**
     * @throws IllegalStateException if auth is enabled and no token could be obtained
     */
    void verify() {
        var auth = config.auth();
        if (!auth.enabled()) {
            return;
        }
        if (auth.tokenEndpoint().isEmpty() || auth.clientId().isEmpty() || auth.clientSecret().isEmpty()) {
            throw fail("OAuth2 authentication requires 'apicurio.mcp.auth.token-endpoint', "
                    + "'apicurio.mcp.auth.client-id', and 'apicurio.mcp.auth.client-secret' to be configured");
        }
        String endpoint = auth.tokenEndpoint().get();
        String clientId = auth.clientId().get();

        String secret = auth.clientSecret().get();
        String body = formBody(clientId, secret, auth.scope().orElse(null));
        HttpClient httpClient = buildHttpClient();

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(deadlineMs);
        long delay = initialDelayMs;
        String lastProblem = null;
        int attempt = 0;
        while (attempt < maxAttempts) {
            long remainingMs = remainingMs(deadline);
            if (remainingMs <= 0) {
                break;
            }
            attempt++;
            long timeoutMs = Math.min(requestTimeoutMs, remainingMs);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofMillis(timeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            // The future completes only once the whole body is read, so get() with a timeout
            // also bounds a server that sends headers and then stalls.
            CompletableFuture<HttpResponse<String>> future =
                    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            try {
                HttpResponse<String> response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                int status = response.statusCode();
                if (status == 200 && hasAccessToken(response.body())) {
                    log.info("Obtained an OAuth2 token from {} for client '{}'", endpoint, clientId);
                    return;
                }
                lastProblem = "HTTP " + status + redact(describeError(response.body()), secret);
                if (status >= 400 && status < 500) {
                    throw fail("Failed to obtain an OAuth2 token from " + endpoint + " for client '" + clientId
                            + "': " + lastProblem + ". Check APICURIO_MCP_AUTH_CLIENT_ID and "
                            + "APICURIO_MCP_AUTH_CLIENT_SECRET.");
                }
            } catch (TimeoutException e) {
                future.cancel(true);
                lastProblem = "no complete response within " + timeoutMs + " ms";
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                lastProblem = redact(cause.getClass().getSimpleName()
                        + (cause.getMessage() != null ? ": " + cause.getMessage() : ""), secret);
            } catch (InterruptedException e) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                throw fail("Interrupted while requesting an OAuth2 token from " + endpoint);
            }
            long sleepMs = Math.min(delay, remainingMs(deadline));
            if (attempt < maxAttempts && sleepMs > 0) {
                log.warn("Could not obtain an OAuth2 token from {} for client '{}' (attempt {}/{}): {}. "
                        + "Retrying in {} ms", endpoint, clientId, attempt, maxAttempts, lastProblem, sleepMs);
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw fail("Interrupted while requesting an OAuth2 token from " + endpoint);
                }
                delay = Math.min(delay * 2, MAX_DELAY_MS);
            }
        }
        throw fail("Failed to obtain an OAuth2 token from " + endpoint + " for client '" + clientId
                + "' after " + attempt + " attempts in at most " + deadlineMs + " ms: " + lastProblem);
    }

    private static long remainingMs(long deadlineNanos) {
        return TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
    }

    /**
     * The token endpoint controls the error text and could echo the submitted secret, so remove it
     * in the forms it was sent in: raw and form-encoded.
     */
    static String redact(String text, String secret) {
        if (text == null || secret == null || secret.isEmpty()) {
            return text;
        }
        String result = text.replace(secret, "***");
        String encoded = URLEncoder.encode(secret, StandardCharsets.UTF_8);
        if (!encoded.isEmpty()) {
            result = result.replace(encoded, "***");
        }
        return result;
    }

    private static IllegalStateException fail(String message) {
        log.error(message);
        return new IllegalStateException(message);
    }

    private HttpClient buildHttpClient() {
        var options = RegistryClientOptions.create(rawBaseUrl);
        RegistryClientResolver.configureTls(config.tls(), options);
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10));
        if (JdkSslContextFactory.hasSslConfig(options)) {
            builder.sslContext(JdkSslContextFactory.createSslContext(options));
            builder.sslParameters(JdkSslContextFactory.createSslParameters(options));
        }
        return builder.build();
    }

    private static String formBody(String clientId, String clientSecret, String scope) {
        StringBuilder body = new StringBuilder("grant_type=client_credentials");
        body.append("&client_id=").append(URLEncoder.encode(clientId, StandardCharsets.UTF_8));
        body.append("&client_secret=").append(URLEncoder.encode(clientSecret, StandardCharsets.UTF_8));
        if (scope != null && !scope.isEmpty()) {
            body.append("&scope=").append(URLEncoder.encode(scope, StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    private static boolean hasAccessToken(String body) {
        try {
            JsonNode token = MAPPER.readTree(body).get("access_token");
            return token != null && token.isTextual() && !token.asText().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Only the standard OAuth2 {@code error} and {@code error_description} fields are reported:
     * the raw body is never logged.
     */
    static String describeError(String body) {
        try {
            JsonNode json = MAPPER.readTree(body);
            if (json == null || !json.isObject()) {
                return "";
            }
            String error = json.path("error").asText("");
            String description = json.path("error_description").asText("");
            if (error.isEmpty() && description.isEmpty()) {
                return "";
            }
            return " (" + error + (description.isEmpty() ? "" : ": " + description) + ")";
        } catch (Exception e) {
            return "";
        }
    }
}
