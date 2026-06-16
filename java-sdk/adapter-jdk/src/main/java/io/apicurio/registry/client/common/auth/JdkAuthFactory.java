package io.apicurio.registry.client.common.auth;

import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Factory class for creating authentication-enabled components for JDK HttpClient.
 * Provides OAuth2 client credentials flow and Basic authentication support.
 */
public class JdkAuthFactory {

    private static final Logger log = Logger.getLogger(JdkAuthFactory.class.getName());

    private static final boolean OTEL_AVAILABLE;

    static {
        boolean available;
        try {
            Class.forName("io.opentelemetry.api.GlobalOpenTelemetry", false,
                    JdkAuthFactory.class.getClassLoader());
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
        OTEL_AVAILABLE = available;
    }

    private JdkAuthFactory() {
        // Prevent instantiation
    }

    /**
     * Creates a Basic authentication header value.
     *
     * @param username the username
     * @param password the password
     * @return the Base64-encoded Authorization header value (e.g., "Basic dXNlcjpwYXNz")
     */
    public static String buildBasicAuthHeaderValue(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    /**
     * Creates an Authenticator for proxy authentication.
     *
     * @param username the proxy username
     * @param password the proxy password
     * @return an Authenticator configured for the proxy credentials
     */
    public static Authenticator buildProxyAuthenticator(String username, String password) {
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == RequestorType.PROXY) {
                    return new PasswordAuthentication(username, password.toCharArray());
                }
                return null;
            }
        };
    }

    /**
     * Creates an OAuth2 token provider that handles client credentials flow.
     * The token provider caches tokens and automatically refreshes them before expiry.
     *
     * @param httpClient    the HttpClient to use for token requests
     * @param tokenEndpoint the OAuth2 token endpoint URL
     * @param clientId      the OAuth2 client ID
     * @param clientSecret  the OAuth2 client secret
     * @param scope         the OAuth2 scope (optional, can be null)
     * @param otelEnabled   whether to inject OpenTelemetry trace context into token requests
     * @return a TokenProvider that supplies valid access tokens
     */
    public static TokenProvider buildOAuth2TokenProvider(HttpClient httpClient, String tokenEndpoint,
                                                         String clientId, String clientSecret, String scope,
                                                         boolean otelEnabled) {
        return new OAuth2TokenProvider(httpClient, tokenEndpoint, clientId, clientSecret, scope, otelEnabled);
    }

    /**
     * Interface for providing authentication tokens.
     */
    public interface TokenProvider {
        /**
         * Gets a valid access token, refreshing if necessary.
         *
         * @return the access token
         * @throws IOException if token retrieval fails
         */
        String getToken() throws IOException;
    }

    /**
     * OAuth2 token provider implementation using client credentials flow.
     * Thread-safe with automatic token caching and refresh.
     */
    public static class OAuth2TokenProvider implements TokenProvider {

        private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern EXPIRES_IN_PATTERN = Pattern.compile("\"expires_in\"\\s*:\\s*(\\d+)");
        private static final long REFRESH_BUFFER_SECONDS = 30;

        private final HttpClient httpClient;
        private final String tokenEndpoint;
        private final String clientId;
        private final String clientSecret;
        private final String scope;
        private final boolean otelEnabled;

        private final ReentrantLock lock = new ReentrantLock();
        private volatile String cachedToken;
        private volatile Instant tokenExpiry;

        public OAuth2TokenProvider(HttpClient httpClient, String tokenEndpoint,
                                   String clientId, String clientSecret, String scope,
                                   boolean otelEnabled) {
            this.httpClient = httpClient;
            this.tokenEndpoint = tokenEndpoint;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.scope = scope;
            this.otelEnabled = otelEnabled;
        }

        @Override
        public String getToken() throws IOException {
            lock.lock();
            try {
                if (!isTokenValid()) {
                    refreshToken();
                }
                return cachedToken;
            } finally {
                lock.unlock();
            }
        }

        private boolean isTokenValid() {
            return cachedToken != null && tokenExpiry != null
                    && Instant.now().plusSeconds(REFRESH_BUFFER_SECONDS).isBefore(tokenExpiry);
        }

        private void refreshToken() throws IOException {
            log.fine("Refreshing OAuth2 token from " + tokenEndpoint);

            StringBuilder body = new StringBuilder();
            body.append("grant_type=client_credentials");
            body.append("&client_id=").append(urlEncode(clientId));
            body.append("&client_secret=").append(urlEncode(clientSecret));
            if (scope != null && !scope.isEmpty()) {
                body.append("&scope=").append(urlEncode(scope));
            }

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()));

            // Inject OTel trace context if available and enabled
            if (otelEnabled && OTEL_AVAILABLE) {
                injectTraceContext(requestBuilder);
            }

            HttpRequest request = requestBuilder.build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new IOException("Token request failed with status " + response.statusCode()
                            + ": " + response.body());
                }

                parseTokenResponse(response.body());
                log.fine("Successfully refreshed OAuth2 token, expires at " + tokenExpiry);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Token request interrupted", e);
            }
        }

        private void parseTokenResponse(String responseBody) throws IOException {
            Matcher tokenMatcher = ACCESS_TOKEN_PATTERN.matcher(responseBody);
            if (!tokenMatcher.find()) {
                throw new IOException("No access_token found in token response: " + responseBody);
            }
            cachedToken = tokenMatcher.group(1);

            Matcher expiryMatcher = EXPIRES_IN_PATTERN.matcher(responseBody);
            if (expiryMatcher.find()) {
                long expiresInSeconds = Long.parseLong(expiryMatcher.group(1));
                tokenExpiry = Instant.now().plusSeconds(expiresInSeconds);
            } else {
                // Default to 1 hour if expires_in not provided
                tokenExpiry = Instant.now().plusSeconds(3600);
                log.log(Level.WARNING, "No expires_in in token response, defaulting to 1 hour expiry");
            }
        }

        private static String urlEncode(String value) {
            try {
                return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name());
            } catch (java.io.UnsupportedEncodingException e) {
                // UTF-8 is always supported
                throw new RuntimeException(e);
            }
        }

        /**
         * Injects OpenTelemetry trace context headers into the request builder.
         * Uses reflection to avoid compile-time dependency on OTel API.
         */
        private static void injectTraceContext(HttpRequest.Builder requestBuilder) {
            try {
                // Get GlobalOpenTelemetry.getPropagators()
                Class<?> globalOpenTelemetryClass = Class.forName("io.opentelemetry.api.GlobalOpenTelemetry");
                Object contextPropagators = globalOpenTelemetryClass.getMethod("getPropagators").invoke(null);

                // Get TextMapPropagator from ContextPropagators
                Class<?> contextPropagatorsClass = Class.forName("io.opentelemetry.context.propagation.ContextPropagators");
                Object textMapPropagator = contextPropagatorsClass.getMethod("getTextMapPropagator").invoke(contextPropagators);

                // Get Context.current()
                Class<?> contextClass = Class.forName("io.opentelemetry.context.Context");
                Object currentContext = contextClass.getMethod("current").invoke(null);

                // Create TextMapSetter lambda using reflection
                Class<?> textMapSetterClass = Class.forName("io.opentelemetry.context.propagation.TextMapSetter");
                Object setter = java.lang.reflect.Proxy.newProxyInstance(
                        textMapSetterClass.getClassLoader(),
                        new Class<?>[]{textMapSetterClass},
                        (proxy, method, args) -> {
                            if ("set".equals(method.getName()) && args.length == 3) {
                                String key = (String) args[1];
                                String value = (String) args[2];
                                requestBuilder.header(key, value);
                            }
                            return null;
                        });

                // Call inject(context, carrier, setter)
                Class<?> textMapPropagatorClass = Class.forName("io.opentelemetry.context.propagation.TextMapPropagator");
                textMapPropagatorClass.getMethod("inject", contextClass, Object.class, textMapSetterClass)
                        .invoke(textMapPropagator, currentContext, requestBuilder, setter);

            } catch (Exception e) {
                // Log but don't fail the request if trace context injection fails
                log.log(Level.WARNING, "Failed to inject OTel trace context into OAuth2 token request", e);
            }
        }
    }
}
