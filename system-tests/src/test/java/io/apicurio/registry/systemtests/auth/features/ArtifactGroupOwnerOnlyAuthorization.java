package io.apicurio.registry.systemtests.auth.features;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicurio.registry.systemtests.client.ApicurioRegistryApiClient;
import io.apicurio.registry.systemtests.client.ArtifactType;
import io.apicurio.registry.systemtests.client.AuthMethod;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.Constants;
import io.apicurio.registry.systemtests.framework.DeploymentUtils;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Assertions;

public class ArtifactGroupOwnerOnlyAuthorization {
    public static void testArtifactGroupOwnerOnlyAuthorization(ApicurioRegistry apicurioRegistry) {
        /* RUN PRE-TEST ACTIONS */

        // GET REGISTRY HOSTNAME
        // Wait for readiness of registry hostname
        Assertions.assertTrue(ApicurioRegistryUtils.waitApicurioRegistryHostnameReady(apicurioRegistry));
        // Get registry hostname
        String hostname = ApicurioRegistryUtils.getApicurioRegistryHostname(apicurioRegistry);

        // GET OWNER API CLIENT
        // Create owner API client
        ApicurioRegistryApiClient ownerClient = new ApicurioRegistryApiClient(hostname);
        // Get access token from Keycloak and update owner API client with it
        ownerClient.setToken(
                KeycloakUtils.getAccessToken(apicurioRegistry, Constants.SSO_ADMIN_USER, Constants.SSO_USER_PASSWORD)
        );
        // Set authentication method of owner API client
        ownerClient.setAuthMethod(AuthMethod.TOKEN);
        // Wait for API availability
        Assertions.assertTrue(ownerClient.waitServiceAvailable());

        // GET TEST API CLIENT
        // Create test API client
        ApicurioRegistryApiClient testClient = new ApicurioRegistryApiClient(hostname);
        // Get access token from Keycloak and update test API client with it
        testClient.setToken(
                KeycloakUtils.getAccessToken(
                        apicurioRegistry,
                        Constants.SSO_DEVELOPER_USER,
                        Constants.SSO_USER_PASSWORD
                )
        );
        // Set authentication method of test API client
        testClient.setAuthMethod(AuthMethod.TOKEN);

        // PREPARE NECESSARY VARIABLES
        // Get registry deployment
        Deployment deployment = Kubernetes.getDeployment(
                apicurioRegistry.getMetadata().getNamespace(),
                apicurioRegistry.getMetadata().getName() + "-deployment"
        );
        // Define artifact group ID
        String groupId = "artifactGroupOwnerOnlyAuthorizationTest";
        // Define artifact ID
        String id = "artifact-group-owner-only-authorization-test";
        // Define artifact ID for create actions that should fail
        String failId = id + "-fail";
        // Define artifact ID prefix for actions that should succeed
        String succeedId = id + "-succeed-";
        // Define artifact type
        ArtifactType type = ArtifactType.JSON;
        // Define artifact initial content
        String initialContent = "{}";
        // Define artifact updated content
        String updatedContent = "{\"key\":\"id\"}";

        // ENABLE ARTIFACT OWNER ONLY AUTHORIZATION THAT IS REQUIRED FOR ARTIFACT GROUP OWNER ONLY AUTHORIZATION
        // Set environment variable REGISTRY_AUTH_OBAC_ENABLED of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("REGISTRY_AUTH_OBAC_ENABLED");
            setValue("true");
        }});
        // Wait for API availability
        Assertions.assertTrue(ownerClient.waitServiceAvailable());

        /* RUN TEST ACTIONS */

        // TEST DEFAULT VALUE OF REGISTRY_AUTH_OBAC_LIMIT_GROUP_ACCESS (false)
        // Define artifact ID for the owner part
        String testArtifactIdOwner = succeedId + "default-by-owner";
        // Define artifact ID for the test part
        String testArtifactIdTest = succeedId + "default-by-test";
        // --- owner part
        // Check that API returns 200 OK when reading all artifacts by owner
        Assertions.assertNotNull(ownerClient.listArtifacts());
        // Check that API returns 200 OK when creating owner's artifact in owner's group by owner
        Assertions.assertTrue(ownerClient.createArtifact(groupId, testArtifactIdOwner, type, initialContent));
        // Check that API returns 200 OK when reading artifacts in owner's group by owner
        Assertions.assertNotNull(ownerClient.listGroupArtifacts(groupId));
        // Check that API returns 200 OK when updating owner's artifact in owner's group by owner
        Assertions.assertTrue(ownerClient.updateArtifact(groupId, testArtifactIdOwner, updatedContent));
        // --- test part
        // Check that API returns 200 OK when reading all artifacts by test
        Assertions.assertNotNull(testClient.listArtifacts());
        // Check that API returns 200 OK when reading artifacts in owner's group by test
        Assertions.assertNotNull(testClient.listGroupArtifacts(groupId));
        // Check that API returns 200 OK when creating test's artifact in owner's group by test
        Assertions.assertTrue(testClient.createArtifact(groupId, testArtifactIdTest, type, initialContent));
        // Check that API returns 200 OK when updating test's artifact in owner's group by test
        Assertions.assertTrue(testClient.updateArtifact(groupId, testArtifactIdTest, updatedContent));
        // Check that API returns 204 No Content when deleting test's artifact in owner's group by test
        Assertions.assertTrue(testClient.deleteArtifact(groupId, testArtifactIdTest));
        // --- owner part finish
        // Check that API returns 204 No Content when deleting owner's artifact in owner's group by owner
        Assertions.assertTrue(ownerClient.deleteArtifact(groupId, testArtifactIdOwner));

        // ENABLE ARTIFACT GROUP OWNER ONLY AUTHORIZATION AND TEST IT
        // Define artifact ID for the owner part
        testArtifactIdOwner = succeedId + "true-by-owner";
        // Define second artifact ID for the owner part
        String testSecondArtifactIdOwner = testArtifactIdOwner + "-second";
        // Define artifact ID for the test part
        testArtifactIdTest = succeedId + "true-by-test";
        // --- prepare artifacts for test
        // Check that API returns 200 OK when creating owner's artifact in owner's group by owner (feature not enabled)
        Assertions.assertTrue(ownerClient.createArtifact(groupId, testArtifactIdOwner, type, initialContent));
        // Check that API returns 200 OK when creating test's artifact in owner's group by test (feature not enabled)
        Assertions.assertTrue(testClient.createArtifact(groupId, testArtifactIdTest, type, initialContent));
        // --- enable feature
        // Set environment variable REGISTRY_AUTH_OBAC_LIMIT_GROUP_ACCESS of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("REGISTRY_AUTH_OBAC_LIMIT_GROUP_ACCESS");
            setValue("true");
        }});
        // Wait for API availability
        Assertions.assertTrue(ownerClient.waitServiceAvailable());
        // --- owner part
        // Check that API returns 200 OK when reading all artifacts by owner
        Assertions.assertNotNull(ownerClient.listArtifacts());
        // Check that API returns 200 OK when reading artifacts in owner's group by owner
        Assertions.assertNotNull(ownerClient.listGroupArtifacts(groupId));
        // Check that API returns 200 OK when creating second owner's artifact in owner's group by owner
        Assertions.assertTrue(ownerClient.createArtifact(groupId, testSecondArtifactIdOwner, type, initialContent));
        // Check that API returns 200 OK when updating owner's artifact in owner's group by owner
        Assertions.assertTrue(ownerClient.updateArtifact(groupId, testArtifactIdOwner, updatedContent));
        // Check that API returns 200 OK when updating second owner's artifact in owner's group by owner
        Assertions.assertTrue(ownerClient.updateArtifact(groupId, testSecondArtifactIdOwner, updatedContent));
        // --- test part
        // Check that API returns 200 OK when reading all artifacts by test
        Assertions.assertNotNull(testClient.listArtifacts());
        // Check that API returns 200 OK when reading artifacts in owner's group by test
        Assertions.assertNotNull(testClient.listGroupArtifacts(groupId));
        // Check that API returns 403 Forbidden when creating second test's artifact in owner's group by test
        Assertions.assertTrue(
                testClient.createArtifact(groupId, failId, type, initialContent, HttpStatus.SC_FORBIDDEN)
        );
        // Check that API returns 403 Forbidden when updating test's artifact in owner's group by test
        Assertions.assertTrue(
                testClient.updateArtifact(groupId, testArtifactIdTest, updatedContent, HttpStatus.SC_FORBIDDEN)
        );
        // Check that API returns 403 Forbidden when updating owner's artifact in owner's group by test
        Assertions.assertTrue(
                testClient.updateArtifact(groupId, testArtifactIdOwner, updatedContent, HttpStatus.SC_FORBIDDEN)
        );
        // Check that API returns 403 Forbidden when deleting test's artifact in owner's group by test
        Assertions.assertTrue(testClient.deleteArtifact(groupId, testArtifactIdTest, HttpStatus.SC_FORBIDDEN));
        // Check that API returns 403 Forbidden when deleting owner's artifact in owner's group by test
        Assertions.assertTrue(testClient.deleteArtifact(groupId, testArtifactIdOwner, HttpStatus.SC_FORBIDDEN));
        // --- owner part finish
        // Check that API returns 204 No Content when deleting test's artifact in owner's group by owner
        Assertions.assertTrue(ownerClient.deleteArtifact(groupId, testArtifactIdTest));
        // Check that API returns 204 No Content when deleting second owner's artifact in owner's group by owner
        Assertions.assertTrue(ownerClient.deleteArtifact(groupId, testSecondArtifactIdOwner));
        // Check that API returns 204 No Content when deleting owner's artifact in owner's group by owner
        Assertions.assertTrue(ownerClient.deleteArtifact(groupId, testArtifactIdOwner));

        // DISABLE ARTIFACT GROUP OWNER ONLY AUTHORIZATION AND TEST IT
        // Set environment variable REGISTRY_AUTH_OBAC_LIMIT_GROUP_ACCESS of deployment to false
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("REGISTRY_AUTH_OBAC_LIMIT_GROUP_ACCESS");
            setValue("false");
        }});
        // Define artifact ID for the owner part
        testArtifactIdOwner = succeedId + "false-by-owner";
        // Define artifact ID for the test part
        testArtifactIdTest = succeedId + "false-by-test";
        // Wait for API availability
        Assertions.assertTrue(ownerClient.waitServiceAvailable());
        // --- owner part
        // Check that API returns 200 OK when reading all artifacts by owner
        Assertions.assertNotNull(ownerClient.listArtifacts());
        // Check that API returns 200 OK when creating owner's artifact in owner's group by owner
        Assertions.assertTrue(ownerClient.createArtifact(groupId, testArtifactIdOwner, type, initialContent));
        // Check that API returns 200 OK when reading artifacts in owner's group by owner
        Assertions.assertNotNull(ownerClient.listGroupArtifacts(groupId));
        // Check that API returns 200 OK when updating owner's artifact in owner's group by owner
        Assertions.assertTrue(ownerClient.updateArtifact(groupId, testArtifactIdOwner, updatedContent));
        // --- test part
        // Check that API returns 200 OK when reading all artifacts by test
        Assertions.assertNotNull(testClient.listArtifacts());
        // Check that API returns 200 OK when reading artifacts in owner's group by test
        Assertions.assertNotNull(testClient.listGroupArtifacts(groupId));
        // Check that API returns 200 OK when creating test's artifact in owner's group by test
        Assertions.assertTrue(testClient.createArtifact(groupId, testArtifactIdTest, type, initialContent));
        // Check that API returns 200 OK when updating test's artifact in owner's group by test
        Assertions.assertTrue(testClient.updateArtifact(groupId, testArtifactIdTest, updatedContent));
        // Check that API returns 204 No Content when deleting test's artifact in owner's group by test
        Assertions.assertTrue(testClient.deleteArtifact(groupId, testArtifactIdTest));
        // --- owner part finish
        // Check that API returns 204 No Content when deleting owner's artifact in owner's group by owner
        Assertions.assertTrue(ownerClient.deleteArtifact(groupId, testArtifactIdOwner));
    }
}
