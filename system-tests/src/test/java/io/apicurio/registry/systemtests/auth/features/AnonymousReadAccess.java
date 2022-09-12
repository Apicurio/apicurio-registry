package io.apicurio.registry.systemtests.auth.features;

import io.apicur.registry.v1.ApicurioRegistry;
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

public class AnonymousReadAccess {
    public static void testAnonymousReadAccess(
            ApicurioRegistry apicurioRegistry,
            String username,
            String password,
            boolean useToken
    ) {
        /* RUN PRE-TEST ACTIONS */

        // GET REGISTRY HOSTNAME
        // Wait for readiness of registry hostname
        Assertions.assertTrue(ApicurioRegistryUtils.waitApicurioRegistryHostnameReady(apicurioRegistry));
        // Get registry hostname
        String hostname = ApicurioRegistryUtils.getApicurioRegistryHostname(apicurioRegistry);

        // GET CONTROL API CLIENT
        // Create control API client for setup of registry before test
        ApicurioRegistryApiClient controlClient = new ApicurioRegistryApiClient(hostname);
        // If access token is needed for control API client
        if (useToken) {
            // Get access token from Keycloak and update control API client with it
            controlClient.setToken(KeycloakUtils.getAccessToken(apicurioRegistry, username, password));
            // Set authentication method of control API client
            controlClient.setAuthMethod(AuthMethod.TOKEN);
        }
        // Wait for API availability
        Assertions.assertTrue(controlClient.waitServiceAvailable());

        // GET TEST API CLIENT
        // Create test API client for test actions
        ApicurioRegistryApiClient testClient = new ApicurioRegistryApiClient(hostname);

        // PREPARE NECESSARY VARIABLES
        // Get registry deployment
        Deployment deployment = Kubernetes.getDeployment(
                apicurioRegistry.getMetadata().getNamespace(),
                apicurioRegistry.getMetadata().getName() + "-deployment"
        );
        // Define artifact group ID
        String groupId = "anonymousReadAccessTest";
        // Define artifact ID
        String id = "anonymous-read-access-test";
        // Define artifact ID for create actions that should fail
        String failId = id + "-fail";
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

        // ENABLE REGISTRY AUTHENTICATION WITHOUT ANONYMOUS READ ACCESS AND TEST IT
        // Set environment variable AUTH_ENABLED of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("AUTH_ENABLED");
            setValue("true");
        }});
        // Wait for API availability
        Assertions.assertTrue(controlClient.waitServiceAvailable());
        // Check that API returns 401 Unauthorized when reading artifacts
        Assertions.assertTrue(testClient.checkUnauthorized());
        // Check that API returns 401 Unauthorized when creating artifact
        Assertions.assertTrue(
                testClient.createArtifact(groupId, failId, type, initialContent, HttpStatus.SC_UNAUTHORIZED)
        );
        // Check that API returns 401 Unauthorized when updating artifact
        Assertions.assertTrue(testClient.updateArtifact(groupId, id, updatedContent, HttpStatus.SC_UNAUTHORIZED));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(testClient.deleteArtifact(groupId, id, HttpStatus.SC_UNAUTHORIZED));

        // ENABLE ANONYMOUS READ ACCESS IN REGISTRY AUTHENTICATION AND TEST IT
        // Set environment variable REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED");
            setValue("true");
        }});
        // Wait for API availability
        Assertions.assertTrue(controlClient.waitServiceAvailable());
        // Check that API returns 200 OK when reading artifacts
        Assertions.assertNotNull(testClient.listArtifacts());
        // Check that API returns 401 Unauthorized when creating artifact
        Assertions.assertTrue(
                testClient.createArtifact(groupId, failId, type, initialContent, HttpStatus.SC_UNAUTHORIZED)
        );
        // Check that API returns 401 Unauthorized when updating artifact
        Assertions.assertTrue(testClient.updateArtifact(groupId, id, updatedContent, HttpStatus.SC_UNAUTHORIZED));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(testClient.deleteArtifact(groupId, id, HttpStatus.SC_UNAUTHORIZED));

        // DISABLE ANONYMOUS READ ACCESS IN REGISTRY AUTHENTICATION AND TEST IT
        // Set environment variable REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED of deployment to false
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED");
            setValue("false");
        }});
        // Wait for API availability
        Assertions.assertTrue(controlClient.waitServiceAvailable());
        // Check that API returns 401 Unauthorized when reading artifacts
        Assertions.assertTrue(testClient.checkUnauthorized());
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
