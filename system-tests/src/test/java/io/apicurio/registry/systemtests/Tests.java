package io.apicurio.registry.systemtests;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtests.client.ApicurioRegistryApiClient;
import io.apicurio.registry.systemtests.client.ArtifactType;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.DatabaseUtils;
import io.apicurio.registry.systemtests.framework.DeploymentUtils;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import io.apicurio.registry.systemtests.operator.types.KeycloakOLMOperatorType;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaKind;
import io.apicurio.registry.systemtests.registryinfra.resources.PersistenceKind;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.List;

@Disabled
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class Tests extends TestBase {
    /* Functions for all tests */

    @BeforeAll
    public void testBeforeAll(ExtensionContext testContext) {
        LOGGER.info("BeforeAll: " + testContext.getDisplayName());
    }

    @AfterAll
    public void testAfterAll(ExtensionContext testContext) {
        LOGGER.info("AfterAll: " + testContext.getDisplayName());
    }

    /* Functions for each test */

    @BeforeEach
    public void testBeforeEach(ExtensionContext testContext) throws InterruptedException {
        LOGGER.info("BeforeEach: " + testContext.getDisplayName());
    }

    @AfterEach
    public void testAfterEach(ExtensionContext testContext) {
        LOGGER.info("AfterEach: " + testContext.getDisplayName());

        resourceManager.deleteResources();

        operatorManager.uninstallOperators();
    }

    /* TESTS - PostgreSQL */

    @Test
    public void testRegistrySqlNoKeycloak(ExtensionContext testContext) throws InterruptedException {
        runTest(testContext, PersistenceKind.SQL, null, false, true);
    }

    @Test
    public void testRegistrySqlKeycloak(ExtensionContext testContext) throws InterruptedException {
        runTest(testContext, PersistenceKind.SQL, null, true, true);
    }

    /* TESTS - KafkaSQL */

    @Test
    public void testRegistryKafkasqlNoAuthNoKeycloak(ExtensionContext testContext) throws InterruptedException {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, false, true);
    }

    @Test
    public void testRegistryKafkasqlNoAuthKeycloak(ExtensionContext testContext) throws InterruptedException {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, true, true);
    }

    @Test
    public void testRegistryKafkasqlTLSNoKeycloak(ExtensionContext testContext) throws InterruptedException {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, false, true);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloak(ExtensionContext testContext) throws InterruptedException {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, true, true);
    }

    @Test
    public void testRegistryKafkasqlSCRAMNoKeycloak(ExtensionContext testContext) throws InterruptedException {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, false, true);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloak(ExtensionContext testContext) throws InterruptedException {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, true, true);
    }

    /* TESTS - Authentication/Authorization */

    @Test
    public void testRegistryAuthAnonymousReadAccessSqlNoKeycloak(ExtensionContext testContext) {
        // DEPLOY REGISTRY WITH SQL STORAGE
        // Deploy PostgreSQL
        DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);
        // Deploy registry
        ApicurioRegistry registry = ApicurioRegistryUtils.deployDefaultApicurioRegistrySql(testContext, false);

        // INITIALIZE API CLIENT
        // Wait for readiness of registry hostname
        Assertions.assertTrue(ApicurioRegistryUtils.waitApicurioRegistryHostnameReady(registry));
        // Create API client
        ApicurioRegistryApiClient client = new ApicurioRegistryApiClient(
                // Set hostname only
                ApicurioRegistryUtils.getApicurioRegistryHostname(registry)
        );
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());

        // PREPARE NECESSARY VARIABLES
        // Get registry deployment
        Deployment deployment = Kubernetes.getDeployment(
                registry.getMetadata().getNamespace(),
                registry.getMetadata().getName() + "-deployment"
        );
        // Save state of environment variables
        List<EnvVar> oldEnvVars = DeploymentUtils.cloneDeploymentEnvVars(deployment);

        // PREPARE REGISTRY CONTENT
        // Create artifact for test
        Assertions.assertTrue(client.createArtifact(
                "default",
                "anonymous-read-access-test",
                ArtifactType.JSON,
                "{}"
        ));

        /* RUN TEST ACTIONS */

        // ENABLE REGISTRY AUTHENTICATION WITHOUT ANONYMOUS READ ACCESS AND TEST IT
        // Set environment variable AUTH_ENABLED of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("AUTH_ENABLED");
            setValue("true");
        }});
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());
        // Check that API returns 401 Unauthorized when reading artifacts
        Assertions.assertTrue(client.checkUnauthorized());
        // Check that API returns 401 Unauthorized when creating artifact
        Assertions.assertTrue(client.createArtifact(
                "default",
                "anonymous-read-access-test-fail",
                ArtifactType.JSON,
                "{}",
                HttpStatus.SC_UNAUTHORIZED
        ));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(client.deleteArtifact(
                "default",
                "anonymous-read-access-test",
                HttpStatus.SC_UNAUTHORIZED
        ));

        // ENABLE ANONYMOUS READ ACCESS IN REGISTRY AUTHENTICATION AND TEST IT
        // Set environment variable REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED");
            setValue("true");
        }});
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());
        // Check that API returns 200 OK when reading artifacts
        Assertions.assertNotNull(client.listArtifacts());
        // Check that API returns 401 Unauthorized when creating artifact
        Assertions.assertTrue(client.createArtifact(
                "default",
                "anonymous-read-access-test-fail",
                ArtifactType.JSON,
                "{}",
                HttpStatus.SC_UNAUTHORIZED
        ));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(client.deleteArtifact(
                "default",
                "anonymous-read-access-test",
                HttpStatus.SC_UNAUTHORIZED
        ));

        // DISABLE ANONYMOUS READ ACCESS IN REGISTRY AUTHENTICATION AND TEST IT
        // Set environment variable REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED of deployment to false
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED");
            setValue("false");
        }});
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());
        // Check that API returns 401 Unauthorized when reading artifacts
        Assertions.assertTrue(client.checkUnauthorized());
        // Check that API returns 401 Unauthorized when creating artifact
        Assertions.assertTrue(client.createArtifact(
                "default",
                "anonymous-read-access-test-fail",
                ArtifactType.JSON,
                "{}",
                HttpStatus.SC_UNAUTHORIZED
        ));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(client.deleteArtifact(
                "default",
                "anonymous-read-access-test",
                HttpStatus.SC_UNAUTHORIZED
        ));

        // RETURN REGISTRY INTO PRE-TEST STATE
        // Restore pre-test state of environment variables
        DeploymentUtils.setDeploymentEnvVars(deployment, oldEnvVars);
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());
        // Remove artifact created for test
        Assertions.assertTrue(client.deleteArtifact(
                "default",
                "anonymous-read-access-test"
        ));
    }

    @Test
    public void testRegistryAuthAnonymousReadAccessSqlKeycloak(ExtensionContext testContext) {
        /* RUN PRE-TEST ACTIONS */

        // DEPLOY KEYCLOAK
        // Install Keycloak operator
        KeycloakOLMOperatorType keycloakOLMOperator = new KeycloakOLMOperatorType();
        operatorManager.installOperator(testContext, keycloakOLMOperator);
        // Deploy Keycloak
        KeycloakUtils.deployKeycloak(testContext);

        // DEPLOY REGISTRY WITH SQL STORAGE
        // Deploy PostgreSQL
        DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);
        // Deploy registry
        ApicurioRegistry registry = ApicurioRegistryUtils.deployDefaultApicurioRegistrySql(testContext, true);

        // INITIALIZE API CLIENT
        // Wait for readiness of registry hostname
        Assertions.assertTrue(ApicurioRegistryUtils.waitApicurioRegistryHostnameReady(registry));
        String accessToken = KeycloakUtils.getAccessToken(registry, "registry-admin", "changeme");
        // Create API client
        ApicurioRegistryApiClient client = new ApicurioRegistryApiClient(
                // Set hostname only
                ApicurioRegistryUtils.getApicurioRegistryHostname(registry),
                accessToken
        );
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());

        // PREPARE NECESSARY VARIABLES
        // Get registry deployment
        Deployment deployment = Kubernetes.getDeployment(
                registry.getMetadata().getNamespace(),
                registry.getMetadata().getName() + "-deployment"
        );
        // Save state of environment variables
        List<EnvVar> oldEnvVars = DeploymentUtils.cloneDeploymentEnvVars(deployment);

        // PREPARE REGISTRY CONTENT
        // Create artifact for test
        Assertions.assertTrue(client.createArtifact(
                "default",
                "anonymous-read-access-test",
                ArtifactType.JSON,
                "{}"
        ));

        // PREPARE API CLIENT
        // Temporarily remove access token from client to try unauthenticated/anonymous access
        client.setToken(null);

        /* RUN TEST ACTIONS */

        // ENABLE REGISTRY AUTHENTICATION WITHOUT ANONYMOUS READ ACCESS AND TEST IT
        // Set environment variable AUTH_ENABLED of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("AUTH_ENABLED");
            setValue("true");
        }});
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());
        // Check that API returns 401 Unauthorized when reading artifacts
        Assertions.assertTrue(client.checkUnauthorized());
        // Check that API returns 401 Unauthorized when creating artifact
        Assertions.assertTrue(client.createArtifact(
                "default",
                "anonymous-read-access-test-fail",
                ArtifactType.JSON,
                "{}",
                HttpStatus.SC_UNAUTHORIZED
        ));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(client.deleteArtifact(
                "default",
                "anonymous-read-access-test",
                HttpStatus.SC_UNAUTHORIZED
        ));

        // ENABLE ANONYMOUS READ ACCESS IN REGISTRY AUTHENTICATION AND TEST IT
        // Set environment variable REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED");
            setValue("true");
        }});
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());
        // Check that API returns 200 OK when reading artifacts
        Assertions.assertNotNull(client.listArtifacts());
        // Check that API returns 401 Unauthorized when creating artifact
        Assertions.assertTrue(client.createArtifact(
                "default",
                "anonymous-read-access-test-fail",
                ArtifactType.JSON,
                "{}",
                HttpStatus.SC_UNAUTHORIZED
        ));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(client.deleteArtifact(
                "default",
                "anonymous-read-access-test",
                HttpStatus.SC_UNAUTHORIZED
        ));

        // DISABLE ANONYMOUS READ ACCESS IN REGISTRY AUTHENTICATION AND TEST IT
        // Set environment variable REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED of deployment to false
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED");
            setValue("false");
        }});
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());
        // Check that API returns 401 Unauthorized when reading artifacts
        Assertions.assertTrue(client.checkUnauthorized());
        // Check that API returns 401 Unauthorized when creating artifact
        Assertions.assertTrue(client.createArtifact(
                "default",
                "anonymous-read-access-test-fail",
                ArtifactType.JSON,
                "{}",
                HttpStatus.SC_UNAUTHORIZED
        ));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(client.deleteArtifact(
                "default",
                "anonymous-read-access-test",
                HttpStatus.SC_UNAUTHORIZED
        ));

        /* RUN POST-TEST ACTIONS */

        // RETURN REGISTRY INTO PRE-TEST STATE
        // Restore pre-test state of environment variables
        DeploymentUtils.setDeploymentEnvVars(deployment, oldEnvVars);
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());
        // Restore API client access token
        client.setToken(accessToken);
        // Remove artifact created for test
        Assertions.assertTrue(client.deleteArtifact(
                "default",
                "anonymous-read-access-test"
        ));

        // REMOVE KEYCLOAK
        // Remove KeycloakRealm and Keycloak
        KeycloakUtils.removeKeycloak();
    }
}
