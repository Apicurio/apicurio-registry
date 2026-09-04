package io.apicurio.registry.utils.tests;

import com.nimbusds.jwt.SignedJWT;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import no.nav.security.mock.oauth2.MockOAuth2Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight OIDC provider for tests, backed by <a href="https://github.com/navikt/mock-oauth2-server">
 * NAV's mock-oauth2-server</a>. Runs in-JVM (no container), making it much faster than
 * {@link KeycloakTestContainerManager} for tests that only need valid JWTs against a real
 * OIDC endpoint flow (discovery, token issuance, JWKS validation).
 *
 * <p>Use it through {@link MockOAuth2AuthTestProfile}, or annotate a test class directly:
 * <pre>
 * &#64;QuarkusTestResource(MockOAuth2TestResource.class)
 * </pre>
 *
 * <p>Supported init args (via {@code TestResourceEntry} args):
 * <ul>
 *   <li>{@code issuer.id} - OIDC issuer identifier appended to the base URL
 *       (default: {@value #DEFAULT_ISSUER_ID})</li>
 *   <li>{@code token.expiry} - lifetime in seconds of tokens issued through
 *       {@link #issueToken(String, Map)} (default: 3600)</li>
 * </ul>
 *
 * @see KeycloakTestContainerManager for full-realm auth testing
 */
public class MockOAuth2TestResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockOAuth2TestResource.class);

    public static final String DEFAULT_ISSUER_ID = "default";
    public static final String DEFAULT_CLIENT_ID = "test-client";
    public static final String DEFAULT_CLIENT_SECRET = "test-secret";

    private MockOAuth2Server server;
    private String issuerId = DEFAULT_ISSUER_ID;
    private long tokenExpirySeconds = 3600;

    @Override
    public void init(Map<String, String> initArgs) {
        if (initArgs != null && !initArgs.isEmpty()) {
            String issuerArg = initArgs.get("issuer.id");
            if (issuerArg != null && !issuerArg.isBlank()) {
                issuerId = issuerArg;
            }
            String expiryArg = initArgs.get("token.expiry");
            if (expiryArg != null && !expiryArg.isBlank()) {
                tokenExpirySeconds = Long.parseLong(expiryArg.trim());
            }
        }
        LOGGER.info("Initialized MockOAuth2TestResource [issuerId={}, tokenExpirySeconds={}]", issuerId, tokenExpirySeconds);
    }

    @Override
    public Map<String, String> start() {
        server = new MockOAuth2Server();
        server.start();
        LOGGER.info("Mock OAuth2 server started on port {}", server.url(issuerId).port());

        Map<String, String> props = new HashMap<>();
        props.put("quarkus.oidc.auth-server-url", authServerUrl());
        props.put("quarkus.oidc.token-path", tokenEndpointUrl());
        props.put("quarkus.oidc.client-id", DEFAULT_CLIENT_ID);
        props.put("quarkus.oidc.credentials.secret", DEFAULT_CLIENT_SECRET);
        props.put("quarkus.oidc.tenant-enabled", "true");

        LOGGER.info("Registry OIDC properties: {}", props);
        return props;
    }

    @Override
    public synchronized void stop() {
        if (server != null) {
            server.shutdown();
            LOGGER.info("Mock OAuth2 server was shut down");
            server = null;
        }
    }

    /**
     * Injects the running {@link MockOAuth2Server} into any field of that type on the test
     * instance, so tests can issue custom tokens (e.g. with specific claims).
     */
    @Override
    public void inject(Object testInstance) {
        Class<?> clazz = testInstance.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType().equals(MockOAuth2Server.class)) {
                    try {
                        field.setAccessible(true);
                        field.set(testInstance, getServer());
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Failed to inject MockOAuth2Server into " + field, e);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Issues a signed JWT for the configured issuer with the given subject and extra claims,
     * honoring the {@code token.expiry} init arg. Use for custom token scenarios; regular
     * tests can simply authenticate against {@link #tokenEndpointUrl()} with any client credentials.
     */
    public SignedJWT issueToken(String subject, Map<String, Object> claims) {
        return getServer().issueToken(issuerId, subject, DEFAULT_CLIENT_ID, claims, tokenExpirySeconds);
    }

    public synchronized MockOAuth2Server getServer() {
        if (server == null) {
            throw new IllegalStateException("MockOAuth2Server is not running - was start() called?");
        }
        return server;
    }

    /** The OIDC issuer base URL, e.g. {@code http://localhost:{port}/{issuerId}}. */
    public String authServerUrl() {
        String url = getServer().url(issuerId).toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** The OAuth2 token endpoint, usable with SDK clients ({@code RegistryClientOptions.oauth2(...)}). */
    public String tokenEndpointUrl() {
        return getServer().tokenEndpointUrl(issuerId).toString();
    }

    /** The OpenID Connect discovery document URL. */
    public String wellKnownUrl() {
        return getServer().wellKnownUrl(issuerId).toString();
    }

    /** The JWKS endpoint used by Quarkus OIDC to verify token signatures. */
    public String jwksUrl() {
        return getServer().jwksUrl(issuerId).toString();
    }
}
