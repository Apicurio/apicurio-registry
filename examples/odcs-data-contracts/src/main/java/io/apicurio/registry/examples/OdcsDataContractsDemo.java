package io.apicurio.registry.examples;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.DefaultVertxInstance;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.groups.item.artifacts.item.contract.promote.PromotePostRequestBody;
import io.apicurio.registry.rest.client.groups.item.artifacts.item.contract.promote.PromotePostRequestBodyTargetStage;
import io.apicurio.registry.rest.client.models.ContractMetadata;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.OdcsContractResult;
import io.apicurio.registry.rest.client.models.OdcsContractSummary;
import io.apicurio.registry.rest.client.models.VersionContent;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Demonstrates ODCS Data Contracts with Apicurio Registry using the Java SDK.
 *
 * Prerequisites:
 *   1. Start Apicurio Registry:
 *      docker run -p 8080:8080 \
 *        quay.io/apicurio/apicurio-registry:latest-snapshot
 *
 *   2. Run this example:
 *      mvn exec:java -Dexec.mainClass="io.apicurio.registry.examples.OdcsDataContractsDemo"
 *
 * This example walks through the complete ODCS data contract lifecycle:
 *   1. Register an Avro schema artifact
 *   2. Submit an ODCS v3.1 data contract referencing the schema
 *   3. List contracts in the group
 *   4. Retrieve the contract metadata
 *   5. Export the contract back as ODCS YAML
 *   6. Check the quality score
 *   7. Promote through deployment stages (DEV -> STAGE)
 *   8. Clean up
 */
public class OdcsDataContractsDemo {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v3";
    private static final String GROUP_ID = "odcs-example";
    private static final String ARTIFACT_ID = "OrderEvent";
    private static final String CONTRACT_ID = "orders-contract";

    private static final String AVRO_SCHEMA = """
            {
              "type": "record",
              "name": "OrderEvent",
              "namespace": "com.example.orders",
              "fields": [
                {"name": "orderId", "type": "string"},
                {"name": "customerEmail", "type": "string", "tags": ["PII", "EMAIL"]},
                {"name": "totalAmount", "type": "double"}
              ]
            }
            """;

    public static void main(String[] args) {
        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(REGISTRY_URL));

        try {
            System.out.println("=== ODCS Data Contracts Demo ===\n");

            // Step 1: Register the schema artifact
            System.out.println("1. Registering Avro schema artifact...");
            registerSchema(client);
            System.out.println("   Schema registered: " + GROUP_ID + "/" + ARTIFACT_ID);

            // Step 2: Submit the ODCS contract
            System.out.println("\n2. Submitting ODCS data contract...");
            String contractYaml = loadContractYaml();
            OdcsContractResult result = client.groups().byGroupId(GROUP_ID)
                    .contracts()
                    .post(new ByteArrayInputStream(
                            contractYaml.getBytes(StandardCharsets.UTF_8)));
            System.out.println("   Contract submitted: " + result.getContractId());
            System.out.println("   Rules applied: " + result.getProjection().getRulesApplied());
            System.out.println("   Labels applied: " + result.getProjection().getLabelsApplied());
            System.out.println("   Tags applied: " + result.getProjection().getTagsApplied());

            // Step 3: List contracts in the group
            System.out.println("\n3. Listing contracts in group '" + GROUP_ID + "'...");
            List<OdcsContractSummary> contracts = client.groups()
                    .byGroupId(GROUP_ID)
                    .contracts()
                    .get();
            if (contracts != null) {
                for (OdcsContractSummary summary : contracts) {
                    System.out.println("   - Contract: " + summary.getContractId()
                            + " (" + summary.getName() + ")");
                }
            }

            // Step 4: Get the contract metadata
            System.out.println("\n4. Getting contract metadata...");
            ContractMetadata metadata = client.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().metadata().get();
            System.out.println("   Status: " + metadata.getStatus());
            System.out.println("   Owner: " + metadata.getOwnerTeam());
            System.out.println("   Classification: " + metadata.getClassification());

            // Step 5: Export the contract as ODCS YAML
            System.out.println("\n5. Exporting contract as ODCS YAML...");
            InputStream exported = client.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().export().get();
            String exportedYaml = new BufferedReader(
                    new InputStreamReader(exported, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            System.out.println("   Exported YAML (first 200 chars):");
            System.out.println("   " + exportedYaml.substring(0,
                    Math.min(200, exportedYaml.length())) + "...");

            // Step 6: Check quality score
            System.out.println("\n6. Checking quality score...");
            var quality = client.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().quality().get(config -> {
                        config.queryParameters.contractId = CONTRACT_ID;
                    });
            System.out.println("   Overall: " + quality.getOverall());
            System.out.println("   Completeness: " + quality.getCompleteness());
            System.out.println("   Compliance: " + quality.getCompliance());
            System.out.println("   Stability: " + quality.getStability());

            // Step 7: Promote through stages
            System.out.println("\n7. Promoting contract through stages...");
            PromotePostRequestBody promoteDevBody = new PromotePostRequestBody();
            promoteDevBody.setContractId(CONTRACT_ID);
            promoteDevBody.setTargetStage(PromotePostRequestBodyTargetStage.DEV);
            var devResult = client.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().promote().post(promoteDevBody);
            System.out.println("   Promoted to: " + devResult.getStage());

            PromotePostRequestBody promoteStageBody = new PromotePostRequestBody();
            promoteStageBody.setContractId(CONTRACT_ID);
            promoteStageBody.setTargetStage(PromotePostRequestBodyTargetStage.STAGE);
            var stageResult = client.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().promote().post(promoteStageBody);
            System.out.println("   Promoted to: " + stageResult.getStage());

            // Step 8: Clean up
            System.out.println("\n8. Cleaning up...");
            client.groups().byGroupId(GROUP_ID)
                    .contracts().byContractId(CONTRACT_ID).delete();
            client.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID).delete();
            System.out.println("   Cleaned up contract and schema artifact.");

            System.out.println("\n=== Demo complete! ===");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            DefaultVertxInstance.close();
        }
    }

    private static void registerSchema(RegistryClient client) {
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(ARTIFACT_ID);
        createArtifact.setArtifactType("AVRO");
        CreateVersion firstVersion = new CreateVersion();
        VersionContent content = new VersionContent();
        content.setContent(AVRO_SCHEMA);
        content.setContentType("application/json");
        firstVersion.setContent(content);
        createArtifact.setFirstVersion(firstVersion);
        client.groups().byGroupId(GROUP_ID).artifacts().post(createArtifact);
    }

    private static String loadContractYaml() throws IOException {
        try (var stream = OdcsDataContractsDemo.class.getResourceAsStream(
                "/order-contract.yaml")) {
            if (stream == null) {
                throw new IOException("order-contract.yaml not found in classpath");
            }
            String yaml = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            return yaml
                    .replace("${GROUP_ID}", GROUP_ID)
                    .replace("${ARTIFACT_ID}", ARTIFACT_ID);
        }
    }
}
