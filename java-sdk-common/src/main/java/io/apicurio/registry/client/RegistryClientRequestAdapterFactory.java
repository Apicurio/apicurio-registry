package io.apicurio.registry.client;

import com.microsoft.kiota.RequestAdapter;
import io.apicurio.registry.client.auth.VertXAuthFactory;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClosedException;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.lang.annotation.Annotation;
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
 * and retry logic.
 */
public class RegistryClientRequestAdapterFactory {

    private static final Logger log = Logger.getLogger(RegistryClientRequestAdapterFactory.class.getName());

    /**
     * Creates a RequestAdapter configured with authentication, SSL/TLS, and retry settings
     * from the provided options.
     *
     * @param options the configuration options
     * @return a fully configured RequestAdapter
     * @throws IllegalArgumentException if options are invalid
     */
    static RequestAdapter createRequestAdapter(RegistryClientOptions options, Version version) {
        if (options == null) {
            throw new IllegalArgumentException("RegistryClientOptions cannot be null");
        }
        var registryUrl = validateAndNormalizeRegistryUrl(options, version);
        validateAuth(options);
        Vertx vertxToUse = getVertx(options);
        // Build WebClientOptions with SSL configuration if needed
        WebClientOptions webClientOptions = buildWebClientOptions(options);

        RequestAdapter adapter;
        switch (options.getAuthType()) {
            case ANONYMOUS:
                adapter = createAnonymous(vertxToUse, webClientOptions);
                break;
            case BASIC:
                adapter = createBasicAuth(options.getUsername(), options.getPassword(), vertxToUse, webClientOptions);
                break;
            case OAUTH2:
                adapter = createOAuth2(options.getTokenEndpoint(), options.getClientId(),
                        options.getClientSecret(), options.getScope(), vertxToUse, webClientOptions);
                break;
            case CUSTOM_WEBCLIENT:
                adapter = createCustomWebClient(options.getWebClient());
                break;
            default:
                throw new IllegalArgumentException("Unsupported authentication type: " + options.getAuthType());
        }
        adapter.setBaseUrl(registryUrl);

        // Wrap with retry proxy if retry is enabled
        if (options.isRetryEnabled()) {
            adapter = createRetryProxy(adapter, options);
        }

        return adapter;
    }

    // Private implementation methods

    private static Vertx getVertxFromCDI(String CDIClassName, String InstanceClassName) {
        try {
            var CDIClass = Class.forName(CDIClassName);
            var instanceClass = Class.forName(InstanceClassName);
            var CDI = CDIClass.getMethod("current").invoke(null);
            var vertxInstance = CDIClass.getMethod("select", Class.class, Annotation[].class).invoke(CDI, Vertx.class, new Annotation[]{});
            return (Vertx) instanceClass.getMethod("get").invoke(vertxInstance);
        } catch (Throwable t) {
            // Log and ignore
            log.log(Level.FINE, "Attempt to retrieve a Vertx instance from CDI failed: "
                    + t.getClass().getCanonicalName() + ": " + t.getMessage());
            return null;
        }
    }

    private static Vertx getVertx(RegistryClientOptions options) {

        if (options.getVertx() != null) {
            return options.getVertx();
        }

        var vertx = getVertxFromCDI("jakarta.enterprise.inject.spi.CDI", "jakarta.enterprise.inject.Instance");
        if (vertx == null) {
            vertx = getVertxFromCDI("javax.enterprise.inject.spi.CDI", "javax.enterprise.inject.Instance");
        }
        if (vertx != null) {
            log.log(Level.INFO, "Successfully retrieved a Vertx instance from CDI.");
            return vertx;
        }

        return DefaultVertxInstance.get();
    }

    private static RequestAdapter createAnonymous(Vertx vertx, WebClientOptions webClientOptions) {
        WebClient webClient = webClientOptions == null ? WebClient.create(vertx) : WebClient.create(vertx, webClientOptions);
        return new VertXRequestAdapter(webClient);
    }

    private static RequestAdapter createBasicAuth(String username, String password, Vertx vertx, WebClientOptions webClientOptions) {
        WebClient webClient = VertXAuthFactory.buildSimpleAuthWebClient(vertx, webClientOptions, username, password);
        return new VertXRequestAdapter(webClient);
    }

