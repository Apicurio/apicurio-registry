package io.apicurio.registry.client.common;

import com.microsoft.kiota.RequestAdapter;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Factory class containing shared logic for creating Registry clients (v2 and v3).
 * This class provides all the common functionality for authentication, SSL/TLS configuration,
 * and retry logic. Supports both Vert.x and JDK HTTP adapters.
 *
 * <p>HTTP adapter implementations are loaded via reflection from separate modules
 * ({@code adapter-vertx} and {@code adapter-jdk}). This isolation ensures that
 * consumers only need the adapter they actually use on the classpath, which is
 * critical for GraalVM native image builds where unused adapter classes would
 * otherwise cause build failures.</p>
 *
 * @see AdapterDetector
 * @see RegistryClientOptions
 */
public class RegistryClientRequestAdapterFactory {

    private static final Logger log = Logger.getLogger(RegistryClientRequestAdapterFactory.class.getName());

    private static final String VERTX_ADAPTER_FACTORY = "io.apicurio.registry.client.common.VertxAdapterFactory";
    private static final String JDK_ADAPTER_FACTORY = "io.apicurio.registry.client.common.JdkAdapterFactory";

    /**
     * Internal property key for storing a Vertx instance. This is not typically set via string properties
     * but rather programmatically when creating the configuration. When provided, this Vertx instance
     * will be used for HTTP client connections instead of creating a new instance.
     *
     * <p><strong>Recommended:</strong> Provide your own managed Vertx instance to ensure proper
     * lifecycle management and resource cleanup.</p>
     */
    public static final String VERTX_INSTANCE = "apicurio.registry.vertx.instance";

    /**
     * Creates a RequestAdapter configured with authentication, SSL/TLS, and retry settings
     * from the provided options.
     *
     * @param options the configuration options
     * @param version the API version (V2 or V3)
     * @return a fully configured RequestAdapter
     * @throws IllegalArgumentException if options are invalid
     * @throws IllegalStateException if required adapter is not available
     */
    public static RequestAdapter createRequestAdapter(RegistryClientOptions options, Version version) {
        if (options == null) {
            throw new IllegalArgumentException("RegistryClientOptions cannot be null");
        }

        var registryUrl = validateAndNormalizeRegistryUrl(options, version);
        validateAuth(options);

        // Resolve which adapter to use
        HttpAdapterType adapterType = AdapterDetector.resolveAdapterType(options.getHttpAdapterType());
        log.log(Level.FINE, "Using HTTP adapter: {0}", adapterType);

        RequestAdapter adapter;
        switch (adapterType) {
            case VERTX:
                adapter = invokeAdapterFactory(VERTX_ADAPTER_FACTORY,
                        "Vert.x", "apicurio-registry-java-sdk-adapter-vertx", options);
                break;
            case JDK:
                adapter = invokeAdapterFactory(JDK_ADAPTER_FACTORY,
                        "JDK", "apicurio-registry-java-sdk-adapter-jdk", options);
                break;
            default:
                throw new IllegalArgumentException("Unknown adapter type: " + adapterType);
        }

        adapter.setBaseUrl(registryUrl);

        // Wrap with retry proxy if retry is enabled
        if (options.isRetryEnabled()) {
            adapter = createRetryProxy(adapter, options);
        }

        // Wrap with OTel trace context propagation decorator if explicitly enabled.
        // This goes after retry so that headers are injected on each retry attempt.
        if (options.isOtelEnabled()) {
            if (!isOTelAvailable()) {
                throw new IllegalStateException(
                        "OpenTelemetry trace context propagation was enabled, but the "
                                + "opentelemetry-api library is not on the classpath. "
                                + "Add a dependency on io.opentelemetry:opentelemetry-api to use this feature.");
            }
            adapter = new OTelRequestAdapterDecorator(adapter);
        }

        return adapter;
    }

    // ==================== Adapter Loading ====================

