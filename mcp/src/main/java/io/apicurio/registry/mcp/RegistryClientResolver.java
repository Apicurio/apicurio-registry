package io.apicurio.registry.mcp;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.HttpAdapterType;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Resolves the Registry REST client for the current request.
 * <p>
 * In HTTP mode with token forwarding, uses the inbound caller's bearer token.
 * Otherwise uses a singleton client configured with OAuth2 client credentials or anonymous access.
 */
@ApplicationScoped
public class RegistryClientResolver {

    private static final Logger log = LoggerFactory.getLogger(RegistryClientResolver.class);

    @ConfigProperty(name = "registry.url", defaultValue = "localhost:8080")
    String rawBaseUrl;

    @Inject
    McpConfig config;

    @Inject
    Instance<SecurityIdentity> securityIdentity;

    @Inject
    Instance<JsonWebToken> jwt;

    private RegistryClient fallbackClient;

    @PostConstruct
    void init() {
        if (needsFallbackClient()) {
            fallbackClient = createClient(buildBaseOptions());
            if (!config.http().enabled() || config.auth().enabled()) {
                var info = fallbackClient.system().info().get();
                log.info("Successfully connected to Apicurio Registry version {} at {}", info.getVersion(),
                        rawBaseUrl);
            }
        } else {
            log.info("Registry client will authenticate using inbound bearer token forwarding");
        }
    }

    /**
     * Returns a Registry client for the current context.
     */
    @ActivateRequestContext
    public RegistryClient getClient() {
        String bearerToken = resolveInboundBearerToken();
        if (bearerToken != null) {
            var options = buildBaseOptions().bearerToken(bearerToken);
            return RegistryClientFactory.create(options);
        }
        if (fallbackClient == null) {
            boolean authenticated = securityIdentity.isResolvable()
                    && securityIdentity.get() != null
                    && !securityIdentity.get().isAnonymous();
            if (authenticated) {
                throw new IllegalStateException(
                        "No Registry client available: HTTP token forwarding is enabled but no bearer access "
                                + "token was found on the authenticated request. Ensure the MCP client sends "
                                + "'Authorization: Bearer <access_token>' on every /mcp request (complete "
                                + "OAuth login so the access token is sent).");
            }
            throw new IllegalStateException(
                    "No Registry client available: HTTP token forwarding is enabled but no bearer token "
                            + "was provided, and no fallback client credentials are configured");
        }
        return fallbackClient;
    }

    boolean hasFallbackClient() {
        return fallbackClient != null;
    }

    private boolean needsFallbackClient() {
        if (!config.http().enabled()) {
            return true;
        }
        return config.auth().enabled() || !config.http().forwardToken();
    }

    private String resolveInboundBearerToken() {
        if (!config.http().enabled() || !config.http().forwardToken()) {
            return null;
        }
        String fromIdentity = resolveTokenFromSecurityIdentity();
        if (fromIdentity != null) {
            return fromIdentity;
        }
        if (!jwt.isResolvable()) {
            return null;
        }
        JsonWebToken token = jwt.get();
        if (token == null || token.getRawToken() == null || token.getRawToken().isBlank()) {
            return null;
        }
        return token.getRawToken();
    }

    private String resolveTokenFromSecurityIdentity() {
        if (!securityIdentity.isResolvable()) {
            return null;
        }
        SecurityIdentity identity = securityIdentity.get();
        if (identity == null || identity.isAnonymous()) {
            return null;
        }
        AccessTokenCredential credential = identity.getCredential(AccessTokenCredential.class);
        if (credential == null) {
            return null;
        }
        String token = credential.getToken();
        if (token == null || token.isBlank()) {
            return null;
        }
        return token;
    }

    private RegistryClientOptions buildBaseOptions() {
        var options = RegistryClientOptions.create(rawBaseUrl)
                .httpAdapter(HttpAdapterType.JDK)
                .retry();
        configureTls(options);
        configureClientCredentials(options);
        return options;
    }

    private void configureClientCredentials(RegistryClientOptions options) {
        var auth = config.auth();
        if (!auth.enabled()) {
            log.info("Registry client authentication is disabled");
            return;
        }

        if (auth.tokenEndpoint().isEmpty() || auth.clientId().isEmpty() || auth.clientSecret().isEmpty()) {
            throw new IllegalStateException(
                    "OAuth2 authentication requires 'apicurio.mcp.auth.token-endpoint', "
                            + "'apicurio.mcp.auth.client-id', and 'apicurio.mcp.auth.client-secret' to be configured");
        }

        if (auth.scope().isPresent()) {
            options.oauth2(auth.tokenEndpoint().get(), auth.clientId().get(), auth.clientSecret().get(),
                    auth.scope().get());
        } else {
            options.oauth2(auth.tokenEndpoint().get(), auth.clientId().get(), auth.clientSecret().get());
        }
        log.info("Configured OAuth2 client credentials with token endpoint: {}", auth.tokenEndpoint().get());
    }

    private void configureTls(RegistryClientOptions options) {
        var tls = config.tls();

        if (tls.trustAll()) {
            log.warn("TLS trust-all is enabled. This should only be used in development environments.");
            options.trustAll(true);
        }

        if (!tls.verifyHost()) {
            log.warn("TLS hostname verification is disabled. This reduces security.");
            options.verifyHost(false);
        }

        var truststore = tls.truststore();
        if (truststore.type().isPresent() && truststore.path().isPresent()) {
            String type = truststore.type().get().toUpperCase(Locale.ROOT);
            String path = truststore.path().get();
            String password = truststore.password().orElse(null);

            switch (type) {
                case "JKS":
                    options.trustStoreJks(path, password);
                    log.info("Configured JKS trust store: {}", path);
                    break;
                case "PKCS12":
                case "P12":
                    options.trustStorePkcs12(path, password);
                    log.info("Configured PKCS12 trust store: {}", path);
                    break;
                case "PEM":
                    options.trustStorePem(path);
                    log.info("Configured PEM trust store: {}", path);
                    break;
                default:
                    throw new IllegalStateException("Unsupported trust store type: " + type
                            + ". Supported types: JKS, PKCS12, PEM");
            }
        }

        var keystore = tls.keystore();
        if (keystore.type().isPresent() && keystore.path().isPresent()) {
            String type = keystore.type().get().toUpperCase(Locale.ROOT);
            String path = keystore.path().get();
            String password = keystore.password().orElse(null);

            switch (type) {
                case "JKS":
                    options.keystoreJks(path, password);
                    log.info("Configured JKS key store for mTLS: {}", path);
                    break;
                case "PKCS12":
                case "P12":
                    options.keystorePkcs12(path, password);
                    log.info("Configured PKCS12 key store for mTLS: {}", path);
                    break;
                default:
                    throw new IllegalStateException("Unsupported key store type: " + type
                            + ". Supported types: JKS, PKCS12");
            }
        }
    }

    private RegistryClient createClient(RegistryClientOptions options) {
        return RegistryClientFactory.create(options);
    }
}
