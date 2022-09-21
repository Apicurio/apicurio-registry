package io.apicurio.registry.systemtests.api.features;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicurio.registry.systemtests.client.ApicurioRegistryApiClient;
import io.apicurio.registry.systemtests.client.ArtifactContent;
import io.apicurio.registry.systemtests.client.ArtifactList;
import io.apicurio.registry.systemtests.client.ArtifactType;
import io.apicurio.registry.systemtests.client.AuthMethod;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import org.junit.jupiter.api.Assertions;

public class CreateReadUpdateDelete {
    public static void testCreateReadUpdateDelete(ApicurioRegistry apicurioRegistry) {
        testCreateReadUpdateDelete(apicurioRegistry, null, null, false);
    }

    public static void testCreateReadUpdateDelete(
            ApicurioRegistry apicurioRegistry,
            String username,
            String password,
            boolean useToken
    ) {
        // Wait for readiness of Apicurio Registry hostname
        Assertions.assertTrue(ApicurioRegistryUtils.waitApicurioRegistryHostnameReady(apicurioRegistry));

        // Prepare necessary variables
        String artifactGroupId = "registry-test-group";
        String artifactId = "registry-test-id";
        String artifactContent = ArtifactContent.DEFAULT_AVRO;
        String updatedArtifactContent = "{\"key\":\"id\"}";
        String hostname = ApicurioRegistryUtils.getApicurioRegistryHostname(apicurioRegistry);

        // Get API client
        ApicurioRegistryApiClient client = new ApicurioRegistryApiClient(hostname);

        // If we want to use access token
        if (useToken) {
            // Update API client with token
            client.setToken(KeycloakUtils.getAccessToken(apicurioRegistry, username, password));
            // Set authentication method
            client.setAuthMethod(AuthMethod.TOKEN);
        }

        // Wait for readiness of API
        Assertions.assertTrue(client.waitServiceAvailable());

        // List artifacts
        ArtifactList artifactList = client.listArtifacts();
        Assertions.assertNotNull(artifactList);
        // Check that artifact does not exist yet
        Assertions.assertFalse(artifactList.contains(artifactGroupId, artifactId));

        // Create artifact
        Assertions.assertTrue(client.createArtifact(artifactGroupId, artifactId, ArtifactType.AVRO, artifactContent));

        // List artifacts
        artifactList = client.listArtifacts();
        Assertions.assertNotNull(artifactList);
        // Check creation of artifact
        Assertions.assertTrue(artifactList.contains(artifactGroupId, artifactId));
        Assertions.assertEquals(client.readArtifactContent(artifactGroupId, artifactId), artifactContent);

        // Update artifact
        Assertions.assertTrue(client.updateArtifact(artifactGroupId, artifactId, updatedArtifactContent));
        // Check update of artifact
        Assertions.assertEquals(client.readArtifactContent(artifactGroupId, artifactId), updatedArtifactContent);

        // Delete artifact
        Assertions.assertTrue(client.deleteArtifact(artifactGroupId, artifactId));

        // List artifacts
        artifactList = client.listArtifacts();
        Assertions.assertNotNull(artifactList);
        // Check deletion of artifact
        Assertions.assertFalse(artifactList.contains(artifactGroupId, artifactId));

        // If we use token
        if (useToken) {
            // Check unauthorized API call
            Assertions.assertTrue(client.checkUnauthorizedFake());
        }
    }
}
