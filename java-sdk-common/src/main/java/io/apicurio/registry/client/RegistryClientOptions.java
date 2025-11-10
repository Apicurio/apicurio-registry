package io.apicurio.registry.client;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

/**
 * Configuration options for creating a RegistryClient. This class encapsulates all the
 * configuration parameters needed to create different types of registry clients.
 *
 * <p>The options support the following authentication methods:</p>
 * <ul>
 *   <li>Anonymous (no authentication)</li>
 *   <li>Basic authentication (username/password)</li>
 *   <li>OAuth2/OIDC authentication (client credentials)</li>
 *   <li>Custom WebClient (for advanced scenarios)</li>
 * </ul>
 */
public class RegistryClientOptions {

    /**
     * <p><strong>Warning:</strong> If you do not provide a Vertx instance,
     * we will try to retrieve it from the current CDI context. If that fails,
     * we will use a default instance managed by {@link DefaultVertxInstance}.
     * <p>
     * However, using the default Vertx instance is not recommended for
     * production use. Applications should manage their own Vertx instance and provide it
     * via {@link RegistryClientOptions#vertx(Vertx)} to ensure proper lifecycle management
     * and resource cleanup.</p>
     */
    public static RegistryClientOptions create() {
        return new RegistryClientOptions();
    }

    /**
     * <p><strong>Warning:</strong> If you do not provide a Vertx instance,
     * we will try to retrieve it from the current CDI context. If that fails,
     * we will use a default instance managed by {@link DefaultVertxInstance}.
     * <p>
     * However, using the default Vertx instance is not recommended for
     * production use. Applications should manage their own Vertx instance and provide it
     * via {@link RegistryClientOptions#vertx(Vertx)} to ensure proper lifecycle management
     * and resource cleanup.</p>
     */
    public static RegistryClientOptions create(String registryUrl) {
        return new RegistryClientOptions().registryUrl(registryUrl);
    }

    public static RegistryClientOptions create(String registryUrl, Vertx vertx) {
        return new RegistryClientOptions().registryUrl(registryUrl).vertx(vertx);
    }

    /**
     * Authentication type enumeration.
     */
    public enum AuthType {
        ANONYMOUS,
        BASIC,
        OAUTH2,
        CUSTOM_WEBCLIENT
    }

    /**
     * Trust store type enumeration for SSL/TLS configuration.
     */
    public enum TrustStoreType {
        JKS,     // Java KeyStore format
        PKCS12,  // PKCS#12 format
        PEM,     // PEM certificate file(s)
        NONE     // No custom trust store configured
    }

    private String registryUrl;
    private boolean normalizeRegistryUrl = true;
    // Provided vertx
    private Vertx vertx;
    // Auth config
    private AuthType authType = AuthType.ANONYMOUS;
    private String username;
    private String password;
    private String tokenEndpoint;
    private String clientId;
    private String clientSecret;
    private String scope;
    private WebClient webClient;
    // Retry config
    private boolean retryEnabled = false;
    private int maxRetryAttempts = 3;
    private long retryDelayMs = 1000;
    private double backoffMultiplier = 2.0;
    private long maxRetryDelayMs = 10000; // 10 seconds max delay
    // SSL/TLS config
    private TrustStoreType trustStoreType = TrustStoreType.NONE;
    private String trustStorePath;
    private String trustStorePassword;
    private String[] pemCertPaths;
    private String pemCertContent; // PEM certificate content as string (alternative to file paths)
    private boolean trustAll = false;
    private boolean verifyHost = true;
    // Proxy config
    private String proxyHost;
    private int proxyPort = -1;
    private String proxyUsername;
    private String proxyPassword;

    private RegistryClientOptions() {
    }

    public String getRegistryUrl() {
        return registryUrl;
    }

    public boolean getNormalizeRegistryUrl() {
        return normalizeRegistryUrl;
    }

