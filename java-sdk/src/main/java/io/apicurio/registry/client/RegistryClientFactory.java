package io.apicurio.registry.client;

import com.microsoft.kiota.RequestAdapter;
import io.apicurio.registry.client.auth.VertXAuthFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

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
                adapter = createAnonymousClient(vertxToUse);
                break;
            case BASIC:
                adapter = createBasicAuthClient(options.getUsername(), options.getPassword(), vertxToUse);
                break;
            case OAUTH2:
                adapter = createOAuth2Client(options.getTokenEndpoint(), options.getClientId(),
                        options.getClientSecret(), options.getScope(), vertxToUse);
                break;
            case CUSTOM_WEBCLIENT:
                adapter = createCustomWebClientClient(options.getWebClient());
                break;
            default:
                throw new IllegalArgumentException("Unsupported authentication type: " + options.getAuthType());
        }
        adapter.setBaseUrl(options.getRegistryUrl());

        return new RegistryClient(adapter);
    }

    // Private implementation methods
    
    private static RequestAdapter createAnonymousClient(Vertx vertx) {
        RequestAdapter adapter = new VertXRequestAdapter(vertx);
        return adapter;
    }
    
    private static RequestAdapter createBasicAuthClient(String username, String password, Vertx vertx) {
        WebClient webClient = VertXAuthFactory.buildSimpleAuthWebClient(vertx, username, password);
        RequestAdapter adapter = new VertXRequestAdapter(webClient);
        return adapter;
    }
    
    private static RequestAdapter createOAuth2Client(String tokenEndpoint,
                                                    String clientId, String clientSecret, String scope, Vertx vertx) {
        WebClient webClient = VertXAuthFactory.buildOIDCWebClient(vertx, tokenEndpoint, clientId, clientSecret, scope);
        RequestAdapter adapter = new VertXRequestAdapter(webClient);
        return adapter;
    }
    
    private static RequestAdapter createCustomWebClientClient(WebClient webClient) {
        if (webClient == null) {
            throw new IllegalArgumentException("WebClient cannot be null");
        }

        RequestAdapter adapter = new VertXRequestAdapter(webClient);
        return adapter;
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
