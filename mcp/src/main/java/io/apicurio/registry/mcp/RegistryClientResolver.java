package io.apicurio.registry.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
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

    private static final ObjectMapper JWT_PAYLOAD_MAPPER = new ObjectMapper();

    @ConfigProperty(name = "registry.url", defaultValue = "localhost:8080")
    String rawBaseUrl;

    @Inject
    McpConfig config;

    @Inject
    Instance<SecurityIdentity> securityIdentity;

    @Inject
    Instance<JsonWebToken> jwt;

    private RegistryClient fallbackClient;

    /**
     * Cached base options (URL, HTTP adapter, retry, TLS) built once at startup.
     * Per-request callers must {@link #copyTransportOptions()} before applying a bearer
     * token so concurrent requests do not mutate this shared instance.
     */
    private RegistryClientOptions transportOptions;

    @PostConstruct
    void init() {
        transportOptions = buildTransportOptions();
        if (needsFallbackClient()) {
            fallbackClient = createClient(buildFallbackOptions());
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
            // Reuse cached transport settings (URL/TLS/adapter/retry); only the bearer token
            // is applied per request on a fresh options instance.
            var options = copyTransportOptions().bearerToken(bearerToken);
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

    boolean needsFallbackClient() {
        if (!config.http().enabled()) {
            return true;
        }
        return config.auth().enabled() || !config.http().forwardToken();
    }

    String resolveInboundBearerToken() {
        if (!config.http().enabled() || !config.http().forwardToken()) {
            return null;
        }
        String fromIdentity = resolveTokenFromSecurityIdentity();
        if (fromIdentity != null) {
            assertBearerTokenNotExpired(fromIdentity, resolveJsonWebToken());
            return fromIdentity;
        }
        JsonWebToken token = resolveJsonWebToken();
        if (token == null || token.getRawToken() == null || token.getRawToken().isBlank()) {
            return null;
        }
        assertBearerTokenNotExpired(token.getRawToken(), token);
        return token.getRawToken();
    }

    private JsonWebToken resolveJsonWebToken() {
        if (!jwt.isResolvable()) {
            return null;
        }
        return jwt.get();
    }

    /**
     * Rejects expired bearer tokens before forwarding them to Registry, so callers get a clear
     * re-auth message instead of an opaque Registry 401.
     */
    void assertBearerTokenNotExpired(String bearerToken, JsonWebToken jwtToken) {
        Long expirationEpochSeconds = null;
        if (jwtToken != null) {
            long exp = jwtToken.getExpirationTime();
            if (exp > 0) {
                expirationEpochSeconds = exp;
            }
        }
        if (expirationEpochSeconds == null) {
            expirationEpochSeconds = parseJwtExpirationEpochSeconds(bearerToken);
        }
        if (expirationEpochSeconds != null
                && Instant.now().getEpochSecond() >= expirationEpochSeconds) {
            throw new IllegalStateException(
                    "Bearer access token has expired. Re-authenticate and retry the request "
                            + "with a fresh access token.");
        }
    }

    /**
     * Best-effort {@code exp} claim read for JWTs when {@link JsonWebToken} is unavailable.
     * Opaque tokens return {@code null} (no proactive check).
     */
    static Long parseJwtExpirationEpochSeconds(String bearerToken) {
        if (bearerToken == null) {
            return null;
        }
        String[] parts = bearerToken.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = JWT_PAYLOAD_MAPPER.readTree(
                    new String(payloadBytes, StandardCharsets.UTF_8));
            JsonNode exp = payload.get("exp");
            if (exp == null || !exp.canConvertToLong()) {
                return null;
            }
            return exp.longValue();
        } catch (Exception ignored) {
            return null;
        }
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

    private RegistryClientOptions buildTransportOptions() {
        var options = RegistryClientOptions.create(rawBaseUrl)
                .httpAdapter(HttpAdapterType.JDK)
                .retry();
        configureTls(options);
        return options;
    }

    private RegistryClientOptions buildFallbackOptions() {
        var options = copyTransportOptions();
        configureClientCredentials(options);
        return options;
    }

    /**
     * Builds a new options instance with only the cached transport settings (URL, adapter,
     * retry, TLS). Auth is intentionally omitted so per-request bearer tokens (or fallback
     * client credentials) can be applied without mutating {@link #transportOptions}.
     */
    private RegistryClientOptions copyTransportOptions() {
        RegistryClientOptions options = transportOptions.getNormalizeRegistryUrl()
                ? RegistryClientOptions.create(transportOptions.getRegistryUrl())
                : RegistryClientOptions.create().rawRegistryUrl(transportOptions.getRegistryUrl());
        options.httpAdapter(transportOptions.getHttpAdapterType());
        if (transportOptions.isRetryEnabled()) {
            options.retry(true, transportOptions.getMaxRetryAttempts(), transportOptions.getRetryDelayMs(),
                    transportOptions.getBackoffMultiplier(), transportOptions.getMaxRetryDelayMs());
        } else {
            options.disableRetry();
        }
        copyTransportTls(transportOptions, options);
        return options;
    }

    private static void copyTransportTls(RegistryClientOptions from, RegistryClientOptions to) {
        if (from.isTrustAll()) {
            to.trustAll(true);
        }
        if (!from.isVerifyHost()) {
            to.verifyHost(false);
        }

        switch (from.getTrustStoreType()) {
            case JKS:
                to.trustStoreJks(from.getTrustStorePath(), from.getTrustStorePassword());
                break;
            case PKCS12:
                to.trustStorePkcs12(from.getTrustStorePath(), from.getTrustStorePassword());
                break;
            case PEM:
                if (from.getPemCertContent() != null) {
                    to.trustStorePemContent(from.getPemCertContent());
                } else if (from.getPemCertPaths() != null) {
                    to.trustStorePem(from.getPemCertPaths());
                }
                break;
            case NONE:
            default:
                break;
        }

        switch (from.getKeyStoreType()) {
            case JKS:
                to.keystoreJks(from.getKeyStorePath(), from.getKeyStorePassword());
                break;
            case PKCS12:
                to.keystorePkcs12(from.getKeyStorePath(), from.getKeyStorePassword());
                break;
            case PEM:
                if (from.getPemClientCertContent() != null) {
                    to.keystorePemContent(from.getPemClientCertContent(), from.getPemClientKeyContent());
                } else if (from.getPemClientCertPath() != null) {
                    to.keystorePem(from.getPemClientCertPath(), from.getPemClientKeyPath());
                }
                break;
            case NONE:
            default:
                break;
        }
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
