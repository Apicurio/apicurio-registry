package io.apicurio.registry.client;

import com.microsoft.kiota.RequestAdapter;
import io.apicurio.registry.client.auth.VertXAuthFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClosedException;
import io.vertx.ext.web.client.WebClient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Factory for creating instances of {@link RegistryClient}. This factory centralizes
 * the creation logic and provides a unified method for creating clients with different
 * authentication configurations using {@link RegistryClientOptions}.
 */
public final class RegistryClientFactory {

    private static final Vertx vertx = Vertx.vertx();

    /**
     * Creates a RegistryClient using the provided options.
     *
     * @param options the configuration options for the client
     * @return a new RegistryClient instance
     * @throws IllegalArgumentException if options are invalid
     */
    public static RegistryClient create(RegistryClientOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("RegistryClientOptions cannot be null");
        }

        validateRegistryUrl(options);
        validateAuth(options);

        Vertx vertxToUse = options.getVertx() != null ? options.getVertx() : vertx;

        RequestAdapter adapter;
        switch (options.getAuthType()) {
            case ANONYMOUS:
                adapter = createAnonymous(vertxToUse);
                break;
            case BASIC:
                adapter = createBasicAuth(options.getUsername(), options.getPassword(), vertxToUse);
                break;
            case OAUTH2:
                adapter = createOAuth2(options.getTokenEndpoint(), options.getClientId(),
                        options.getClientSecret(), options.getScope(), vertxToUse);
                break;
            case CUSTOM_WEBCLIENT:
                adapter = createCustomWebClient(options.getWebClient());
                break;
            default:
                throw new IllegalArgumentException("Unsupported authentication type: " + options.getAuthType());
        }
        adapter.setBaseUrl(options.getRegistryUrl());

        // Wrap with retry proxy if retry is enabled
        if (options.isRetryEnabled()) {
            adapter = createRetryProxy(adapter, options);
        }

        return new RegistryClient(adapter);
    }

    // Private implementation methods
    
    private static RequestAdapter createAnonymous(Vertx vertx) {
        RequestAdapter adapter = new VertXRequestAdapter(vertx);
        return adapter;
    }
    
    private static RequestAdapter createBasicAuth(String username, String password, Vertx vertx) {
        WebClient webClient = VertXAuthFactory.buildSimpleAuthWebClient(vertx, username, password);
        RequestAdapter adapter = new VertXRequestAdapter(webClient);
        return adapter;
    }
    
    private static RequestAdapter createOAuth2(String tokenEndpoint,
                                               String clientId, String clientSecret, String scope, Vertx vertx) {
        WebClient webClient = VertXAuthFactory.buildOIDCWebClient(vertx, tokenEndpoint, clientId, clientSecret, scope);
        RequestAdapter adapter = new VertXRequestAdapter(webClient);
        return adapter;
    }
    
    private static RequestAdapter createCustomWebClient(WebClient webClient) {
        if (webClient == null) {
            throw new IllegalArgumentException("WebClient cannot be null");
        }

        RequestAdapter adapter = new VertXRequestAdapter(webClient);
        return adapter;
    }

    /**
     * Creates a retry-enabled proxy for the RequestAdapter.
     *
     * @param delegate the original RequestAdapter to wrap
     * @param options the client options containing retry configuration
     * @return a proxy RequestAdapter with retry functionality
     */
    private static RequestAdapter createRetryProxy(RequestAdapter delegate, RegistryClientOptions options) {
        return (RequestAdapter) Proxy.newProxyInstance(
                delegate.getClass().getClassLoader(),
                new Class<?>[]{RequestAdapter.class},
                new RetryInvocationHandler(
                        delegate, 
                        options.getMaxRetryAttempts(), 
                        options.getRetryDelayMs(),
                        options.getBackoffMultiplier(),
                        options.getMaxRetryDelayMs()
                )
        );
    }

    /**
     * InvocationHandler that implements retry logic with exponential backoff for RequestAdapter methods.
     * Only retries on specific exceptions like HttpClosedException.
     */
    private static class RetryInvocationHandler implements InvocationHandler {
        private final RequestAdapter delegate;
        private final int maxRetryAttempts;
        private final long initialRetryDelayMs;
        private final double backoffMultiplier;
        private final long maxRetryDelayMs;

        public RetryInvocationHandler(RequestAdapter delegate, int maxRetryAttempts, long initialRetryDelayMs,
                                    double backoffMultiplier, long maxRetryDelayMs) {
            this.delegate = delegate;
            this.maxRetryAttempts = maxRetryAttempts;
            this.initialRetryDelayMs = initialRetryDelayMs;
            this.backoffMultiplier = backoffMultiplier;
            this.maxRetryDelayMs = maxRetryDelayMs;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            int attempt = 0;
            while (true) {
                Throwable originalCause = null;
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (originalCause == null) {
                        originalCause = cause;
                    }
                    
                    // Only retry if the error is retryable and if we haven't exceeded max attempts
                    if (isRetryable(cause) && attempt < maxRetryAttempts) {
                        attempt++;
                        long delayMs = calculateRetryDelay(attempt);
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Retry interrupted", interruptedException);
                        }
                    } else {
                        // Re-throw the original cause
                        throw originalCause;
                    }
                }
            }
        }

        private static boolean isRetryable(Throwable cause) {
            return cause instanceof HttpClosedException;
        }

        /**
         * Calculates the retry delay using exponential backoff based on the attempt number.
         *
         * @param attempt the current attempt number (1-based)
         * @return the delay in milliseconds
         */
        private long calculateRetryDelay(int attempt) {
            // Calculate exponential backoff: initialDelay * (multiplier ^ (attempt - 1))
            double delay = initialRetryDelayMs * Math.pow(backoffMultiplier, attempt - 1);
            
            // Cap the delay at maxRetryDelayMs
            return Math.min((long) delay, maxRetryDelayMs);
        }
    }

    // Private validation methods
    
    private static void validateRegistryUrl(RegistryClientOptions options) {
        String registryUrl = options.getRegistryUrl();
        if (registryUrl == null || registryUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Registry URL cannot be null or empty");
        }
    }

    private static void validateAuth(RegistryClientOptions options) {
        switch (options.getAuthType()) {
            case ANONYMOUS:
            case CUSTOM_WEBCLIENT:
                break;
            case BASIC:
                validateCredentials(options.getUsername(), options.getPassword());
                break;
            case OAUTH2:
                validateOAuth2Credentials(options.getTokenEndpoint(), options.getClientId(), options.getClientSecret());
                break;
        }
    }

    private static void validateCredentials(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
    }

    private static void validateOAuth2Credentials(String tokenEndpoint, String clientId, String clientSecret) {
        if (tokenEndpoint == null || tokenEndpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("Token endpoint cannot be null or empty");
        }
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("Client secret cannot be null or empty");
        }
    }
}
