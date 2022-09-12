package io.apicurio.registry.systemtests.api.features;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicurio.registry.systemtests.client.ApicurioRegistryApiClient;
import io.apicurio.registry.systemtests.client.ArtifactContent;
import io.apicurio.registry.systemtests.client.ArtifactType;
import io.apicurio.registry.systemtests.client.AuthMethod;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import org.junit.jupiter.api.Assertions;

public class CreateArtifact {
    public static void testCreateArtifact(ApicurioRegistry apicurioRegistry) {
        testCreateArtifact(apicurioRegistry, null, null, false);
    }

    public static void testCreateArtifact(
            ApicurioRegistry apicurioRegistry,
            String username,
            String password,
            boolean useToken
    ) {
        // Wait for readiness of Apicurio Registry hostname
        Assertions.assertTrue(ApicurioRegistryUtils.waitApicurioRegistryHostnameReady(apicurioRegistry));

        // Prepare necessary variables
        String artifactGroupId = "registry-create-test-group";
        String artifactId = "registry-create-test-id";
        String artifactContent = ArtifactContent.DEFAULT_AVRO;
        String hostname = ApicurioRegistryUtils.getApicurioRegistryHostname(apicurioRegistry);

        // Get API test client
        ApicurioRegistryApiClient testClient = new ApicurioRegistryApiClient(hostname);

        // If we want to use access token
        if (useToken) {
            // Update API test client with token
            testClient.setToken(KeycloakUtils.getAccessToken(apicurioRegistry, username, password));
            // Set authentication method
            testClient.setAuthMethod(AuthMethod.TOKEN);
        }

        // Wait for readiness of API
        Assertions.assertTrue(testClient.waitServiceAvailable());

        // Create artifact
        Assertions.assertTrue(
                testClient.createArtifact(artifactGroupId, artifactId, ArtifactType.AVRO, artifactContent)
        );
    }
}