    public AuthType getAuthType() {
        return authType;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getScope() {
        return scope;
    }

    public Vertx getVertx() {
        return vertx;
    }

    public WebClient getWebClient() {
        return webClient;
    }

    public boolean isRetryEnabled() {
        return retryEnabled;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public long getMaxRetryDelayMs() {
        return maxRetryDelayMs;
    }

    public TrustStoreType getTrustStoreType() {
        return trustStoreType;
    }

    public String getTrustStorePath() {
        return trustStorePath;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public String[] getPemCertPaths() {
        return pemCertPaths;
    }

    public String getPemCertContent() {
        return pemCertContent;
    }

    public boolean isTrustAll() {
        return trustAll;
    }

    public boolean isVerifyHost() {
        return verifyHost;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    /**
     * Sets the registry URL.
     * <p>
     * If the protocol or the API path is not provided (e.g., "localhost:8080"), it will be added.
     *
     * @param registryUrl the base URL of the registry API (e.g., "http://localhost:8080/apis/registry/v3")
     * @return this builder
     */
    public RegistryClientOptions registryUrl(String registryUrl) {
        this.registryUrl = registryUrl;
        return this;
    }

    /**
     * Sets the registry URL.
     * <p>
     * URL will not be modified, but valid URL is still required.
     *
     * @param registryUrl the base URL of the registry API (e.g., "http://localhost:8080/apis/registry/v3")
     * @return this builder
     */
    public RegistryClientOptions rawRegistryUrl(String registryUrl) {
        this.registryUrl = registryUrl;
        normalizeRegistryUrl = false;
        return this;
    }

    /**
     * Configures basic authentication.
     *
     * @param username the username for basic authentication
     * @param password the password for basic authentication
     * @return this builder
     */
    public RegistryClientOptions basicAuth(String username, String password) {
        clearAuth();
        this.authType = AuthType.BASIC;
        this.username = username;
        this.password = password;
        return this;
    }

    /**
     * Configures OAuth2/OIDC authentication using client credentials flow.
     *
     * @param tokenEndpoint the OAuth2 token endpoint URL
     * @param clientId      the OAuth2 client ID
     * @param clientSecret  the OAuth2 client secret
     * @return this builder
     */
    public RegistryClientOptions oauth2(String tokenEndpoint, String clientId, String clientSecret) {
        clearAuth();
        return oauth2(tokenEndpoint, clientId, clientSecret, null);
    }

    /**
     * Configures OAuth2/OIDC authentication using client credentials flow with scope.
     *
     * @param tokenEndpoint the OAuth2 token endpoint URL
     * @param clientId      the OAuth2 client ID
     * @param clientSecret  the OAuth2 client secret
     * @param scope         the OAuth2 scope (optional, can be null)
     * @return this builder
     */
    public RegistryClientOptions oauth2(String tokenEndpoint, String clientId, String clientSecret, String scope) {
        clearAuth();
        this.authType = AuthType.OAUTH2;
        this.tokenEndpoint = tokenEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        return this;
    }

    /**
     * Configures a custom WebClient for advanced authentication scenarios.
     *
     * @param webClient the pre-configured WebClient to use
     * @return this builder
     */
    public RegistryClientOptions customWebClient(WebClient webClient) {
        clearAuth();
        this.authType = AuthType.CUSTOM_WEBCLIENT;
        this.webClient = webClient;
        return this;
    }

    private void clearAuth() {
        this.authType = AuthType.ANONYMOUS;
        this.username = null;
        this.password = null;
        this.tokenEndpoint = null;
        this.clientId = null;
        this.clientSecret = null;
        this.scope = null;
        this.webClient = null;
    }

    /**
     * Configures retry functionality for HTTP requests.
     *
     * @param enabled        whether retry functionality is enabled
     * @param maxAttempts    the maximum number of retry attempts (must be > 0 if enabled)
     * @param initialDelayMs the initial delay between retry attempts in milliseconds (must be > 0 if enabled)
     * @return this builder
     */
    public RegistryClientOptions retry(boolean enabled, int maxAttempts, long initialDelayMs) {
        return retry(enabled, maxAttempts, initialDelayMs, 2, 10000);
    }

    /**
     * Configures retry functionality with exponential backoff for HTTP requests.
     *
     * @param enabled           whether retry functionality is enabled
     * @param maxAttempts       the maximum number of retry attempts (must be > 0 if enabled)
     * @param initialDelayMs    the initial delay between retry attempts in milliseconds (must be > 0 if enabled)
     * @param backoffMultiplier the multiplier for exponential backoff (must be > 1.0 if enabled)
     * @param maxDelayMs        the maximum delay between retries in milliseconds (must be > 0 if enabled)
     * @return this builder
     */
    public RegistryClientOptions retry(boolean enabled, int maxAttempts, long initialDelayMs,
                                       double backoffMultiplier, long maxDelayMs) {
        if (enabled && maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be greater than 0 when retry is enabled");
        }
        if (enabled && initialDelayMs <= 0) {
            throw new IllegalArgumentException("initialDelayMs must be greater than 0 when retry is enabled");
        }
        if (enabled && backoffMultiplier <= 1.0) {
            throw new IllegalArgumentException("backoffMultiplier must be greater than 1.0 when retry is enabled");
        }
        if (enabled && maxDelayMs <= 0) {
            throw new IllegalArgumentException("maxDelayMs must be greater than 0 when retry is enabled");
        }
        this.retryEnabled = enabled;
        this.maxRetryAttempts = maxAttempts;
        this.retryDelayMs = initialDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxRetryDelayMs = maxDelayMs;
        return this;
    }

    /**
     * Enables retry functionality with default settings (3 attempts, 1000ms initial delay, exponential backoff).
     *
     * @return this builder
     */
    public RegistryClientOptions retry() {
        return retry(true, 3, 250, 2, 10000);
    }

    /**
     * Disables retry functionality.
     *
     * @return this builder
     */
    public RegistryClientOptions disableRetry() {
        return retry(false, 0, 0, 1.0, 0);
    }

    /**
     * Sets a custom Vertx instance to use.
     *
     * @param vertx the Vertx instance to use
     * @return this builder
     */
    public RegistryClientOptions vertx(Vertx vertx) {
        this.vertx = vertx;
        return this;
    }

    /**
     * Configures SSL/TLS with a JKS (Java KeyStore) trust store.
     * This allows the client to trust certificates signed by custom certificate authorities
     * or self-signed certificates.
     *
     * @param path     the path to the JKS trust store file (can be a file system path or classpath resource prefixed with "classpath:")
     * @param password the password for the trust store
     * @return this builder
     * @throws IllegalArgumentException if path is null or empty
     */
    public RegistryClientOptions trustStoreJks(String path, String password) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Trust store path cannot be null or empty");
        }
        clearTrustStore();
        this.trustStoreType = TrustStoreType.JKS;
        this.trustStorePath = path;
        this.trustStorePassword = password;
        return this;
    }

    /**
     * Configures SSL/TLS with a PKCS#12 trust store.
     * This allows the client to trust certificates signed by custom certificate authorities
     * or self-signed certificates.
     *
     * @param path     the path to the PKCS#12 trust store file (can be a file system path or classpath resource prefixed with "classpath:")
     * @param password the password for the trust store
     * @return this builder
     * @throws IllegalArgumentException if path is null or empty
     */
    public RegistryClientOptions trustStorePkcs12(String path, String password) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Trust store path cannot be null or empty");
        }
        clearTrustStore();
        this.trustStoreType = TrustStoreType.PKCS12;
        this.trustStorePath = path;
        this.trustStorePassword = password;
        return this;
    }

