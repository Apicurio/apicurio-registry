package io.apicurio.registry.systemtests.auth.features;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicurio.registry.systemtests.client.ApicurioRegistryApiClient;
import io.apicurio.registry.systemtests.client.ArtifactType;
import io.apicurio.registry.systemtests.client.AuthMethod;
import io.apicurio.registry.systemtests.client.KeycloakAdminApiClient;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.Constants;
import io.apicurio.registry.systemtests.framework.DeploymentUtils;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Assertions;

public class BasicAuthentication {
    public static void testBasicAuthentication(ApicurioRegistry apicurioRegistry, String username, String password) {
        /* RUN PRE-TEST ACTIONS */

        // GET REGISTRY HOSTNAME
        // Wait for readiness of registry hostname
        Assertions.assertTrue(ApicurioRegistryUtils.waitApicurioRegistryHostnameReady(apicurioRegistry));
        // Get registry hostname
        String hostname = ApicurioRegistryUtils.getApicurioRegistryHostname(apicurioRegistry);

        // GET CONTROL API CLIENT
        // Create control API client for setup of registry before test
        ApicurioRegistryApiClient controlClient = new ApicurioRegistryApiClient(hostname);
        // Get access token from Keycloak and update API client with it
        controlClient.setToken(KeycloakUtils.getAccessToken(apicurioRegistry, username, password));
        // Set authentication method to token
        controlClient.setAuthMethod(AuthMethod.TOKEN);
        // Wait for API availability
        Assertions.assertTrue(controlClient.waitServiceAvailable());

        // GET KEYCLOAK ADMIN API CLIENT
        // Get Keycloak admin API URL
        String keycloakUrl = KeycloakUtils.getDefaultKeycloakAdminURL();
        // Create Keycloak admin API client
        KeycloakAdminApiClient keycloakAdminApiClient = new KeycloakAdminApiClient(
                // Set Keycloak admin API URL
                keycloakUrl,
                // Set admin access token
                KeycloakUtils.getAdminAccessToken()
        );
        // Check that admin access token is available
        Assertions.assertNotNull(keycloakAdminApiClient.getToken());

        // GET TEST API CLIENT
        // Create test API client for test actions
        ApicurioRegistryApiClient testClient = new ApicurioRegistryApiClient(hostname);
        // Set username
        testClient.setUsername(Constants.SSO_TEST_CLIENT_API);
        // Set secret
        testClient.setPassword(keycloakAdminApiClient.getClientSecret(Constants.SSO_TEST_CLIENT_API));
        // Check that client secret is available
        Assertions.assertNotNull(testClient.getPassword());
        // Set authentication method to basic
        testClient.setAuthMethod(AuthMethod.BASIC);

        // PREPARE NECESSARY VARIABLES
        // Get registry deployment
        Deployment deployment = Kubernetes.getDeployment(
                apicurioRegistry.getMetadata().getNamespace(),
                apicurioRegistry.getMetadata().getName() + "-deployment"
        );
        // Define artifact group ID
        String groupId = "basicAuthenticationTest";
        // Define artifact ID
        String id = "basic-authentication-test";
        // Define artifact ID for create actions that should fail
        String failId = id + "-fail";
        // Define artifact ID prefix for create actions that should succeed
        String succeedId = id + "-succeed-";
        // Define artifact type
        ArtifactType type = ArtifactType.JSON;
        // Define artifact initial content
        String initialContent = "{}";
        // Define artifact updated content
        String updatedContent = "{\"key\":\"id\"}";

        // PREPARE REGISTRY CONTENT
        // Create artifact for test
        Assertions.assertTrue(controlClient.createArtifact(groupId, id, type, initialContent));

        /* RUN TEST ACTIONS */

        // TRY DEFAULT VALUE OF HTTP BASIC AUTHENTICATION
        // Wait for API availability
        Assertions.assertTrue(controlClient.waitServiceAvailable());
        // Check that API returns 401 Unauthorized when reading artifacts
        Assertions.assertNull(testClient.listArtifacts(1, HttpStatus.SC_UNAUTHORIZED));
        // Check that API returns 401 Unauthorized when creating artifact
        Assertions.assertTrue(
                testClient.createArtifact(groupId, failId, type, initialContent, HttpStatus.SC_UNAUTHORIZED)
        );
        // Check that API returns 401 Unauthorized when updating artifact
        Assertions.assertTrue(testClient.updateArtifact(groupId, id, updatedContent, HttpStatus.SC_UNAUTHORIZED));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(testClient.deleteArtifact(groupId, id, HttpStatus.SC_UNAUTHORIZED));

        // ENABLE HTTP BASIC AUTHENTICATION AND TEST IT
        // Set environment variable REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("CLIENT_CREDENTIALS_BASIC_AUTH_ENABLED");
            setValue("true");
        }});
        // Define artifact ID for the test part
        String testArtifactId = succeedId + "true-value";
        // Wait for API availability
        Assertions.assertTrue(controlClient.waitServiceAvailable());
        // Check that API returns 200 OK when reading artifacts
        Assertions.assertNotNull(testClient.listArtifacts());
        // Check that API returns 200 OK when creating artifact
        Assertions.assertTrue(testClient.createArtifact(groupId, testArtifactId, type, initialContent));
        // Check that API returns 200 OK when updating artifact
        Assertions.assertTrue(testClient.updateArtifact(groupId, testArtifactId, updatedContent));
        // Check that API returns 204 No Content when deleting artifact
        Assertions.assertTrue(testClient.deleteArtifact(groupId, testArtifactId));

        // DISABLE HTTP BASIC AUTHENTICATION AND TEST IT
        // Set environment variable CLIENT_CREDENTIALS_BASIC_AUTH_ENABLED of deployment to false
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("CLIENT_CREDENTIALS_BASIC_AUTH_ENABLED");
            setValue("false");
        }});
        // Wait for API availability
        Assertions.assertTrue(controlClient.waitServiceAvailable());
        // Check that API returns 401 Unauthorized when reading artifacts
        Assertions.assertNull(testClient.listArtifacts(1, HttpStatus.SC_UNAUTHORIZED));
        // Check that API returns 401 Unauthorized when creating artifact
        Assertions.assertTrue(
                testClient.createArtifact(groupId, failId, type, initialContent, HttpStatus.SC_UNAUTHORIZED)
        );
        // Check that API returns 401 Unauthorized when updating artifact
        Assertions.assertTrue(testClient.updateArtifact(groupId, id, updatedContent, HttpStatus.SC_UNAUTHORIZED));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(testClient.deleteArtifact(groupId, id, HttpStatus.SC_UNAUTHORIZED));
    }
}
