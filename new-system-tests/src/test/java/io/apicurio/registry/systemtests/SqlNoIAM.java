package io.apicurio.registry.systemtests;

import io.apicur.registry.v1.ApicurioRegistry;
import io.fabric8.junit.jupiter.api.KubernetesTest;
import io.fabric8.junit.jupiter.api.LoadKubernetesManifests;
import io.fabric8.openshift.client.OpenShiftClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.apicurio.registry.systemtests.Utils.findRegistryOperatorDeployment;
import static io.apicurio.registry.systemtests.Utils.isDeploymentReady;
import static io.apicurio.registry.systemtests.Utils.waitDeploymentReady;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KubernetesTest
@LoadKubernetesManifests({
        // PostgreSQL database resources
        "/sql/00_persistent_volume_claim.yaml", // Storage
        "/sql/01_deployment.yaml", // Deployment
        "/sql/02_service.yaml", // Service
        // Apicurio Registry operator
        "/apicurio/00_operator_group.yaml", // Operator group for Apicurio Registry operator
        "/apicurio/01_subscription_namespace.yaml", // Apicurio Registry operator subscription in specific namespace
        // Apicurio Registry instance
        "/apicurio/02_registry_sql_no_iam.yaml" // Apicurio Registry instance with PostgreSQL storage and without IAM
})
@TestMethodOrder(MethodOrderer.MethodName.class)
public class SqlNoIAM extends TestBase {
    /** {@link OpenShiftClient} instance for tests. */
    private OpenShiftClient client;

    /**
     * @return {@link OpenShiftClient} instance used in tests.
     */
    @Override
    protected OpenShiftClient getClient() {
        return client;
    }

    /**
     * Performs actions that should be done before all tests.
     */
    @BeforeAll
    public static void beforeAll() {
        // Log information about current action
        System.out.println("Before all.");

        // Client is not accessible here
    }

    /**
     * Performs actions that should be done after all tests.
     */
    @AfterAll
    public static void afterAll() {
        // Log information about current action
        System.out.println("After all.");

        // Client is not accessible here
    }

    /**
     * Performs actions that should be done before each test.
     */
    @BeforeEach
    public void beforeEach() {
        // Log information about current action
        System.out.println("Before each.");
    }

    /**
     * Performs actions that should be done after each test.
     */
    @AfterEach
    public void afterEach() {
        // Log information about current action
        System.out.println("After each.");
    }

    /**
     * Tests that {@link ApicurioRegistry} with PostgreSQL database storage becomes ready.
     */
    @Test
    public void testDeploy() {
        // Log information about current action
        System.out.println("### testDeploy test ###");

        // Wait for readiness of PostgreSQL database deployment
        assertTrue(waitDeploymentReady(client, Constants.POSTGRESQL_NAME));

        // Wait for readiness of Apicurio Registry operator deployment
        assertTrue(waitDeploymentReady(client, findRegistryOperatorDeployment(client).getMetadata().getName()));

        // Wait for readiness of Apicurio Registry instance with PostgreSQL database storage deployment
        assertTrue(waitDeploymentReady(client, Constants.REGISTRY_NAME + "-deployment"));

        // Check readiness of Apicurio Registry instance with PostgreSQL database storage deployment
        assertTrue(isDeploymentReady(client, Constants.REGISTRY_NAME + "-deployment"));
    }
}
