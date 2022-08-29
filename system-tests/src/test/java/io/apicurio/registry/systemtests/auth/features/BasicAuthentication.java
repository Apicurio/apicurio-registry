package io.apicurio.registry.systemtests.auth.features;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtests.client.ApicurioRegistryApiClient;
import io.apicurio.registry.systemtests.client.ArtifactType;
import io.apicurio.registry.systemtests.client.AuthMethod;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.DeploymentUtils;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Assertions;

import java.util.List;

public class BasicAuthentication {
    public static void testBasicAuthentication(ApicurioRegistry apicurioRegistry, String username, String password) {
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
}