    /**
     * Configures SSL/TLS with PEM certificate file(s).
     * This allows the client to trust certificates signed by custom certificate authorities
     * or self-signed certificates.
     *
     * @param certPaths one or more paths to PEM certificate files (can be file system paths or classpath resources prefixed with "classpath:")
     * @return this builder
     * @throws IllegalArgumentException if no certificate paths are provided
     */
    public RegistryClientOptions trustStorePem(String... certPaths) {
        if (certPaths == null || certPaths.length == 0) {
            throw new IllegalArgumentException("At least one PEM certificate path must be provided");
        }
        for (String path : certPaths) {
            if (path == null || path.trim().isEmpty()) {
                throw new IllegalArgumentException("Certificate path cannot be null or empty");
            }
        }
        clearTrustStore();
        this.trustStoreType = TrustStoreType.PEM;
        this.pemCertPaths = certPaths;
        return this;
    }

    /**
     * Configures SSL/TLS with PEM certificate content (as a string).
     * This allows the client to trust certificates without requiring file system access,
     * which is useful in containerized or cloud environments where certificates are
     * provided via environment variables or configuration systems.
     *
     * <p>The content should be in standard PEM format with BEGIN/END markers.
     * Multiple certificates can be provided in a single string, each delineated by
     * the standard PEM markers (-----BEGIN CERTIFICATE----- and -----END CERTIFICATE-----).</p>
     *
     * <p>Example:</p>
     * <pre>
     * -----BEGIN CERTIFICATE-----
     * MIIDXTCCAkWgAwIBAgIJAKJ0...
     * -----END CERTIFICATE-----
     * -----BEGIN CERTIFICATE-----
     * MIIEYzCCA0ugAwIBAgIQAHmP...
     * -----END CERTIFICATE-----
     * </pre>
     *
     * @param pemContent the PEM certificate content as a string (may contain multiple certificates)
     * @return this builder
     * @throws IllegalArgumentException if pemContent is null or empty
     */
    public RegistryClientOptions trustStorePemContent(String pemContent) {
        if (pemContent == null || pemContent.trim().isEmpty()) {
            throw new IllegalArgumentException("PEM certificate content cannot be null or empty");
        }
        if (!pemContent.contains("-----BEGIN CERTIFICATE-----")) {
            throw new IllegalArgumentException("PEM certificate content must contain at least one certificate with BEGIN CERTIFICATE marker");
        }
        clearTrustStore();
        this.trustStoreType = TrustStoreType.PEM;
        this.pemCertContent = pemContent;
        return this;
    }

