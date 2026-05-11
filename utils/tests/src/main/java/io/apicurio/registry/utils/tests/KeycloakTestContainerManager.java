package io.apicurio.registry.utils.tests;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Test container manager for Keycloak.
 * Supports optional TLS/HTTPS configuration via init args.
 *
 * To enable TLS, pass "tls.enabled" = "true" in TestResourceEntry args:
 * <pre>
 * new TestResourceEntry(KeycloakTestContainerManager.class, Map.of("tls.enabled", "true"))
 * </pre>
 */
public class KeycloakTestContainerManager implements QuarkusTestResourceLifecycleManager {

    static final Logger LOGGER = LoggerFactory.getLogger(KeycloakTestContainerManager.class);
    private KeycloakContainer server;
    private boolean tlsEnabled = false;

    public static String ADMIN_CLIENT_ID = "admin-client";
    public static String DEVELOPER_CLIENT_ID = "developer-client";
    public static String DEVELOPER_2_CLIENT_ID = "developer-2-client";
    public static String READONLY_CLIENT_ID = "readonly-client";

    public static String NO_ROLE_CLIENT_ID = "no-role-client";
    public static String WRONG_CREDS_CLIENT_ID = "wrong-client";

    @Override
    public void init(Map<String, String> initArgs) {
        if (initArgs != null && "true".equalsIgnoreCase(initArgs.get("tls.enabled"))) {
            tlsEnabled = true;
            LOGGER.info("TLS/HTTPS enabled for Keycloak test container");
        }
    }

    @Override
    public Map<String, String> start() {
        KeycloakContainer container = new KeycloakContainer(
                DockerImageName.parse("quay.io/keycloak/keycloak").withTag("26.1.0").toString())
                .withNetwork(Network.SHARED)
                .withRealmImportFile("/realm.json");

        String trustStorePath = null;

        // Configure TLS if enabled
        if (tlsEnabled) {
            LOGGER.info("Configuring Keycloak with TLS enabled");
            URL keystoreUrl = getClass().getClassLoader().getResource("tls/keycloak-keystore.jks");
            if (keystoreUrl == null) {
                LOGGER.error("Keycloak keystore not found at tls/keycloak-keystore.jks");
                throw new RuntimeException("Keycloak keystore not found at tls/keycloak-keystore.jks");
            }

            String keystorePath;
            try {
                keystorePath = Paths.get(keystoreUrl.toURI()).toString();
            } catch (Exception e) {
                LOGGER.error("Failed to resolve keystore path: " + e.getMessage());
                throw new RuntimeException("Failed to resolve keystore path", e);
            }

            URL truststoreUrl = getClass().getClassLoader().getResource("tls/combined-truststore.jks");
            if (truststoreUrl == null) {
                LOGGER.error("Registry truststore not found at tls/combined-truststore.jks");
                throw new RuntimeException("Registry truststore not found at tls/combined-truststore.jks");
            }
            trustStorePath = truststoreUrl.getPath();

            LOGGER.info("Configuring Keycloak with HTTPS using keystore: {}", keystorePath);

            // Copy the keystore into the container and configure HTTPS
            container.withCopyFileToContainer(
                    MountableFile.forHostPath(keystorePath),
                    "/opt/keycloak/conf/server.keystore"
            )
            // Keycloak HTTPS configuration - using correct environment variable names
            .withEnv("KC_HTTPS_KEY_STORE_FILE", "/opt/keycloak/conf/server.keystore")
            .withEnv("KC_HTTPS_KEY_STORE_PASSWORD", "registrytest")
            .withEnv("KC_HTTPS_PORT", "8443")
            .withEnv("KC_HTTP_ENABLED", "true")  // Keep HTTP enabled for testcontainers health checks
            .withEnv("KC_HOSTNAME_STRICT", "false")
            .withEnv("KC_HOSTNAME_STRICT_HTTPS", "false");

            LOGGER.info("Keycloak TLS configuration:");
            LOGGER.info("  - Keystore: /opt/keycloak/conf/server.keystore");
            LOGGER.info("  - HTTPS Port: 8443");
            LOGGER.info("  - HTTP Enabled: true (for health checks)");

            // Override wait strategy for TLS mode - use log-based waiting
            // The default KeycloakContainer wait strategy expects HTTP to be available
            LOGGER.info("Setting log-based wait strategy for TLS mode");
            container.waitingFor(Wait.forLogMessage(".*Listening on:.*", 1));

            // Enable log forwarding for debugging in TLS mode
            LOGGER.info("Enabling container log forwarding for Keycloak container debugging");
            container.withLogConsumer(new Slf4jLogConsumer(LOGGER).withPrefix("KEYCLOAK"));
        }

        server = container;

        LOGGER.info("Starting Keycloak container...");
        try {
            server.start();
            LOGGER.info("Keycloak container started successfully");
            LOGGER.info("Container ID: {}", server.getContainerId());
            LOGGER.info("Container is running: {}", server.isRunning());
            LOGGER.info("Keycloak container is ready");
        } catch (Exception e) {
            LOGGER.error("Failed to start Keycloak container", e);
            LOGGER.error("Container logs:");
            if (server != null && server.isRunning()) {
                LOGGER.error(server.getLogs());
            }
            throw new RuntimeException("Failed to start Keycloak container", e);
        }

        Map<String, String> props = new HashMap<>();

        // Use HTTPS URL if TLS is enabled
        String authUrl;
        String tokenUrl;
        if (tlsEnabled) {
            String host = server.getHost();
            Integer httpsPort = server.getMappedPort(8443);
            LOGGER.info("Mapped HTTPS port 8443 -> {}", httpsPort);
            authUrl = "https://" + host + ":" + httpsPort + "/realms/registry";
            tokenUrl = authUrl + "/protocol/openid-connect/token/";
            LOGGER.info("Keycloak HTTPS Auth URL: {}", authUrl);
            LOGGER.info("Keycloak HTTPS Token URL: {}", tokenUrl);

            // Configure OIDC client to trust Keycloak's self-signed certificate
            // Use combined truststore that contains both Registry and Keycloak certificates
            props.put("quarkus.oidc.tls.trust-store-file", trustStorePath);
            props.put("quarkus.oidc.tls.trust-store-password", "registrytest");
            props.put("quarkus.oidc.tls.trust-store-file-type", "JKS");
            LOGGER.info("Registry TLS trust store path: {}", trustStorePath);
        } else {
            authUrl = server.getAuthServerUrl() + "/realms/registry";
            tokenUrl = authUrl + "/protocol/openid-connect/token/";
        }

        props.put("quarkus.oidc.auth-server-url", authUrl);
        props.put("quarkus.oidc.token-path", tokenUrl);
        props.put("quarkus.oidc.tenant-enabled", "true");
        props.put("apicurio.auth.role-based-authorization", "true");
        props.put("apicurio.auth.owner-only-authorization", "true");
        props.put("apicurio.auth.admin-override.enabled", "true");
        props.put("apicurio.authn.basic-client-credentials.enabled", "true");

        LOGGER.info("Registry properties: " + props.toString());
        return props;
    }

    @Override
    public synchronized void stop() {
        if (server != null) {
            server.stop();
            LOGGER.info("Keycloak was shut down");
            server = null;
        }
    }
}
