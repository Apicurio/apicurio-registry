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
 * for testing the MCP server with basic authentication.
 * <p>
 * Basic authentication uses client credentials (client-id:client-secret) passed as
 * username:password in the Authorization header. The Registry validates these against Keycloak.
 */
public class BasicAuthTestContainersManager implements QuarkusTestResourceLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(BasicAuthTestContainersManager.class);

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

        // Start Registry with basic client credentials authentication enabled
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

        log.info("Starting Registry container with basic auth support...");
        registry.start();

        String registryHost = registry.getHost();
        Integer registryPort = registry.getMappedPort(8080);
        String registryUrl = "http://" + registryHost + ":" + registryPort;

        log.info("Registry started at: {}", registryUrl);

        Map<String, String> props = new HashMap<>();
        props.put("registry.url", registryUrl);
        props.put("apicurio.mcp.auth.type", "basic");
        props.put("apicurio.mcp.auth.basic.username", ADMIN_CLIENT_ID);
        props.put("apicurio.mcp.auth.basic.password", ADMIN_CLIENT_SECRET);

        log.info("MCP Test configuration (Basic Auth): {}", props);
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
