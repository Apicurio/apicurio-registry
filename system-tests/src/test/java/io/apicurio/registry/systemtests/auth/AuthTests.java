package io.apicurio.registry.systemtests;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtests.client.ApicurioRegistryApiClient;
import io.apicurio.registry.systemtests.client.ArtifactType;
import io.apicurio.registry.systemtests.client.AuthMethod;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.DeploymentUtils;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaKind;
import io.apicurio.registry.systemtests.registryinfra.resources.PersistenceKind;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.List;

@Disabled
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AuthTests extends TestBase {
    /* Authentication test methods */

    public static void anonymousReadAccess(
            ApicurioRegistry apicurioRegistry,
            String username,
            String password,
            boolean useToken
    ) {
        /* RUN PRE-TEST ACTIONS */

        // INITIALIZE API CLIENT
        // Wait for readiness of registry hostname
        Assertions.assertTrue(ApicurioRegistryUtils.waitApicurioRegistryHostnameReady(apicurioRegistry));
        // Create API client
        ApicurioRegistryApiClient client = new ApicurioRegistryApiClient(
                // Set hostname only
                ApicurioRegistryUtils.getApicurioRegistryHostname(apicurioRegistry)
        );
        // If access token is needed
        if (useToken) {
            // Get access token from Keycloak and update API client with it
            client.setToken(KeycloakUtils.getAccessToken(apicurioRegistry, username, password));
            // Set authentication method
            client.setAuthMethod(AuthMethod.TOKEN);
        }
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());

        // PREPARE NECESSARY VARIABLES
        // Get registry deployment
        Deployment deployment = Kubernetes.getDeployment(
                apicurioRegistry.getMetadata().getNamespace(),
                apicurioRegistry.getMetadata().getName() + "-deployment"
        );
        // Save state of deployment's environment variables
        List<EnvVar> savedEnvVars = DeploymentUtils.cloneDeploymentEnvVars(deployment);
        // Define artifact group ID
        String groupId = "default";
        // Define artifact ID
        String id = "anonymous-read-access-test";
        // Define artifact ID for create actions that should fail
        String failId = id + "-fail";
        // Define artifact type
        ArtifactType type = ArtifactType.JSON;
        // Define artifact content
        String content = "{}";

        // PREPARE REGISTRY CONTENT
        // Create artifact for test
        Assertions.assertTrue(client.createArtifact(groupId, id, type, content));

        // PREPARE API CLIENT
        // If access token is used
        if (useToken) {
            // Temporarily remove access token from client to try unauthenticated/anonymous access
            client.setToken(null);
        }

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
        Assertions.assertTrue(client.createArtifact(groupId, failId, type, content, HttpStatus.SC_UNAUTHORIZED));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(client.deleteArtifact(groupId, id, HttpStatus.SC_UNAUTHORIZED));

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
        Assertions.assertTrue(client.createArtifact(groupId, failId, type, content, HttpStatus.SC_UNAUTHORIZED));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(client.deleteArtifact(groupId, id, HttpStatus.SC_UNAUTHORIZED));

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
        Assertions.assertTrue(client.createArtifact(groupId, failId, type, content, HttpStatus.SC_UNAUTHORIZED));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(client.deleteArtifact(groupId, id, HttpStatus.SC_UNAUTHORIZED));

        /* RUN POST-TEST ACTIONS */

        // RETURN REGISTRY INTO PRE-TEST STATE
        // Restore pre-test state of environment variables
        DeploymentUtils.setDeploymentEnvVars(deployment, savedEnvVars);
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());
        // If access token was needed
        if (useToken) {
            // Get access token from Keycloak again and update API client with it
            client.setToken(KeycloakUtils.getAccessToken(apicurioRegistry, username, password));
        }
        // Remove artifact created for test
        Assertions.assertTrue(client.deleteArtifact(groupId, id));
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    public static void basicAuthentication(ApicurioRegistry apicurioRegistry, String username, String password) {
        /* RUN PRE-TEST ACTIONS */

        // INITIALIZE API CLIENT
        // Wait for readiness of registry hostname
        Assertions.assertTrue(ApicurioRegistryUtils.waitApicurioRegistryHostnameReady(apicurioRegistry));
        // Create API client
        ApicurioRegistryApiClient client = new ApicurioRegistryApiClient(
                // Set hostname only
                ApicurioRegistryUtils.getApicurioRegistryHostname(apicurioRegistry)
        );
        // Get access token from Keycloak and update API client with it
        client.setToken(KeycloakUtils.getAccessToken(apicurioRegistry, username, password));
        // Set authentication method to token
        client.setAuthMethod(AuthMethod.TOKEN);
        // Set username
        client.setUsername(username);
        // Set password
        client.setPassword(password);
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());

        // PREPARE NECESSARY VARIABLES
        // Get registry deployment
        Deployment deployment = Kubernetes.getDeployment(
                apicurioRegistry.getMetadata().getNamespace(),
                apicurioRegistry.getMetadata().getName() + "-deployment"
        );
        // Save state of deployment's environment variables
        List<EnvVar> savedEnvVars = DeploymentUtils.cloneDeploymentEnvVars(deployment);
        // Define artifact group ID
        String groupId = "default";
        // Define artifact ID
        String id = "basic-authentication-test";
        // Define artifact ID for create actions that should fail
        String failId = id + "-fail";
        // Define artifact ID prefix for create actions that should succeed
        String succeedId = id + "-succeed-";
        // Define artifact type
        ArtifactType type = ArtifactType.JSON;
        // Define artifact content
        String content = "{}";

        // PREPARE REGISTRY CONTENT
        // Create artifact for test
        Assertions.assertTrue(client.createArtifact(groupId, id, type, content));

        // PREPARE API CLIENT
        // Set authentication method to Basic
        client.setAuthMethod(AuthMethod.BASIC);

        /* RUN TEST ACTIONS */

        // TRY DEFAULT VALUE OF HTTP BASIC AUTHENTICATION
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());
        // Check that API returns 401 Unauthorized when reading artifacts
        Assertions.assertNotNull(client.listArtifacts(1, HttpStatus.SC_UNAUTHORIZED));
        // Check that API returns 401 Unauthorized when creating artifact
        Assertions.assertTrue(client.createArtifact(groupId, failId, type, content, HttpStatus.SC_UNAUTHORIZED));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(client.deleteArtifact(groupId, id, HttpStatus.SC_UNAUTHORIZED));

        // DISABLE HTTP BASIC AUTHENTICATION AND TEST IT
        // Set environment variable CLIENT_CREDENTIALS_BASIC_AUTH_ENABLED of deployment to false
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("CLIENT_CREDENTIALS_BASIC_AUTH_ENABLED");
            setValue("false");
        }});
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());
        // Check that API returns 401 Unauthorized when reading artifacts
        Assertions.assertNotNull(client.listArtifacts(1, HttpStatus.SC_UNAUTHORIZED));
        // Check that API returns 401 Unauthorized when creating artifact
        Assertions.assertTrue(client.createArtifact(groupId, failId, type, content, HttpStatus.SC_UNAUTHORIZED));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(client.deleteArtifact(groupId, id, HttpStatus.SC_UNAUTHORIZED));

        // ENABLE HTTP BASIC AUTHENTICATION AND TEST IT
        // Set environment variable REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED of deployment to false
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("CLIENT_CREDENTIALS_BASIC_AUTH_ENABLED");
            setValue("true");
        }});
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());
        // Check that API returns 200 OK when reading artifacts
        Assertions.assertNotNull(client.listArtifacts());
        // Check that API returns 200 OK when creating artifact
        Assertions.assertTrue(client.createArtifact(groupId, succeedId + "true-value", type, content));
        // Check that API returns 204 No Content when deleting artifact
        Assertions.assertTrue(client.deleteArtifact(groupId, succeedId + "true-value"));

        /* RUN POST-TEST ACTIONS */

        // RETURN REGISTRY INTO PRE-TEST STATE
        // Restore pre-test state of environment variables
        DeploymentUtils.setDeploymentEnvVars(deployment, savedEnvVars);
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());
        // Set authentication method back to Token
        client.setAuthMethod(AuthMethod.TOKEN);
        // Remove artifact created for test
        Assertions.assertTrue(client.deleteArtifact(groupId, id));
    }

    /* Authentication test runners */

    protected void runAnonymousReadAccess(
            ExtensionContext testContext,
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind,
            boolean useKeycloak
    ) {
        ApicurioRegistry registry = deployTestResources(testContext, persistenceKind, kafkaKind, useKeycloak);

        if (useKeycloak) {
            anonymousReadAccess(registry, "registry-admin", "changeme", true);

            KeycloakUtils.removeKeycloak();
        } else {
            anonymousReadAccess(registry, null, null, false);
        }
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    protected void runBasicAuthentication(
            ExtensionContext testContext,
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind
    ) {
        ApicurioRegistry registry = deployTestResources(testContext, persistenceKind, kafkaKind, true);

        basicAuthentication(registry, "registry-admin", "changeme");

        KeycloakUtils.removeKeycloak();
    }

    /* TESTS - PostgreSQL */

    @Test
    public void testRegistrySqlNoKeycloakAnonymousReadAccess(ExtensionContext testContext) {
        runAnonymousReadAccess(testContext, PersistenceKind.SQL, null, false);
    }

    @Test
    public void testRegistrySqlKeycloakAnonymousReadAccess(ExtensionContext testContext) {
        runAnonymousReadAccess(testContext, PersistenceKind.SQL, null, true);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistrySqlKeycloakBasicAuthentication(ExtensionContext testContext) {
        runBasicAuthentication(testContext, PersistenceKind.SQL, null);
    }

    /* TESTS - KafkaSQL */

    @Test
    public void testRegistryKafkasqlNoAuthNoKeycloakAnonymousReadAccess(ExtensionContext testContext) {
        runAnonymousReadAccess(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, false);
    }

    @Test
    public void testRegistryKafkasqlNoAuthKeycloakAnonymousReadAccess(ExtensionContext testContext) {
        runAnonymousReadAccess(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, true);
    }

    @Test
    public void testRegistryKafkasqlTLSNoKeycloakAnonymousReadAccess(ExtensionContext testContext) {
        runAnonymousReadAccess(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, false);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloakAnonymousReadAccess(ExtensionContext testContext) {
        runAnonymousReadAccess(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, true);
    }

    @Test
    public void testRegistryKafkasqlSCRAMNoKeycloakAnonymousReadAccess(ExtensionContext testContext) {
        runAnonymousReadAccess(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, false);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloakAnonymousReadAccess(ExtensionContext testContext) {
        runAnonymousReadAccess(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, true);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistryKafkasqlNoAuthKeycloakBasicAuthentication(ExtensionContext testContext) {
        runBasicAuthentication(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloakBasicAuthentication(ExtensionContext testContext) {
        runBasicAuthentication(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloakBasicAuthentication(ExtensionContext testContext) {
        runBasicAuthentication(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM);
    }
}
