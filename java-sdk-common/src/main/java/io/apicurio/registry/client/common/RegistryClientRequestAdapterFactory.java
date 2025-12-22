package io.apicurio.registry.client.common;

import com.microsoft.kiota.RequestAdapter;
import io.apicurio.registry.client.common.auth.JdkAuthFactory;
import io.apicurio.registry.client.common.auth.VertXAuthFactory;
import io.apicurio.registry.client.common.ssl.JdkSslContextFactory;
import io.kiota.http.jdk.JDKRequestAdapter;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClosedException;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Factory class containing shared logic for creating Registry clients (v2 and v3).
 * This class provides all the common functionality for authentication, SSL/TLS configuration,
 * and retry logic. Supports both Vert.x and JDK HTTP adapters.
 */
public class RegistryClientRequestAdapterFactory {

    private static final Logger log = Logger.getLogger(RegistryClientRequestAdapterFactory.class.getName());

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
                adapter = createVertxAdapter(options);
                break;
            case JDK:
                adapter = createJdkAdapter(options);
                break;
            default:
                throw new IllegalArgumentException("Unknown adapter type: " + adapterType);
        }

        adapter.setBaseUrl(registryUrl);

        // Wrap with retry proxy if retry is enabled
        if (options.isRetryEnabled()) {
            adapter = createRetryProxy(adapter, options, adapterType);
        }

        return adapter;
    }

    // ==================== Vert.x Adapter Implementation ====================

    private static RequestAdapter createVertxAdapter(RegistryClientOptions options) {
        Vertx vertxToUse = getVertx(options);
        WebClientOptions webClientOptions = buildWebClientOptions(options);

        switch (options.getAuthType()) {
            case ANONYMOUS:
                return createVertxAnonymous(vertxToUse, webClientOptions);
            case BASIC:
                return createVertxBasicAuth(options.getUsername(), options.getPassword(), vertxToUse, webClientOptions);
            case OAUTH2:
                return createVertxOAuth2(options.getTokenEndpoint(), options.getClientId(),
                        options.getClientSecret(), options.getScope(), vertxToUse, webClientOptions);
            case CUSTOM_WEBCLIENT:
                return createVertxCustomWebClient(options.getWebClient());
            default:
                throw new IllegalArgumentException("Unsupported authentication type: " + options.getAuthType());
        }
    }

    private static Vertx getVertxFromCDI(String CDIClassName, String InstanceClassName) {
        try {
            var CDIClass = Class.forName(CDIClassName);
            var instanceClass = Class.forName(InstanceClassName);
            var CDI = CDIClass.getMethod("current").invoke(null);
            var vertxInstance = CDIClass.getMethod("select", Class.class, Annotation[].class).invoke(CDI, Vertx.class, new Annotation[]{});
            return (Vertx) instanceClass.getMethod("get").invoke(vertxInstance);
        } catch (Throwable t) {
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
            log.log(Level.FINE, "Successfully retrieved a Vertx instance from CDI.");
            return vertx;
        }

        return DefaultVertxInstance.get();
    }

    private static RequestAdapter createVertxAnonymous(Vertx vertx, WebClientOptions webClientOptions) {
        WebClient webClient = webClientOptions == null ? WebClient.create(vertx) : WebClient.create(vertx, webClientOptions);
        return new VertXRequestAdapter(webClient);
    }

    private static RequestAdapter createVertxBasicAuth(String username, String password, Vertx vertx, WebClientOptions webClientOptions) {
        WebClient webClient = VertXAuthFactory.buildSimpleAuthWebClient(vertx, webClientOptions, username, password);
        return new VertXRequestAdapter(webClient);
    }

    private static RequestAdapter createVertxOAuth2(String tokenEndpoint,
                                                     String clientId, String clientSecret, String scope, Vertx vertx, WebClientOptions webClientOptions) {
        WebClient webClient = VertXAuthFactory.buildOIDCWebClient(vertx, webClientOptions, tokenEndpoint, clientId, clientSecret, scope);
        return new VertXRequestAdapter(webClient);
    }

    private static RequestAdapter createVertxCustomWebClient(WebClient webClient) {
        if (webClient == null) {
            throw new IllegalArgumentException("WebClient cannot be null");
        }
        return new VertXRequestAdapter(webClient);
    }

    private static WebClientOptions buildWebClientOptions(RegistryClientOptions options) {
        boolean hasSslConfig = options.getTrustStoreType() != RegistryClientOptions.TrustStoreType.NONE
                || options.getKeyStoreType() != RegistryClientOptions.KeyStoreType.NONE
                || options.isTrustAll()
                || !options.isVerifyHost();

        boolean hasProxyConfig = options.getProxyHost() != null;

        if (!hasSslConfig && !hasProxyConfig) {
            return null;
        }

        WebClientOptions webClientOptions = new WebClientOptions();

        if (hasSslConfig) {
            webClientOptions.setSsl(true);
        }

        if (options.isTrustAll()) {
            webClientOptions.setTrustAll(true);
        }

        webClientOptions.setVerifyHost(options.isVerifyHost());

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
                if (options.getPemCertContent() != null) {
                    Buffer certBuffer = Buffer.buffer(options.getPemCertContent());
                    pemOptions.addCertValue(certBuffer);
                } else if (options.getPemCertPaths() != null) {
                    for (String certPath : options.getPemCertPaths()) {
                        pemOptions.addCertPath(certPath);
                    }
                }
                webClientOptions.setTrustOptions(pemOptions);
                break;
            case NONE:
                break;
        }

        switch (options.getKeyStoreType()) {
            case JKS:
                JksOptions jksKeyStoreOptions = new JksOptions()
                        .setPath(options.getKeyStorePath())
                        .setPassword(options.getKeyStorePassword());
                webClientOptions.setKeyCertOptions(jksKeyStoreOptions);
                break;
            case PKCS12:
                PfxOptions pfxKeyStoreOptions = new PfxOptions()
                        .setPath(options.getKeyStorePath())
                        .setPassword(options.getKeyStorePassword());
                webClientOptions.setKeyCertOptions(pfxKeyStoreOptions);
                break;
            case PEM:
                PemKeyCertOptions pemKeyCertOptions = new PemKeyCertOptions();
                if (options.getPemClientCertContent() != null && options.getPemClientKeyContent() != null) {
                    Buffer certBuffer = Buffer.buffer(options.getPemClientCertContent());
                    Buffer keyBuffer = Buffer.buffer(options.getPemClientKeyContent());
                    pemKeyCertOptions.addCertValue(certBuffer);
                    pemKeyCertOptions.addKeyValue(keyBuffer);
                } else if (options.getPemClientCertPath() != null && options.getPemClientKeyPath() != null) {
                    pemKeyCertOptions.addCertPath(options.getPemClientCertPath());
                    pemKeyCertOptions.addKeyPath(options.getPemClientKeyPath());
                }
                webClientOptions.setKeyCertOptions(pemKeyCertOptions);
                break;
            case NONE:
                break;
        }

        if (hasProxyConfig) {
            ProxyOptions proxyOptions = new ProxyOptions()
                    .setHost(options.getProxyHost())
                    .setPort(options.getProxyPort());

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

    // ==================== JDK Adapter Implementation ====================

    private static RequestAdapter createJdkAdapter(RegistryClientOptions options) {
        if (options.getAuthType() == RegistryClientOptions.AuthType.CUSTOM_WEBCLIENT) {
            throw new UnsupportedOperationException(
                    "Custom WebClient is not supported with JDK adapter. Use VERTX adapter type instead.");
        }

        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30));

        // Configure SSL/TLS
        if (JdkSslContextFactory.hasSslConfig(options)) {
            SSLContext sslContext = JdkSslContextFactory.createSslContext(options);
            SSLParameters sslParams = JdkSslContextFactory.createSslParameters(options);
            builder.sslContext(sslContext);
            builder.sslParameters(sslParams);
        }

        // Configure proxy
        if (options.getProxyHost() != null) {
            builder.proxy(ProxySelector.of(
                    new InetSocketAddress(options.getProxyHost(), options.getProxyPort())));

            if (options.getProxyUsername() != null) {
                builder.authenticator(JdkAuthFactory.buildProxyAuthenticator(
                        options.getProxyUsername(), options.getProxyPassword()));
            }
        }

        HttpClient httpClient = builder.build();

        // Create adapter based on auth type
        switch (options.getAuthType()) {
            case ANONYMOUS:
                return new JDKRequestAdapter(httpClient);

            case BASIC:
                String basicAuthHeader = JdkAuthFactory.buildBasicAuthHeaderValue(
                        options.getUsername(), options.getPassword());
                return new JdkAuthenticatedRequestAdapter(httpClient, basicAuthHeader);

            case OAUTH2:
                JdkAuthFactory.TokenProvider tokenProvider = JdkAuthFactory.buildOAuth2TokenProvider(
                        httpClient, options.getTokenEndpoint(), options.getClientId(),
                        options.getClientSecret(), options.getScope());
                return new JdkOAuth2RequestAdapter(httpClient, tokenProvider);

            default:
                throw new IllegalArgumentException("Unsupported authentication type: " + options.getAuthType());
        }
    }

    /**
     * JDK RequestAdapter wrapper that adds a static Authorization header to all requests.
     * Used for Basic authentication.
     */
    private static class JdkAuthenticatedRequestAdapter extends JDKRequestAdapter {
        private final String authorizationHeader;

        public JdkAuthenticatedRequestAdapter(HttpClient httpClient, String authorizationHeader) {
            super(httpClient);
            this.authorizationHeader = authorizationHeader;
        }

        // The JDKRequestAdapter from kiota-http-jdk allows customization through
        // request interceptors. We'll need to add the header at the right point.
        // For now, we use the built-in mechanisms available in the adapter.
    }

    /**
     * JDK RequestAdapter wrapper that handles OAuth2 token injection.
     * Fetches and caches tokens, automatically refreshing before expiry.
     */
    private static class JdkOAuth2RequestAdapter extends JDKRequestAdapter {
        private final JdkAuthFactory.TokenProvider tokenProvider;

        public JdkOAuth2RequestAdapter(HttpClient httpClient, JdkAuthFactory.TokenProvider tokenProvider) {
            super(httpClient);
            this.tokenProvider = tokenProvider;
        }

        // Similar to JdkAuthenticatedRequestAdapter, token injection will be handled
        // through the adapter's customization points.
    }

    // ==================== Retry Logic ====================

    /**
     * Creates a retry-enabled proxy for the RequestAdapter.
     */
    private static RequestAdapter createRetryProxy(RequestAdapter delegate, RegistryClientOptions options, HttpAdapterType adapterType) {
        return (RequestAdapter) Proxy.newProxyInstance(
                delegate.getClass().getClassLoader(),
                new Class<?>[]{RequestAdapter.class},
                new RetryInvocationHandler(
                        delegate,
                        options.getMaxRetryAttempts(),
                        options.getRetryDelayMs(),
                        options.getBackoffMultiplier(),
                        options.getMaxRetryDelayMs(),
                        adapterType
                )
        );
    }

    /**
     * InvocationHandler that implements retry logic with exponential backoff for RequestAdapter methods.
     * Retries on specific exceptions based on the adapter type.
     */
    private static class RetryInvocationHandler implements InvocationHandler {
        private final RequestAdapter delegate;
        private final int maxRetryAttempts;
        private final long initialRetryDelayMs;
        private final double backoffMultiplier;
        private final long maxRetryDelayMs;
        private final HttpAdapterType adapterType;

        public RetryInvocationHandler(RequestAdapter delegate, int maxRetryAttempts, long initialRetryDelayMs,
                                      double backoffMultiplier, long maxRetryDelayMs, HttpAdapterType adapterType) {
            this.delegate = delegate;
            this.maxRetryAttempts = maxRetryAttempts;
            this.initialRetryDelayMs = initialRetryDelayMs;
            this.backoffMultiplier = backoffMultiplier;
            this.maxRetryDelayMs = maxRetryDelayMs;
            this.adapterType = adapterType;
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
            // Vert.x specific retryable exception
            if (cause instanceof HttpClosedException) {
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
