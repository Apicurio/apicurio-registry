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
 * Test container manager that starts Keycloak and Apicurio Registry for MCP authentication tests.
 * <p>
 * Uses the shared realm.json from apicurio-registry-utils-tests.
 */
public class AuthTestContainersManager implements QuarkusTestResourceLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(AuthTestContainersManager.class);

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.1.0";
    private static final String REGISTRY_IMAGE = "quay.io/apicurio/apicurio-registry:latest-release";

    public static final String ADMIN_CLIENT_ID = "admin-client";
    public static final String ADMIN_CLIENT_SECRET = "test1";
    public static final String READONLY_CLIENT_ID = "readonly-client";
    public static final String READONLY_CLIENT_SECRET = "test1";

    private KeycloakContainer keycloak;
    private GenericContainer<?> registry;
    private Network network;

    private static volatile String sharedTokenEndpoint;

    public static String sharedTokenEndpoint() {
        return sharedTokenEndpoint;
    }

    @Override
    public Map<String, String> start() {
        network = Network.newNetwork();

        keycloak = new KeycloakContainer(DockerImageName.parse(KEYCLOAK_IMAGE).toString())
                .withNetwork(network)
                .withNetworkAliases("keycloak")
                .withRealmImportFile("/realm.json")
                .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("KEYCLOAK"));

        log.info("Starting Keycloak container...");
        keycloak.start();
        log.info("Keycloak started at: {}", keycloak.getAuthServerUrl());

        String keycloakExternalRealmUrl = keycloak.getAuthServerUrl() + "/realms/registry";
        String keycloakInternalRealmUrl = "http://keycloak:8080/realms/registry";
        String tokenEndpoint = keycloakExternalRealmUrl + "/protocol/openid-connect/token";
        sharedTokenEndpoint = tokenEndpoint;

        registry = new GenericContainer<>(DockerImageName.parse(REGISTRY_IMAGE))
                .withNetwork(network)
                .withNetworkAliases("registry")
                .withExposedPorts(8080)
                .withEnv("QUARKUS_OIDC_AUTH_SERVER_URL", keycloakInternalRealmUrl)
                .withEnv("QUARKUS_OIDC_TOKEN_PATH", keycloakInternalRealmUrl + "/protocol/openid-connect/token")
                .withEnv("QUARKUS_OIDC_TOKEN_ISSUER", keycloakExternalRealmUrl)
                .withEnv("QUARKUS_OIDC_TENANT_ENABLED", "true")
                .withEnv("APICURIO_AUTH_ROLE_BASED_AUTHORIZATION", "true")
                .withEnv("APICURIO_AUTH_OWNER_ONLY_AUTHORIZATION", "true")
                .withEnv("APICURIO_AUTH_ADMIN_OVERRIDE_ENABLED", "true")
                .withEnv("APICURIO_AUTHN_BASIC_CLIENT_CREDENTIALS_ENABLED", "true")
                .waitingFor(Wait.forLogMessage(".*Installed features:.*", 1).withStartupTimeout(Duration.ofMinutes(3)))
                .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("REGISTRY"));

        log.info("Starting Registry container with authentication...");
        registry.start();

        String registryUrl = "http://" + registry.getHost() + ":" + registry.getMappedPort(8080);
        log.info("Registry started at: {}", registryUrl);

        Map<String, String> props = buildMcpProperties(registryUrl, tokenEndpoint, keycloakExternalRealmUrl);
        Map<String, String> safeProps = new HashMap<>(props);
        safeProps.replace("apicurio.mcp.auth.client-secret", "***");
        log.info("MCP test configuration: {}", safeProps);
        return props;
    }

    protected Map<String, String> buildMcpProperties(String registryUrl, String tokenEndpoint,
            String keycloakExternalRealmUrl) {
        Map<String, String> props = new HashMap<>();
        props.put("registry.url", registryUrl);
        props.put("apicurio.mcp.auth.enabled", "true");
        props.put("apicurio.mcp.auth.token-endpoint", tokenEndpoint);
        props.put("apicurio.mcp.auth.client-id", ADMIN_CLIENT_ID);
        props.put("apicurio.mcp.auth.client-secret", ADMIN_CLIENT_SECRET);
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
