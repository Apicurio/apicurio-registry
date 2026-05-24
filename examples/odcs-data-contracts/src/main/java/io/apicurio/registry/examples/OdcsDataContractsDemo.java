package io.apicurio.registry.examples;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.DefaultVertxInstance;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.groups.item.artifacts.item.contract.audit.Audit;
import io.apicurio.registry.rest.client.groups.item.artifacts.item.contract.compatibilitygroup.CompatibilityGroupPutRequestBody;
import io.apicurio.registry.rest.client.groups.item.artifacts.item.contract.promote.PromotePostRequestBody;
import io.apicurio.registry.rest.client.groups.item.artifacts.item.contract.promote.PromotePostRequestBodyTargetStage;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.ContractMetadata;
import io.apicurio.registry.rest.client.models.ContractRule;
import io.apicurio.registry.rest.client.models.ContractRuleKind;
import io.apicurio.registry.rest.client.models.ContractRuleMode;
import io.apicurio.registry.rest.client.models.ContractRuleOnFailure;
import io.apicurio.registry.rest.client.models.ContractRuleSet;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Demonstrates the full ODCS Data Contracts lifecycle (Phases 1-8) with Apicurio Registry.
 *
 * Prerequisites:
 *   1. Build and start Apicurio Registry:
 *      ./mvnw clean install -DskipTests
 *      cd app/ && ../mvnw quarkus:dev
 *
 *   2. Run this example:
 *      cd examples/odcs-data-contracts
 *      mvn compile exec:java
 *
 * Steps demonstrated:
 *    1. Register Avro schema (v1)
 *    2. Submit ODCS contract
 *    3. List contracts
 *    4. Get contract metadata
 *    5. Export as ODCS YAML
 *    6. Check quality score
 *    7. Promote DEV -> STAGE
 *    8. Set contract rules (CEL + JSONata)
 *    9. Execute contract rules
 *   10. Set compatibility group
 *   11. Register v2 schema + migrate record
 *   12. Search contracts
 *   13. Get audit log
 *   14. Set global contract rules
 *   15. Start embedded Kafka (Testcontainers)
 *   16. Produce with contract rules — valid record (passes)
 *   17. Produce with contract rules — invalid record (blocked)
 *   18. Consume the valid message
 *   19. Stop Kafka
 *   20. Clean up
 */