    private static RequestAdapter createOAuth2(String tokenEndpoint,
                                               String clientId, String clientSecret, String scope, Vertx vertx, WebClientOptions webClientOptions) {
        WebClient webClient = VertXAuthFactory.buildOIDCWebClient(vertx, webClientOptions, tokenEndpoint, clientId, clientSecret, scope);
        return new VertXRequestAdapter(webClient);
    }

    private static RequestAdapter createCustomWebClient(WebClient webClient) {
        if (webClient == null) {
            throw new IllegalArgumentException("WebClient cannot be null");
        }

        return new VertXRequestAdapter(webClient);
    }

    /**
     * Creates a retry-enabled proxy for the RequestAdapter.
     *
     * @param delegate the original RequestAdapter to wrap
     * @param options  the client options containing retry configuration
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

    /**
     * Builds WebClientOptions with SSL/TLS and proxy configuration from RegistryClientOptions.
     * Returns null if no SSL/TLS or proxy configuration is specified.
     *
     * @param options the RegistryClientOptions containing SSL/TLS and proxy configuration
     * @return WebClientOptions with SSL/TLS and proxy settings, or null if no configuration is needed
     */
    private static WebClientOptions buildWebClientOptions(RegistryClientOptions options) {
        // Check if any SSL/TLS configuration is present
        boolean hasSslConfig = options.getTrustStoreType() != RegistryClientOptions.TrustStoreType.NONE
                || options.isTrustAll()
                || !options.isVerifyHost();

        // Check if proxy configuration is present
        boolean hasProxyConfig = options.getProxyHost() != null;

        if (!hasSslConfig && !hasProxyConfig) {
            return null;
        }

        WebClientOptions webClientOptions = new WebClientOptions();

        // Configure SSL/TLS if present
        if (hasSslConfig) {
            webClientOptions.setSsl(true);
        }

        // Configure trust-all if enabled
        if (options.isTrustAll()) {
            webClientOptions.setTrustAll(true);
        }

        // Configure hostname verification
        webClientOptions.setVerifyHost(options.isVerifyHost());

        // Configure trust store based on type
        switch (options.getTrustStoreType()) {
            case JKS:
                JksOptions jksOptions = new JksOptions()
                        .setPath(options.getTrustStorePath())
                        .setPassword(options.getTrustStorePassword());
                webClientOptions.setTrustOptions(jksOptions);
                break;
            case PKCS12:
                PfxOptions pfxOptions = new PfxOptions()
                        .setPath(options.getTrustStorePath())
                        .setPassword(options.getTrustStorePassword());
                webClientOptions.setTrustOptions(pfxOptions);
                break;
            case PEM:
                PemTrustOptions pemOptions = new PemTrustOptions();

                // Check if we have PEM content (as string) or PEM file paths
                if (options.getPemCertContent() != null) {
                    // PEM content provided as string - add as value
                    Buffer certBuffer = Buffer.buffer(options.getPemCertContent());
                    pemOptions.addCertValue(certBuffer);
                } else if (options.getPemCertPaths() != null) {
                    // PEM file paths provided - add as paths
                    for (String certPath : options.getPemCertPaths()) {
                        pemOptions.addCertPath(certPath);
                    }
                }

                webClientOptions.setTrustOptions(pemOptions);
                break;
            case NONE:
                // No custom trust store configured
                break;
        }

        // Configure proxy if present
        if (hasProxyConfig) {
            ProxyOptions proxyOptions = new ProxyOptions()
                    .setHost(options.getProxyHost())
                    .setPort(options.getProxyPort());

            // Configure proxy authentication if credentials are provided
            if (options.getProxyUsername() != null && !options.getProxyUsername().isEmpty()) {
                proxyOptions.setUsername(options.getProxyUsername());
                if (options.getProxyPassword() != null) {
                    proxyOptions.setPassword(options.getProxyPassword());
                }
            }

            webClientOptions.setProxyOptions(proxyOptions);
        }

        return webClientOptions;
    }

    // Private validation methods

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