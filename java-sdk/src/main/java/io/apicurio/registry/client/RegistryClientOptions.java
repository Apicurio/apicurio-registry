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

    public static final RegistryClientOptions create() {
        return new RegistryClientOptions();
    }

    public static final RegistryClientOptions create(String registryUrl) {
        return new RegistryClientOptions().registryUrl(registryUrl);
    }

    public static final RegistryClientOptions create(String registryUrl, Vertx vertx) {
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

    private String registryUrl;
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
    
    private RegistryClientOptions() {
    }

    public String getRegistryUrl() {
        return registryUrl;
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

    /**
     * Sets the registry URL.
     *
     * @param registryUrl the base URL of the registry API (e.g., "http://localhost:8080/apis/registry/v3")
     * @return this builder
     */
    public RegistryClientOptions registryUrl(String registryUrl) {
        this.registryUrl = registryUrl;
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
     * @param clientId the OAuth2 client ID
     * @param clientSecret the OAuth2 client secret
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
     * @param clientId the OAuth2 client ID
     * @param clientSecret the OAuth2 client secret
     * @param scope the OAuth2 scope (optional, can be null)
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
     * Sets a custom Vertx instance to use.
     *
     * @param vertx the Vertx instance to use
     * @return this builder
     */
    public RegistryClientOptions vertx(Vertx vertx) {
        this.vertx = vertx;
        return this;
    }
}