public class OdcsDataContractsDemo {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v3";
    private static final String GROUP_ID = "odcs-example";
    private static final String ARTIFACT_ID = "OrderEvent";
    private static final String CONTRACT_ID = "orders-contract";

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final String AVRO_SCHEMA_V1 = """
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

    private static final String AVRO_SCHEMA_V2 = """
            {
              "type": "record",
              "name": "OrderEvent",
              "namespace": "com.example.orders",
              "fields": [
                {"name": "orderId", "type": "string"},
                {"name": "customerEmail", "type": "string", "tags": ["PII", "EMAIL"]},
                {"name": "totalAmount", "type": "double"},
                {"name": "currency", "type": "string", "default": "USD"}
              ]
            }
            """;

    public static void main(String[] args) {
        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(REGISTRY_URL));

        try {
            System.out.println("=== ODCS Data Contracts Demo (Phases 1-8) ===\n");

            // ==================== Phase 1-3: Foundation + ODCS ====================

            // Step 0: Clean up any leftover data from previous runs
            try {
                client.groups().byGroupId(GROUP_ID)
                        .contracts().byContractId(CONTRACT_ID).delete();
            } catch (Exception ignored) { }
            try {
                client.groups().byGroupId(GROUP_ID)
                        .artifacts().byArtifactId(ARTIFACT_ID).delete();
            } catch (Exception ignored) { }
            try {
                client.admin().contracts().ruleset().delete();
            } catch (Exception ignored) { }

            // Step 1: Register the schema artifact
            System.out.println("1. Registering Avro schema artifact (v1)...");
            registerSchema(client, AVRO_SCHEMA_V1);
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

            // Step 3: List contracts
            System.out.println("\n3. Listing contracts in group '" + GROUP_ID + "'...");
            List<OdcsContractSummary> contracts = client.groups()
                    .byGroupId(GROUP_ID).contracts().get();
            if (contracts != null) {
                for (OdcsContractSummary summary : contracts) {
                    System.out.println("   - " + summary.getContractId()
                            + " (" + summary.getName() + ")");
                }
            }

            // Step 4: Get contract metadata
            System.out.println("\n4. Getting contract metadata...");
            ContractMetadata metadata = client.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().metadata().get();
            System.out.println("   Status: " + metadata.getStatus());
            System.out.println("   Owner: " + metadata.getOwnerTeam());
            System.out.println("   Classification: " + metadata.getClassification());

            // Step 5: Export as ODCS YAML
            System.out.println("\n5. Exporting contract as ODCS YAML...");
            InputStream exported = client.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().export().get();
            String exportedYaml = new BufferedReader(
                    new InputStreamReader(exported, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            System.out.println("   Exported (first 200 chars): "
                    + exportedYaml.substring(0, Math.min(200, exportedYaml.length())) + "...");

            // ==================== Phase 4: Governance ====================

            // Step 6: Check quality score
            System.out.println("\n6. Checking quality score...");
            var quality = client.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().quality().get(config -> {
                        config.queryParameters.contractId = CONTRACT_ID;
                    });
            System.out.println("   Overall:      " + quality.getOverall());
            System.out.println("   Completeness: " + quality.getCompleteness());
            System.out.println("   Compliance:   " + quality.getCompliance());
            System.out.println("   Stability:    " + quality.getStability());

            // Step 7: Promote DEV -> STAGE
            System.out.println("\n7. Promoting contract (DEV -> STAGE)...");
            PromotePostRequestBody promoteDevBody = new PromotePostRequestBody();
            promoteDevBody.setContractId(CONTRACT_ID);
            promoteDevBody.setTargetStage(PromotePostRequestBodyTargetStage.DEV);
            client.groups().byGroupId(GROUP_ID).artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().promote().post(promoteDevBody);
            System.out.println("   Promoted to DEV");

            PromotePostRequestBody promoteStageBody = new PromotePostRequestBody();
            promoteStageBody.setContractId(CONTRACT_ID);
            promoteStageBody.setTargetStage(PromotePostRequestBodyTargetStage.STAGE);
            client.groups().byGroupId(GROUP_ID).artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().promote().post(promoteStageBody);
            System.out.println("   Promoted to STAGE");

            // ==================== Phase 5: Runtime Rules ====================

            // Step 8: Verify ODCS-projected rules and add migration rule
            System.out.println("\n8. Verifying ODCS-projected rules and adding migration rule...");
            ContractRuleSet existingRules = client.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID).contract().ruleset().get();
            System.out.println("   ODCS-projected domain rules: "
                    + (existingRules.getDomainRules() != null ? existingRules.getDomainRules().size() : 0));
            if (existingRules.getDomainRules() != null) {
                for (var r : existingRules.getDomainRules()) {
                    System.out.println("   - " + r.getName() + " (" + r.getType() + "): " + r.getExpr());
                }
            }

            ContractRule jsonataRule = new ContractRule();
            jsonataRule.setName("add-currency-default");
            jsonataRule.setKind(ContractRuleKind.TRANSFORM);
            jsonataRule.setType("JSONATA");
            jsonataRule.setMode(ContractRuleMode.UPGRADE);
            jsonataRule.setExpr("$ ~> |$|{\"currency\": \"USD\"}|");
            jsonataRule.setOnFailure(ContractRuleOnFailure.ERROR);
            jsonataRule.setDisabled(false);

            List<ContractRule> domainRules = existingRules.getDomainRules() != null
                    ? new ArrayList<>(existingRules.getDomainRules()) : new ArrayList<>();
            ContractRuleSet updatedRules = new ContractRuleSet();
            updatedRules.setDomainRules(domainRules);
            updatedRules.setMigrationRules(List.of(jsonataRule));

            client.groups().byGroupId(GROUP_ID).artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().ruleset().put(updatedRules);
            System.out.println("   Added JSONata migration rule (preserving ODCS domain rules)");

            // Step 9: Execute contract rules
            System.out.println("\n9. Executing contract rules against a test record...");
            var executeResponse = httpPost(
                    "/groups/" + GROUP_ID + "/artifacts/" + ARTIFACT_ID
                            + "/versions/branch%3Dlatest/contract/execute",
                    "{\"mode\":\"WRITE\",\"record\":{\"orderId\":\"ORD-123\","
                            + "\"customerEmail\":\"alice@example.com\",\"totalAmount\":99.99}}");
            System.out.println("   Result: " + executeResponse);

            // ==================== Phase 6: Migration ====================

            // Step 10: Set compatibility group
            System.out.println("\n10. Setting compatibility group...");
            CompatibilityGroupPutRequestBody compatBody = new CompatibilityGroupPutRequestBody();
            compatBody.setContractId(CONTRACT_ID);
            compatBody.setCompatibilityGroup("orders-v1");
            client.groups().byGroupId(GROUP_ID).artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().compatibilityGroup().put(compatBody);

            var compatResult = client.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().compatibilityGroup().get(config -> {
                        config.queryParameters.contractId = CONTRACT_ID;
                    });
            System.out.println("   Compatibility group: " + compatResult.getCompatibilityGroup());

            // Step 11: Register v2 and migrate
            System.out.println("\n11. Registering v2 schema and migrating a record...");
            registerSchemaVersion(client, AVRO_SCHEMA_V2, "2");
            System.out.println("   Schema v2 registered (adds 'currency' field)");

            var migrateResponse = httpPost(
                    "/groups/" + GROUP_ID + "/artifacts/" + ARTIFACT_ID + "/contract/migrate",
                    "{\"fromVersion\":\"1\",\"toVersion\":\"2\",\"record\":"
                            + "{\"orderId\":\"ORD-456\",\"customerEmail\":\"bob@example.com\","
                            + "\"totalAmount\":50.0}}");
            System.out.println("   Migration result: " + migrateResponse);

            // ==================== Phase 7: Integration ====================

            // Step 12: Search contracts
            System.out.println("\n12. Searching for contracts...");
            ArtifactSearchResults searchResults = client.search().contracts().get();
            System.out.println("   Found " + searchResults.getCount() + " contract(s)");

            // Step 13: Get audit log
            System.out.println("\n13. Getting contract audit log...");
            try {
                List<Audit> auditEntries = client.groups().byGroupId(GROUP_ID)
                        .artifacts().byArtifactId(ARTIFACT_ID)
                        .contract().audit().get(config -> {
                            config.queryParameters.limit = 5;
                            config.queryParameters.offset = 0;
                        });
                if (auditEntries != null) {
                    System.out.println("   Audit entries (" + auditEntries.size() + "):");
                    for (Audit entry : auditEntries) {
                        System.out.println("   - " + entry.getAction()
                                + " by " + entry.getPrincipal()
                                + " at " + entry.getCreatedOn());
                    }
                }
            } catch (Exception e) {
                System.out.println("   Audit log not available (DB upgrade 104 may not be applied yet)");
            }

            // ==================== Phase 8: Global Rules ====================

            // Step 14: Set global contract rules
            System.out.println("\n14. Setting global contract rules...");
            ContractRuleSet globalRuleSet = new ContractRuleSet();
            ContractRule globalRule = new ContractRule();
            globalRule.setName("global-non-empty-id");
            globalRule.setKind(ContractRuleKind.CONDITION);
            globalRule.setType("CEL");
            globalRule.setMode(ContractRuleMode.WRITE);
            globalRule.setExpr("size(orderId) > 0");
            globalRule.setOnFailure(ContractRuleOnFailure.ERROR);
            globalRule.setDisabled(false);
            globalRuleSet.setDomainRules(List.of(globalRule));
            globalRuleSet.setMigrationRules(new ArrayList<>());

            client.admin().contracts().ruleset().put(globalRuleSet);
            System.out.println("   Global ruleset set: 1 CEL rule (non-empty orderId)");

            ContractRuleSet retrievedGlobal = client.admin().contracts().ruleset().get();
            System.out.println("   Retrieved: " + retrievedGlobal.getDomainRules().size()
                    + " domain rule(s)");

            // ==================== SerDes Integration ====================

            // Step 15: Start embedded Kafka
            System.out.println("\n15. Starting embedded Kafka (Testcontainers)...");
            var kafka = new org.testcontainers.kafka.KafkaContainer("apache/kafka:3.8.1");
            kafka.start();
            String bootstrapServers = kafka.getBootstrapServers();
            System.out.println("   Kafka running at: " + bootstrapServers);

            String topic = "orders-topic";

            // Step 16: Produce with contract rules — valid message
            System.out.println("\n16. Producing Kafka message with contract rules enabled (valid record)...");
            {
                var schema = new org.apache.avro.Schema.Parser().parse(AVRO_SCHEMA_V1);
                var props = new java.util.Properties();
                props.put("bootstrap.servers", bootstrapServers);
                props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
                props.put("value.serializer", "io.apicurio.registry.serde.avro.AvroKafkaSerializer");
                props.put("apicurio.registry.url", REGISTRY_URL);
                props.put("apicurio.registry.auto-register", "true");
                props.put("apicurio.registry.artifact.group-id", GROUP_ID);
                props.put("apicurio.registry.artifact.artifact-id", ARTIFACT_ID);
                props.put("apicurio.registry.serde.contract-rules.enabled", "true");
                props.put("apicurio.registry.serde.contract-rules.fail-on-error", "true");

                var producer = new org.apache.kafka.clients.producer.KafkaProducer<String, org.apache.avro.generic.GenericRecord>(props);
                var validRecord = new org.apache.avro.generic.GenericData.Record(schema);
                validRecord.put("orderId", "ORD-KAFKA-001");
                validRecord.put("customerEmail", "alice@example.com");
                validRecord.put("totalAmount", 99.99);

                var record = new org.apache.kafka.clients.producer.ProducerRecord<>(
                        topic, "key1", (org.apache.avro.generic.GenericRecord) validRecord);
                producer.send(record).get();
                System.out.println("   Valid message sent successfully (totalAmount=99.99, rule passed)");
                producer.close();
            }

            // Step 17: Produce with contract rules — invalid message
            System.out.println("\n17. Producing Kafka message with contract rules enabled (invalid record)...");
            {
                var schema = new org.apache.avro.Schema.Parser().parse(AVRO_SCHEMA_V1);
                var props = new java.util.Properties();
                props.put("bootstrap.servers", bootstrapServers);
                props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
                props.put("value.serializer", "io.apicurio.registry.serde.avro.AvroKafkaSerializer");
                props.put("apicurio.registry.url", REGISTRY_URL);
                props.put("apicurio.registry.auto-register", "true");
                props.put("apicurio.registry.artifact.group-id", GROUP_ID);
                props.put("apicurio.registry.artifact.artifact-id", ARTIFACT_ID);
                props.put("apicurio.registry.serde.contract-rules.enabled", "true");
                props.put("apicurio.registry.serde.contract-rules.fail-on-error", "true");

                var producer = new org.apache.kafka.clients.producer.KafkaProducer<String, org.apache.avro.generic.GenericRecord>(props);
                var invalidRecord = new org.apache.avro.generic.GenericData.Record(schema);
                invalidRecord.put("orderId", "ORD-KAFKA-002");
                invalidRecord.put("customerEmail", "bob@example.com");
                invalidRecord.put("totalAmount", -5.00);

                try {
                    var record = new org.apache.kafka.clients.producer.ProducerRecord<>(
                            topic, "key2", (org.apache.avro.generic.GenericRecord) invalidRecord);
                    producer.send(record).get();
                    System.out.println("   ERROR: Message should have been rejected!");
                } catch (Exception e) {
                    System.out.println("   Message correctly rejected by contract rule!");
                    String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    System.out.println("   Error: " + cause);
                }
                producer.close();
            }

            // Step 18: Consume the valid message
            System.out.println("\n18. Consuming messages from Kafka...");
            {
                var props = new java.util.Properties();
                props.put("bootstrap.servers", bootstrapServers);
                props.put("group.id", "odcs-demo-consumer");
                props.put("auto.offset.reset", "earliest");
                props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
                props.put("value.deserializer", "io.apicurio.registry.serde.avro.AvroKafkaDeserializer");
                props.put("apicurio.registry.url", REGISTRY_URL);
                props.put("apicurio.registry.serde.contract-rules.enabled", "true");
                props.put("apicurio.registry.serde.contract-rules.fail-on-error", "true");

                var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, Object>(props);
                consumer.subscribe(java.util.Collections.singletonList(topic));
                int totalConsumed = 0;
                for (int attempt = 0; attempt < 5; attempt++) {
                    try {
                        var records = consumer.poll(java.time.Duration.ofSeconds(5));
                        for (var r : records) {
                            System.out.println("   - Key: " + r.key() + ", Value: " + r.value());
                            totalConsumed++;
                        }
                        if (totalConsumed > 0) break;
                    } catch (Exception e) {
                        System.out.println("   Consumer error (attempt " + (attempt + 1) + "): " + e.getMessage());
                        Throwable cause = e;
                        while (cause.getCause() != null) cause = cause.getCause();
                        System.out.println("   Root cause: " + cause.getClass().getName() + ": " + cause.getMessage());
                        break;
                    }
                }
                System.out.println("   Consumed " + totalConsumed + " message(s)");
                consumer.close();
            }

            // Step 19: Stop Kafka
            System.out.println("\n19. Stopping embedded Kafka...");
            kafka.stop();
            System.out.println("   Kafka stopped.");

            // ==================== Clean Up ====================

            // Step 20: Clean up (commented out to allow UI testing)
            // System.out.println("\n20. Cleaning up...");
            // client.admin().contracts().ruleset().delete();
            // client.groups().byGroupId(GROUP_ID)
            //         .contracts().byContractId(CONTRACT_ID).delete();
            // client.groups().byGroupId(GROUP_ID)
            //         .artifacts().byArtifactId(ARTIFACT_ID).delete();
            // System.out.println("   Cleaned up global rules, contract, and schema.");
            System.out.println("\n20. Cleanup skipped — data left for UI testing.");

            System.out.println("\n=== Demo complete! ===");
            System.out.println("\nTo explore the UI:");
            System.out.println("  1. Start the UI dev server: cd ui/ui-app && npm run dev");
            System.out.println("  2. Open http://localhost:8888");
            System.out.println("  3. Navigate: Explore > odcs-example > OrderEvent > Contract tab");

        } catch (Exception e) {
            System.err.println("\nError: " + e.getMessage()); // NOSONAR — demo code, not production
        } finally {
            DefaultVertxInstance.close();
        }
    }

    private static void registerSchema(RegistryClient client, String schema) {
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(ARTIFACT_ID);
        createArtifact.setArtifactType("AVRO");
        CreateVersion firstVersion = new CreateVersion();
        firstVersion.setVersion("1");
        VersionContent content = new VersionContent();
        content.setContent(schema);
        content.setContentType("application/json");
        firstVersion.setContent(content);
        createArtifact.setFirstVersion(firstVersion);
        client.groups().byGroupId(GROUP_ID).artifacts().post(createArtifact);
    }

    private static void registerSchemaVersion(RegistryClient client, String schema,
            String version) {
        CreateVersion createVersion = new CreateVersion();
        createVersion.setVersion(version);
        VersionContent content = new VersionContent();
        content.setContent(schema);
        content.setContentType("application/json");
        createVersion.setContent(content);
        client.groups().byGroupId(GROUP_ID).artifacts().byArtifactId(ARTIFACT_ID)
                .versions().post(createVersion);
    }

    private static String httpPost(String path, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(REGISTRY_URL + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
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
