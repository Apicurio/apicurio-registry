package io.apicurio.registry.systemtest.framework;

import io.apicurio.registry.systemtest.executor.Exec;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.ResourceManager;
import io.apicurio.registry.systemtest.registryinfra.resources.RouteResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.ServiceResourceType;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

import java.nio.file.Paths;

public class KeycloakUtils {
    private static final Logger LOGGER = LoggerUtils.getLogger();

    private static String getKeycloakFilePath(String filename) {
        return Paths.get(Environment.TESTSUITE_DIRECTORY, "kubefiles", "keycloak", filename).toString();
    }

    public static void deployKeycloak(ExtensionContext testContext, String namespace) {
        LOGGER.info("Deploying Keycloak...");

        ResourceManager manager = ResourceManager.getInstance();

        // Deploy Keycloak server
        Exec.executeAndCheck(
                "oc",
                "apply",
                "-n", namespace,
                "-f", getKeycloakFilePath("keycloak.yaml")
        );
        // TODO: Add Keycloak server cleanup

        // Wait for Keycloak server to be ready
        ResourceUtils.waitStatefulSetReady(namespace, "keycloak");

        // Create Keycloak HTTP Service and do not wait for its readiness
        manager.createResource(testContext, false, ServiceResourceType.getDefaultKeycloakHttp(namespace));

        // Create Keycloak Route and wait for its readiness
        manager.createResource(testContext, true, RouteResourceType.getDefaultKeycloak(namespace));

        // Log Keycloak URL
        LOGGER.info("Keycloak URL: {}", getDefaultKeycloakURL(namespace));

        // Create Keycloak Realm
        Exec.executeAndCheck(
                "oc",
                "apply",
                "-n", namespace,
                "-f", getKeycloakFilePath("keycloak-realm.yaml")
        );
        // TODO: Add Keycloak Realm cleanup

        LOGGER.info("Keycloak should be deployed.");
    }

    public static void removeKeycloak(String namespace) {
        LOGGER.info("Removing Keycloak...");

        Exec.executeAndCheck(
                "oc",
                "delete",
                "-n", namespace,
                "-f", getKeycloakFilePath("keycloak-realm.yaml")
        );

        Exec.executeAndCheck(
                "oc",
                "delete",
                "-n", namespace,
                "-f", getKeycloakFilePath("keycloak.yaml")
        );

        LOGGER.info("Keycloak should be removed.");
    }

    public static String getKeycloakURL(String namespace, String name) {
        return "http://" + Kubernetes.getRouteHost(namespace, name);
    }

    public static String getDefaultKeycloakURL(String namespace) {
        return getKeycloakURL(namespace, Environment.KEYCLOAK_HTTP_SERVICE_NAME);
    }
}
