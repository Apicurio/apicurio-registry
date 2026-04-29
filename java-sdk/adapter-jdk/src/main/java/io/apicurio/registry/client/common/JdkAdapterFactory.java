package io.apicurio.registry.client.common;

import com.microsoft.kiota.RequestAdapter;
import com.microsoft.kiota.RequestInformation;
import io.apicurio.registry.client.common.auth.JdkAuthFactory;
import io.apicurio.registry.client.common.ssl.JdkSslContextFactory;
import io.kiota.http.jdk.JDKRequestAdapter;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * Factory class for creating JDK HTTP-based RequestAdapter instances.
 *
 * <p>This class is isolated from the main RegistryClientRequestAdapterFactory to ensure
 * that JDK HTTP adapter classes (kiota-http-jdk) are only loaded when the JDK adapter
 * is explicitly selected. This prevents NoClassDefFoundError when using the Vert.x adapter
 * without kiota-http-jdk on the classpath, and enables GraalVM native image builds.</p>
 */
public final class JdkAdapterFactory {

    private JdkAdapterFactory() {
        // Prevent instantiation
    }

    /**
     * Creates a JDK HTTP-based RequestAdapter configured with authentication, SSL/TLS,
     * and other settings from the provided options.
     *
     * @param options the configuration options
     * @return a fully configured JDK RequestAdapter
     */
    public static RequestAdapter createAdapter(RegistryClientOptions options) {
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
        private static final String AUTHORIZATION_HEADER = "Authorization";
        private final String authorizationHeader;

        public JdkAuthenticatedRequestAdapter(HttpClient httpClient, String authorizationHeader) {
            super(httpClient);
            this.authorizationHeader = authorizationHeader;
        }

        @Override
        protected HttpRequest getRequestFromRequestInformation(RequestInformation requestInfo) {
            requestInfo.headers.tryAdd(AUTHORIZATION_HEADER, authorizationHeader);
            return super.getRequestFromRequestInformation(requestInfo);
        }
    }

    /**
     * JDK RequestAdapter wrapper that handles OAuth2 token injection.
     * Fetches and caches tokens, automatically refreshing before expiry.
     */
    private static class JdkOAuth2RequestAdapter extends JDKRequestAdapter {
        private static final String AUTHORIZATION_HEADER = "Authorization";
        private final JdkAuthFactory.TokenProvider tokenProvider;

        public JdkOAuth2RequestAdapter(HttpClient httpClient, JdkAuthFactory.TokenProvider tokenProvider) {
            super(httpClient);
            this.tokenProvider = tokenProvider;
        }

        @Override
        protected HttpRequest getRequestFromRequestInformation(RequestInformation requestInfo) {
            try {
                String token = tokenProvider.getToken();
                requestInfo.headers.tryAdd(AUTHORIZATION_HEADER, "Bearer " + token);
            } catch (IOException e) {
                throw new RuntimeException("Failed to obtain OAuth2 token", e);
            }
            return super.getRequestFromRequestInformation(requestInfo);
        }
    }
}