    /**
     * Configures the client to trust all SSL/TLS certificates without validation.
     *
     * <p><strong>WARNING:</strong> This option should ONLY be used in development or testing environments.
     * Using this in production environments creates a serious security vulnerability as it disables
     * certificate validation, making the connection susceptible to man-in-the-middle attacks.</p>
     *
     * @param trustAll if true, all certificates will be trusted without validation
     * @return this builder
     */
    public RegistryClientOptions trustAll(boolean trustAll) {
        this.trustAll = trustAll;
        return this;
    }

    /**
     * Configures whether to verify the hostname in the server's certificate matches the hostname
     * being connected to. By default, hostname verification is enabled.
     *
     * <p><strong>WARNING:</strong> Disabling hostname verification reduces security and should only
     * be done in development or testing environments.</p>
     *
     * @param verifyHost if true, hostname verification will be performed (default); if false, it will be disabled
     * @return this builder
     */
    public RegistryClientOptions verifyHost(boolean verifyHost) {
        this.verifyHost = verifyHost;
        return this;
    }

    /**
     * Clears any configured trust store settings, reverting to default JVM trust store.
     *
     * @return this builder
     */
    public RegistryClientOptions clearTrustStore() {
        this.trustStoreType = TrustStoreType.NONE;
        this.trustStorePath = null;
        this.trustStorePassword = null;
        this.pemCertPaths = null;
        this.pemCertContent = null;
        this.trustAll = false;
        return this;
    }

    /**
     * Configures an HTTP/HTTPS proxy for registry connections.
     * This is useful in environments where direct internet access is restricted
     * and all outbound connections must go through a proxy server.
     *
     * <p>This proxy configuration will be used for both registry API calls and
     * OAuth token endpoint calls (if OAuth authentication is configured).</p>
     *
     * @param host the proxy server hostname or IP address
     * @param port the proxy server port number (typically 3128, 8080, or 8888)
     * @return this builder
     * @throws IllegalArgumentException if host is null/empty or port is invalid
     */
    public RegistryClientOptions proxy(String host, int port) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Proxy host cannot be null or empty");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Proxy port must be between 1 and 65535");
        }
        this.proxyHost = host;
        this.proxyPort = port;
        return this;
    }

    /**
     * Configures authentication credentials for the HTTP/HTTPS proxy.
     * This should be called after {@link #proxy(String, int)} if the proxy server
     * requires authentication.
     *
     * @param username the proxy authentication username
     * @param password the proxy authentication password
     * @return this builder
     * @throws IllegalArgumentException if username or password is null/empty
     */
    public RegistryClientOptions proxyAuth(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Proxy username cannot be null or empty");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Proxy password cannot be null or empty");
        }
        this.proxyUsername = username;
        this.proxyPassword = password;
        return this;
    }

    /**
     * Clears any configured proxy settings.
     *
     * @return this builder
     */
    public RegistryClientOptions clearProxy() {
        this.proxyHost = null;
        this.proxyPort = -1;
        this.proxyUsername = null;
        this.proxyPassword = null;
        return this;
    }
}
