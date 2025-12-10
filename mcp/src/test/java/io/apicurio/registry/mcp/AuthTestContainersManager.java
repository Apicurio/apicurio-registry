package io.apicurio.registry.mcp;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Test container manager that starts both Keycloak and Apicurio Registry containers
 * for testing the MCP server authentication functionality.
 */
public class AuthTestContainersManager implements QuarkusTestResourceLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(AuthTestContainersManager.class);

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.1.0";
    private static final String REGISTRY_IMAGE = "quay.io/apicurio/apicurio-registry:latest-release";

    public static final String ADMIN_CLIENT_ID = "admin-client";
    public static final String ADMIN_CLIENT_SECRET = "test1";

    private KeycloakContainer keycloak;
    private GenericContainer<?> registry;
    private Network network;

    @Override
    public Map<String, String> start() {
        network = Network.newNetwork();

        // Start Keycloak first
        keycloak = new KeycloakContainer(DockerImageName.parse(KEYCLOAK_IMAGE).toString())
                .withNetwork(network)
                .withNetworkAliases("keycloak")
                .withRealmImportFile("/realm.json")
                .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("KEYCLOAK"));

        log.info("Starting Keycloak container...");
        keycloak.start();
        log.info("Keycloak started at: {}", keycloak.getAuthServerUrl());

        String keycloakInternalUrl = "http://keycloak:8080";
        String tokenEndpoint = keycloakInternalUrl + "/realms/registry/protocol/openid-connect/token";

        // Start Registry with authentication enabled
        registry = new GenericContainer<>(DockerImageName.parse(REGISTRY_IMAGE))
                .withNetwork(network)
                .withNetworkAliases("registry")
                .withExposedPorts(8080)
                .withEnv("QUARKUS_OIDC_AUTH_SERVER_URL", keycloakInternalUrl + "/realms/registry")
                .withEnv("QUARKUS_OIDC_TOKEN_PATH", tokenEndpoint)
                .withEnv("QUARKUS_OIDC_TENANT_ENABLED", "true")
                .withEnv("APICURIO_AUTH_ROLE_BASED_AUTHORIZATION", "true")
                .withEnv("APICURIO_AUTH_OWNER_ONLY_AUTHORIZATION", "true")
                .withEnv("APICURIO_AUTH_ADMIN_OVERRIDE_ENABLED", "true")
                .withEnv("APICURIO_AUTHN_BASIC_CLIENT_CREDENTIALS_ENABLED", "true")
                .waitingFor(Wait.forLogMessage(".*Installed features:.*", 1).withStartupTimeout(Duration.ofMinutes(3)))
                .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("REGISTRY"));

        log.info("Starting Registry container with authentication...");
        registry.start();

        String registryHost = registry.getHost();
        Integer registryPort = registry.getMappedPort(8080);
        String registryUrl = "http://" + registryHost + ":" + registryPort;

        log.info("Registry started at: {}", registryUrl);

        // Build the token endpoint URL using the external Keycloak URL
        String externalTokenEndpoint = keycloak.getAuthServerUrl() + "/realms/registry/protocol/openid-connect/token";

        Map<String, String> props = new HashMap<>();
        props.put("registry.url", registryUrl);
        props.put("apicurio.mcp.auth.type", "oauth2");
        props.put("apicurio.mcp.auth.oauth2.token-endpoint", externalTokenEndpoint);
        props.put("apicurio.mcp.auth.oauth2.client-id", ADMIN_CLIENT_ID);
        props.put("apicurio.mcp.auth.oauth2.client-secret", ADMIN_CLIENT_SECRET);

        log.info("MCP Test configuration: {}", props);
        return props;
    }

    @Override
    public void stop() {
        if (registry != null) {
            log.info("Stopping Registry container...");
            registry.stop();
        }
        if (keycloak != null) {
            log.info("Stopping Keycloak container...");
            keycloak.stop();
        }
        if (network != null) {
            network.close();
        }
    }
}
