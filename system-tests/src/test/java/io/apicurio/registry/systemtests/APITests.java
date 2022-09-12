package io.apicurio.registry.systemtests;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicurio.registry.systemtests.client.ApicurioRegistryApiClient;
import io.apicurio.registry.systemtests.client.ArtifactContent;
import io.apicurio.registry.systemtests.client.ArtifactList;
import io.apicurio.registry.systemtests.client.ArtifactType;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import io.apicurio.registry.systemtests.framework.LoggerUtils;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;

public class APITests {
    protected static Logger LOGGER = LoggerUtils.getLogger();

    public static void run(ApicurioRegistry apicurioRegistry) {
        run(apicurioRegistry, null, null, false);
    }

    public static void run(ApicurioRegistry apicurioRegistry, String username, String password, boolean useToken) {
        LOGGER.info("Running API tests...");

        // Wait for readiness of Apicurio Registry hostname
        // Prepare necessary variables
        String artifactGroupId = "registry-test-group";
        String artifactId = "registry-test-id";
        String artifactContent = ArtifactContent.DEFAULT_AVRO;
        String hostname = ApicurioRegistryUtils.getApicurioRegistryHostname(apicurioRegistry);

        LOGGER.info("Hostname: {}", hostname);

        // Get API client
        ApicurioRegistryApiClient client = new ApicurioRegistryApiClient(hostname);

        // If we want to use access token
        if (useToken) {
            // Update API client with token
            client.setToken(KeycloakUtils.getAccessToken(apicurioRegistry, username, password));
        }

        // Wait for readiness of API
        Assertions.assertTrue(client.waitServiceAvailable());

        // List artifacts
        LOGGER.info("Listing artifacts...");
        ArtifactList artifactList = client.listArtifacts();
        artifactList.printArtifactList(LOGGER);
        // Check that artifact does not exist yet
        Assertions.assertFalse(artifactList.contains(artifactGroupId, artifactId));

        // Create artifact
        LOGGER.info("Creating artifact...");
        Assertions.assertTrue(client.createArtifact(artifactGroupId, artifactId, ArtifactType.AVRO, artifactContent));

        // List artifacts
        LOGGER.info("Listing artifacts...");
        artifactList = client.listArtifacts();
        artifactList.printArtifactList(LOGGER);
        // Check creation of artifact
        Assertions.assertTrue(artifactList.contains(artifactGroupId, artifactId));
        Assertions.assertEquals(client.readArtifactContent(artifactGroupId, artifactId), artifactContent);

        // Delete artifact
        LOGGER.info("Deleting artifact...");
        Assertions.assertTrue(client.deleteArtifact(artifactGroupId, artifactId));

        // List artifacts
        LOGGER.info("Listing artifacts...");
        artifactList = client.listArtifacts();
        artifactList.printArtifactList(LOGGER);
        // Check deletion of artifact
        Assertions.assertFalse(artifactList.contains(artifactGroupId, artifactId));

        // If we use token
        if (useToken) {
            // Check unauthorized API call
            LOGGER.info("Checking unauthorized API call...");
            Assertions.assertTrue(client.checkUnauthorized());
        }
    }
}
