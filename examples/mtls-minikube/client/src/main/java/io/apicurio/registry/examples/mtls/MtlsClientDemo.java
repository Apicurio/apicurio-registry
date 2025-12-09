package io.apicurio.registry.examples.mtls;

import io.apicurio.registry.client.common.DefaultVertxInstance;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.SystemInfo;
import io.apicurio.registry.rest.client.models.VersionContent;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Example client demonstrating mutual TLS (mTLS) authentication with Apicurio Registry.
 *
 * This client:
 * 1. Connects to a registry requiring mutual TLS authentication
 * 2. Uses client certificates to authenticate
 * 3. Performs basic registry operations (create, read, delete artifacts)
 *
 */
public class MtlsClientDemo {

    private static final String SAMPLE_JSON_SCHEMA = "{\n" +
            "    \"$id\": \"https://example.com/person.schema.json\",\n" +
            "    \"$schema\": \"http://json-schema.org/draft-07/schema#\",\n" +
            "    \"title\": \"Person\",\n" +
            "    \"type\": \"object\",\n" +
            "    \"properties\": {\n" +
            "        \"firstName\": {\n" +
            "            \"type\": \"string\",\n" +
            "            \"description\": \"The person's first name.\"\n" +
            "        },\n" +
            "        \"lastName\": {\n" +
            "            \"type\": \"string\",\n" +
            "            \"description\": \"The person's last name.\"\n" +
            "        },\n" +
            "        \"age\": {\n" +
            "            \"type\": \"integer\",\n" +
            "            \"description\": \"Age in years.\",\n" +
            "            \"minimum\": 0\n" +
            "        }\n" +
            "    }\n" +
            "}";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Apicurio Registry mTLS Client Demo");
        System.out.println("========================================");
        System.out.println();

        // Configuration
        String registryUrl = getEnvOrDefault("REGISTRY_URL", "https://localhost:8443/apis/registry/v3");
        String certsDir = getEnvOrDefault("CERTS_DIR", "../certs");

        String clientKeystorePath = certsDir + "/client-keystore.p12";
        String clientTruststorePath = certsDir + "/client-truststore.p12";
        String keystorePassword = getEnvOrDefault("KEYSTORE_PASSWORD", "apicurio");

        System.out.println("Configuration:");
        System.out.println("  Registry URL: " + registryUrl);
        System.out.println("  Client Keystore: " + clientKeystorePath);
        System.out.println("  Client Truststore: " + clientTruststorePath);
        System.out.println();

        // Validate certificate files exist
        if (!new File(clientKeystorePath).exists()) {
            System.err.println("ERROR: Client keystore not found at: " + clientKeystorePath);
            System.err.println("Please generate certificates first using: ../certs/generate-certs.sh");
            System.exit(1);
        }

        try {
            // Create client with mTLS configuration
            System.out.println("1. Creating registry client with mTLS...");
            RegistryClient client = createMtlsClient(registryUrl, clientKeystorePath,
                    clientTruststorePath, keystorePassword);
            System.out.println("   Client created successfully");
            System.out.println();

            // Test connection by getting system info
            System.out.println("2. Testing connection - getting system info...");
            SystemInfo systemInfo = client.system().info().get();
            System.out.println("   Connected successfully!");
            System.out.println("   Registry Name: " + systemInfo.getName());
            System.out.println("   Version: " + systemInfo.getVersion());
            System.out.println("   Description: " + systemInfo.getDescription());
            System.out.println();

            // Create a new artifact
            String artifactId = "mtls-demo-" + UUID.randomUUID().toString();
            System.out.println("3. Creating artifact: " + artifactId);

            CreateArtifact createArtifact = new CreateArtifact();
            createArtifact.setArtifactType("JSON");
            createArtifact.setArtifactId(artifactId);
            createArtifact.setFirstVersion(new CreateVersion());
            createArtifact.getFirstVersion().setContent(new VersionContent());
            createArtifact.getFirstVersion().getContent().setContent(SAMPLE_JSON_SCHEMA);
            createArtifact.getFirstVersion().getContent().setContentType("application/json");

            CreateArtifactResponse response = client.groups().byGroupId("default")
                    .artifacts().post(createArtifact, config -> {
                        config.queryParameters.ifExists = IfArtifactExists.FAIL;
                    });

            System.out.println("   Artifact created successfully");
            System.out.println("   Group: " + response.getArtifact().getGroupId());
            System.out.println("   Artifact ID: " + response.getArtifact().getArtifactId());
            System.out.println("   Version: " + response.getVersion().getVersion());
            System.out.println("   Global ID: " + response.getVersion().getGlobalId());
            System.out.println();

            // Retrieve the artifact
            System.out.println("4. Retrieving artifact...");
            var contentStream = client.groups().byGroupId("default")
                    .artifacts().byArtifactId(artifactId)
                    .versions().byVersionExpression("branch=latest")
                    .content().get();

            String retrievedContent = new String(contentStream.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("   Artifact retrieved successfully");
            System.out.println("   Content length: " + retrievedContent.length() + " bytes");
            System.out.println();

            // Delete the artifact
            System.out.println("5. Deleting artifact...");
            client.groups().byGroupId("default")
                    .artifacts().byArtifactId(artifactId)
                    .delete();
            System.out.println("   Artifact deleted successfully");
            System.out.println();

            System.out.println("========================================");
            System.out.println("Demo completed successfully!");
            System.out.println("========================================");
            System.out.println();
            System.out.println("This demo has successfully demonstrated:");
            System.out.println("  - Mutual TLS authentication with client certificates");
            System.out.println("  - Secure connection to Apicurio Registry");
            System.out.println("  - Basic registry operations (create, read, delete)");

        } catch (Exception e) {
            System.err.println();
            System.err.println("========================================");
            System.err.println("ERROR: Demo failed");
            System.err.println("========================================");
            System.err.println();

            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("certificate")) {
                System.err.println("This appears to be a certificate-related error.");
                System.err.println("Please ensure:");
                System.err.println("  1. Certificates were generated: ../certs/generate-certs.sh");
                System.err.println("  2. Registry is deployed with mTLS enabled");
                System.err.println("  3. Port forwarding is active: kubectl port-forward -n apicurio-mtls svc/apicurio-registry-mtls-app-service 8443:8443");
            }

            System.err.println();
            System.err.println("Error details:");
            e.printStackTrace();
            System.exit(1);
        } finally {
            // Clean up Vert.x instance
            DefaultVertxInstance.close();
        }
    }

    /**
     * Creates a registry client configured for mutual TLS authentication.
     */
    private static RegistryClient createMtlsClient(String registryUrl, String keystorePath,
            String truststorePath, String password) {

        RegistryClientOptions options = RegistryClientOptions.create(registryUrl)
                // Configure client keystore (for client authentication)
                .keystorePkcs12(keystorePath, password)
                // Configure truststore (to trust server certificate)
                .trustStorePkcs12(truststorePath, password);

        return RegistryClientFactory.create(options);
    }

    /**
     * Helper method to get environment variable or default value.
     */
    private static String getEnvOrDefault(String envVar, String defaultValue) {
        String value = System.getenv(envVar);
        return value != null ? value : defaultValue;
    }
}