    /**
     * Loads an adapter factory class by name and invokes its {@code createAdapter} method via reflection.
     *
     * <p>Reflection is used intentionally so that GraalVM native image does not follow the call chain
     * into adapter modules that may not be on the classpath. This prevents build-time class resolution
     * failures when only one adapter module is present.</p>
     *
     * @param factoryClassName the fully qualified class name of the adapter factory
     * @param adapterName human-readable adapter name for error messages
     * @param moduleName Maven module name for error messages
     * @param options the client options to pass to the factory
     * @return the created RequestAdapter
     */
    private static RequestAdapter invokeAdapterFactory(String factoryClassName,
            String adapterName, String moduleName, RegistryClientOptions options) {
        try {
            Class<?> factoryClass = Class.forName(factoryClassName);
            var method = factoryClass.getMethod("createAdapter", RegistryClientOptions.class);
            return (RequestAdapter) method.invoke(null, options);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException("Failed to create " + adapterName + " adapter.", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to create " + adapterName + " adapter. "
                            + "Ensure " + moduleName + " is on the classpath.", e);
        }
    }

    // ==================== Retry Logic ====================

    /**
     * Creates a retry-enabled proxy for the RequestAdapter.
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
     * Retries on transient network exceptions (connection reset, timeout, etc.) for both Vert.x and JDK adapters.
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
            Throwable originalCause = null;
            while (true) {
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (originalCause == null) {
                        originalCause = cause;
                    }

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
                        if (isRetryable(cause) && attempt >= maxRetryAttempts) {
                            log.log(Level.WARNING, "Maximum retry attempts ({0}) exceeded for {1}: {2}",
                                    new Object[]{maxRetryAttempts, cause.getClass().getName(), cause.getMessage()});
                        }
                        throw originalCause;
                    }
                }
            }
        }

        private boolean isRetryable(Throwable cause) {
            // Vert.x specific retryable exception - check by class name to avoid compile-time Vertx dependency
            if ("io.vertx.core.http.HttpClosedException".equals(cause.getClass().getName())) {
                return true;
            }
            // JDK specific retryable exceptions
            if (cause instanceof java.net.ConnectException) {
                return true;
            }
            if (cause instanceof java.net.SocketTimeoutException) {
                return true;
            }
            if (cause instanceof java.io.IOException && cause.getMessage() != null
                    && cause.getMessage().contains("Connection reset")) {
                return true;
            }
            return false;
        }

        private long calculateRetryDelay(int attempt) {
            double delay = initialRetryDelayMs * Math.pow(backoffMultiplier, attempt - 1);
            return Math.min((long) delay, maxRetryDelayMs);
        }
    }

    // ==================== OpenTelemetry ====================

    private static final boolean OTEL_AVAILABLE;

    static {
        boolean available;
        try {
            Class.forName("io.opentelemetry.api.GlobalOpenTelemetry", false,
                    RegistryClientRequestAdapterFactory.class.getClassLoader());
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
        OTEL_AVAILABLE = available;
        log.log(Level.FINE, "OpenTelemetry API available: {0}", OTEL_AVAILABLE);
    }

    private static boolean isOTelAvailable() {
        return OTEL_AVAILABLE;
    }

    // ==================== Validation ====================

    private static final Pattern REGISTRY_URL_PROTOCOL_PATTERN = Pattern.compile("https?://.*");
    private static final Pattern REGISTRY_URL_V3_PATH_PATTERN = Pattern.compile(".*/apis/registry/v3/?");
    private static final Pattern REGISTRY_URL_V2_PATH_PATTERN = Pattern.compile(".*/apis/registry/v2/?");

    private static String validateAndNormalizeRegistryUrl(RegistryClientOptions options, Version version) {
        var url = options.getRegistryUrl();

        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Registry API URL cannot be null or blank.");
        }

        if (options.getNormalizeRegistryUrl() && !REGISTRY_URL_PROTOCOL_PATTERN.matcher(url).matches()) {
            url = "http://" + url;
        }

        switch (version) {
            case V3 -> {
                if (options.getNormalizeRegistryUrl() && !REGISTRY_URL_V3_PATH_PATTERN.matcher(url).matches()) {
                    if (!url.endsWith("/")) {
                        url += "/";
                    }
                    url += "apis/registry/v3";
                }
            }
            case V2 -> {
                if (options.getNormalizeRegistryUrl() && !REGISTRY_URL_V2_PATH_PATTERN.matcher(url).matches()) {
                    if (!url.endsWith("/")) {
                        url += "/";
                    }
                    url += "apis/registry/v2";
                }
            }
        }

        try {
            var _ignored1 = new URI(url);
            var _ignored2 = _ignored1.toURL();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Registry API URL '" + url + "' is not well-formed: " + ex.getMessage());
        }

        return url;
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
