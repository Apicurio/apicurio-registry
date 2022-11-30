package io.apicurio.registry.systemtests;

import io.apicur.registry.v1.ApicurioRegistry;
import io.fabric8.junit.jupiter.api.KubernetesTest;
import io.fabric8.junit.jupiter.api.LoadKubernetesManifests;
import io.fabric8.openshift.client.OpenShiftClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.systemtests.Utils.findRegistryOperatorDeployment;
import static io.apicurio.registry.systemtests.Utils.isDeploymentReady;
import static io.apicurio.registry.systemtests.Utils.waitDeploymentReady;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KubernetesTest
@LoadKubernetesManifests({
        // Keycloak IAM
        "/keycloak/00_service.yaml", // Service
        "/keycloak/01_realm.yaml", // Keycloak realm as realm.json in ConfigMap
        "/keycloak/02_deployment.yaml", // Deployment of Keycloak itself
        // PostgreSQL database resources
        "/sql/00_service.yaml", // Service
        "/sql/01_deployment.yaml", // Deployment
        // Apicurio Registry operator
        "/apicurio/00_operator_group.yaml", // Operator group for Apicurio Registry operator
        "/apicurio/01_subscription.yaml", // Apicurio Registry operator subscription
        // Apicurio Registry instance
        "/apicurio/02_registry_sql_keycloak.yaml" // Apicurio Registry instance with PostgreSQL storage and Keycloak IAM
})
public class SqlKeycloak extends TestBase {
    /** {@link OpenShiftClient} instance for tests. */
    private OpenShiftClient client;

    /**
     * Performs actions that should be done before each test.
     */
    @BeforeEach
    public void beforeEach() {
        // Log information about current action
        System.out.println("Before each.");

        // Wait for readiness of Keycloak deployment
        assertTrue(waitDeploymentReady(client, Constants.KEYCLOAK_NAME));

        // Wait for readiness of PostgreSQL database deployment
        assertTrue(waitDeploymentReady(client, Constants.POSTGRESQL_NAME));

        // Wait for readiness of Apicurio Registry operator deployment
        assertTrue(waitDeploymentReady(client, findRegistryOperatorDeployment(client).getMetadata().getName()));
    }

    /**
     * Tests that {@link ApicurioRegistry} with PostgreSQL database storage and Keycloak IAM becomes ready.
     */
    @Test
    public void testDeploy() {
        // Log information about current action
        System.out.println("### testDeploy test ###");

        // Wait for readiness of Apicurio Registry instance with PostgreSQL database storage deployment
        assertTrue(waitDeploymentReady(client, Constants.REGISTRY_NAME + "-deployment"));

        // Check readiness of Apicurio Registry instance with PostgreSQL database storage deployment
        assertTrue(isDeploymentReady(client, Constants.REGISTRY_NAME + "-deployment"));
    }
}
