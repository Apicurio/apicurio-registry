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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Requests an OAuth2 client-credentials token at startup when {@code apicurio.mcp.auth.enabled}
 * is true, so that wrong credentials stop the server instead of failing every tool call later.
 * <p>
 * A 4xx answer from the token endpoint (wrong client ID or secret, disabled client) fails at once.
 * Connection errors and 5xx answers are retried a few times, because the identity provider may
 * still be starting next to this server (for example in the same compose file or pod).
 */
@ApplicationScoped
public class ClientCredentialsStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(ClientCredentialsStartupCheck.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final int DEFAULT_MAX_ATTEMPTS = 6;
    static final long DEFAULT_INITIAL_DELAY_MS = 1000;
    static final long MAX_DELAY_MS = 8000;

    @ConfigProperty(name = "registry.url", defaultValue = "localhost:8080")
    String rawBaseUrl;

    @Inject
    McpConfig config;

    int maxAttempts = DEFAULT_MAX_ATTEMPTS;
    long initialDelayMs = DEFAULT_INITIAL_DELAY_MS;

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

        HttpClient httpClient = buildHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(formBody(clientId, auth.clientSecret().get(),
                        auth.scope().orElse(null))))
                .build();

        long delay = initialDelayMs;
        String lastProblem = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 200 && hasAccessToken(response.body())) {
                    log.info("Obtained an OAuth2 token from {} for client '{}'", endpoint, clientId);
                    return;
                }
                lastProblem = "HTTP " + status + describeError(response.body());
                if (status >= 400 && status < 500) {
                    throw fail("Failed to obtain an OAuth2 token from " + endpoint + " for client '" + clientId
                            + "': " + lastProblem + ". Check APICURIO_MCP_AUTH_CLIENT_ID and "
                            + "APICURIO_MCP_AUTH_CLIENT_SECRET.");
                }
            } catch (IOException e) {
                lastProblem = e.getClass().getSimpleName()
                        + (e.getMessage() != null ? ": " + e.getMessage() : "");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw fail("Interrupted while requesting an OAuth2 token from " + endpoint);
            }
            if (attempt < maxAttempts) {
                log.warn("Could not obtain an OAuth2 token from {} for client '{}' (attempt {}/{}): {}. "
                        + "Retrying in {} ms", endpoint, clientId, attempt, maxAttempts, lastProblem, delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw fail("Interrupted while requesting an OAuth2 token from " + endpoint);
                }
                delay = Math.min(delay * 2, MAX_DELAY_MS);
            }
        }
        throw fail("Failed to obtain an OAuth2 token from " + endpoint + " for client '" + clientId
                + "' after " + maxAttempts + " attempts: " + lastProblem);
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
