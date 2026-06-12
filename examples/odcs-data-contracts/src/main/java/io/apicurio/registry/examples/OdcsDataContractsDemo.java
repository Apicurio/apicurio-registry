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
import java.util.Scanner;
import java.util.stream.Collectors;

public class OdcsDataContractsDemo {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v3";
    private static final String GROUP_ID = "odcs-example";
    private static final String ARTIFACT_ID = "OrderEvent";
    private static final String CONTRACT_ID = "orders-contract";

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final String B = "\033[1m";
    private static final String G = "\033[32m";
    private static final String R = "\033[31m";
    private static final String C = "\033[36m";
    private static final String Y = "\033[33m";
    private static final String M = "\033[35m";
    private static final String X = "\033[0m";

    private static boolean interactive = true;
    private static Scanner scanner;

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
        interactive = !(args.length > 0 && "--no-pause".equals(args[0]));
        if (interactive) {
            scanner = new Scanner(System.in);
        }

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(REGISTRY_URL));

        try {
            banner("ODCS Data Contracts Demo", "Apicurio Registry 3.3.0 — Phases 1-8");

            cleanup(client);

            // ═══════════════════ Phase 1-3: Foundation + ODCS ═══════════════════

            phase("PHASE 1-3", "Foundation + ODCS Support");

            step(1, "Register Avro Schema (v1)",
                    "Avro schema with inline PII tags → automatic tag extraction");
            registerSchema(client, AVRO_SCHEMA_V1);
            ok("Schema registered: " + GROUP_ID + "/" + ARTIFACT_ID);
            data("Fields: orderId (string), customerEmail (string, PII), totalAmount (double)");
            data("Inline tags: customerEmail → [PII, EMAIL]");

            step(2, "Submit ODCS Contract",
                    "ODCS v3.1 YAML projected onto the schema artifact");
            String contractYaml = loadContractYaml();
            OdcsContractResult result = client.groups().byGroupId(GROUP_ID)
                    .contracts()
                    .post(new ByteArrayInputStream(
                            contractYaml.getBytes(StandardCharsets.UTF_8)));
            ok("Contract submitted: " + result.getContractId());
            data("Rules applied:  " + result.getProjection().getRulesApplied()
                    + "  (CEL from quality.accuracy)");
            data("Labels applied: " + result.getProjection().getLabelsApplied()
                    + "  (owner, status, classification, SLA)");
            data("Tags applied:   " + result.getProjection().getTagsApplied()
                    + "  (PII, EMAIL on customerEmail)");

            step(3, "Inspect: List, Metadata, Export",
                    "Verify what the projection created on the schema artifact");
            List<OdcsContractSummary> contracts = client.groups()
                    .byGroupId(GROUP_ID).contracts().get();
            if (contracts != null) {
                for (OdcsContractSummary summary : contracts) {
                    data("Contract: " + summary.getContractId()
                            + " (" + summary.getName() + ")");
                }
            }

            ContractMetadata metadata = client.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().metadata().get();
            data("Status:         " + metadata.getStatus());
            data("Owner:          " + metadata.getOwnerTeam());
            data("Classification: " + metadata.getClassification());

            InputStream exported = client.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().export().get();
            String exportedYaml = new BufferedReader(
                    new InputStreamReader(exported, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            ok("ODCS export: " + exportedYaml.length() + " chars");

            // ═══════════════════ Phase 4: Governance ═══════════════════

            phase("PHASE 4", "Governance & Quality");

            step(4, "Quality Score",
                    "Weighted score: completeness 30%, compliance 40%, stability 30%");
            var quality = client.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().quality().get(config -> {
                        config.queryParameters.contractId = CONTRACT_ID;
                    });
            data("Overall:      " + quality.getOverall());
            data("Completeness: " + quality.getCompleteness() + "  (30% weight)");
            data("Compliance:   " + quality.getCompliance() + "  (40% weight)");
            data("Stability:    " + quality.getStability() + "  (30% weight)");

            step(5, "Promote DEV → STAGE",
                    "Promotion pipeline: DEV → STAGE → PROD");
            PromotePostRequestBody promoteDevBody = new PromotePostRequestBody();
            promoteDevBody.setContractId(CONTRACT_ID);
            promoteDevBody.setTargetStage(PromotePostRequestBodyTargetStage.DEV);
            client.groups().byGroupId(GROUP_ID).artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().promote().post(promoteDevBody);
            ok("Promoted to DEV");

            PromotePostRequestBody promoteStageBody = new PromotePostRequestBody();
            promoteStageBody.setContractId(CONTRACT_ID);
            promoteStageBody.setTargetStage(PromotePostRequestBodyTargetStage.STAGE);
            client.groups().byGroupId(GROUP_ID).artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().promote().post(promoteStageBody);
            ok("Promoted to STAGE");

            // ═══════════════════ Phase 5: Runtime Rules ═══════════════════

            phase("PHASE 5", "Runtime Rules (CEL)");

            step(6, "Verify ODCS-Projected Rules + Add Migration Rule",
                    "ODCS quality rules → CEL rules. JSONata for schema migration.");
            ContractRuleSet existingRules = client.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID).contract().ruleset().get();
            if (existingRules.getDomainRules() != null) {
                for (var r : existingRules.getDomainRules()) {
                    data("ODCS rule: " + r.getName() + " [" + r.getType()
                            + "] → " + r.getExpr());
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
            ok("Added JSONata migration rule: add-currency-default");

            step(7, "Execute CEL Rules Against Data",
                    "Server-side rule execution via POST .../contract/execute");
            var passResponse = httpPost(
                    "/groups/" + GROUP_ID + "/artifacts/" + ARTIFACT_ID
                            + "/versions/branch%3Dlatest/contract/execute",
                    "{\"mode\":\"WRITE\",\"record\":{\"orderId\":\"ORD-123\","
                            + "\"customerEmail\":\"alice@example.com\",\"totalAmount\":99.99}}");
            ok("Valid record (totalAmount=99.99): " + passResponse);

            var failResponse = httpPost(
                    "/groups/" + GROUP_ID + "/artifacts/" + ARTIFACT_ID
                            + "/versions/branch%3Dlatest/contract/execute",
                    "{\"mode\":\"WRITE\",\"record\":{\"orderId\":\"ORD-456\","
                            + "\"customerEmail\":\"bob@example.com\",\"totalAmount\":-5.0}}");
            fail("Invalid record (totalAmount=-5.0): " + failResponse);

            // ═══════════════════ Phase 6: Migration ═══════════════════

            phase("PHASE 6", "Schema Migration");

            step(8, "Compatibility Group + Schema v2",
                    "Compatibility groups scope version history. JSONata transforms data.");
            CompatibilityGroupPutRequestBody compatBody = new CompatibilityGroupPutRequestBody();
            compatBody.setContractId(CONTRACT_ID);
            compatBody.setCompatibilityGroup("orders-v1");
            client.groups().byGroupId(GROUP_ID).artifacts().byArtifactId(ARTIFACT_ID)
                    .contract().compatibilityGroup().put(compatBody);
            ok("Compatibility group set: orders-v1");

            registerSchemaVersion(client, AVRO_SCHEMA_V2, "2");
            ok("Schema v2 registered (adds 'currency' field)");

            var migrateResponse = httpPost(
                    "/groups/" + GROUP_ID + "/artifacts/" + ARTIFACT_ID + "/contract/migrate",
                    "{\"fromVersion\":\"1\",\"toVersion\":\"2\",\"record\":"
                            + "{\"orderId\":\"ORD-456\",\"customerEmail\":\"bob@example.com\","
                            + "\"totalAmount\":50.0}}");
            ok("Migration v1→v2: " + migrateResponse);

            // ═══════════════════ Phase 7: Integration ═══════════════════

            phase("PHASE 7", "Integration (Search, Audit)");

            step(9, "Search Contracts + Audit Log",
                    "Search across artifacts. Audit trail for compliance.");
            ArtifactSearchResults searchResults = client.search().contracts().get();
            ok("Found " + searchResults.getCount() + " contract(s)");

            try {
                List<Audit> auditEntries = client.groups().byGroupId(GROUP_ID)
                        .artifacts().byArtifactId(ARTIFACT_ID)
                        .contract().audit().get(config -> {
                            config.queryParameters.limit = 5;
                            config.queryParameters.offset = 0;
                        });
                if (auditEntries != null) {
                    ok("Audit log (" + auditEntries.size() + " entries):");
                    for (Audit entry : auditEntries) {
                        data("  " + entry.getAction() + " by " + entry.getPrincipal()
                                + " at " + entry.getCreatedOn());
                    }
                }
            } catch (Exception e) {
                warn("Audit log: " + e.getMessage());
            }

            // ═══════════════════ Phase 8: Global Rules ═══════════════════

            phase("PHASE 8", "Global Rules");

            step(10, "Set Global Contract Rules",
                    "Organization-wide rules. Precedence: global → artifact → version");
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
            ok("Global rule set: non-empty orderId (CEL)");

            ContractRuleSet retrievedGlobal = client.admin().contracts().ruleset().get();
            data("Global rules: " + retrievedGlobal.getDomainRules().size() + " domain rule(s)");

            // ═══════════════════ SerDes Integration ═══════════════════

            phase("KAFKA SERDES", "Contract Rules in the Kafka Pipeline");

            step(11, "Start Embedded Kafka",
                    "Testcontainers Kafka — contract rules enforced during produce/consume");
            var kafka = new org.testcontainers.kafka.KafkaContainer("apache/kafka:3.8.1");
            kafka.start();
            String bootstrapServers = kafka.getBootstrapServers();
            ok("Kafka running at: " + bootstrapServers);

            String topic = "orders-topic";

            step(12, "Produce Valid Message",
                    "contract-rules.enabled=true — CEL rules pass, message sent");
            {
                var schema = new org.apache.avro.Schema.Parser().parse(AVRO_SCHEMA_V1);
                var props = kafkaProducerProps(bootstrapServers);
                var producer = new org.apache.kafka.clients.producer.KafkaProducer<String,
                        org.apache.avro.generic.GenericRecord>(props);
                var validRecord = new org.apache.avro.generic.GenericData.Record(schema);
                validRecord.put("orderId", "ORD-KAFKA-001");
                validRecord.put("customerEmail", "alice@example.com");
                validRecord.put("totalAmount", 99.99);

                var record = new org.apache.kafka.clients.producer.ProducerRecord<>(
                        topic, "key1", (org.apache.avro.generic.GenericRecord) validRecord);
                producer.send(record).get();
                ok("Message sent: orderId=ORD-KAFKA-001, totalAmount=99.99");
                producer.close();
            }

            step(13, "Produce Invalid Message",
                    "totalAmount=-5.0 violates CEL rule → serialization rejected");
            {
                var schema = new org.apache.avro.Schema.Parser().parse(AVRO_SCHEMA_V1);
                var props = kafkaProducerProps(bootstrapServers);
                var producer = new org.apache.kafka.clients.producer.KafkaProducer<String,
                        org.apache.avro.generic.GenericRecord>(props);
                var invalidRecord = new org.apache.avro.generic.GenericData.Record(schema);
                invalidRecord.put("orderId", "ORD-KAFKA-002");
                invalidRecord.put("customerEmail", "bob@example.com");
                invalidRecord.put("totalAmount", -5.00);

                try {
                    var record = new org.apache.kafka.clients.producer.ProducerRecord<>(
                            topic, "key2", (org.apache.avro.generic.GenericRecord) invalidRecord);
                    producer.send(record).get();
                    fail("ERROR: Message should have been rejected!");
                } catch (Exception e) {
                    ok("Message correctly REJECTED by contract rule!");
                    String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    fail("Violation: " + cause);
                }
                producer.close();
            }

            step(14, "Consume Valid Message",
                    "Consumer reads the valid message — read-side rules pass");
            {
                var props = new java.util.Properties();
                props.put("bootstrap.servers", bootstrapServers);
                props.put("group.id", "odcs-demo-consumer");
                props.put("auto.offset.reset", "earliest");
                props.put("key.deserializer",
                        "org.apache.kafka.common.serialization.StringDeserializer");
                props.put("value.deserializer",
                        "io.apicurio.registry.serde.avro.AvroKafkaDeserializer");
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
                            ok("Consumed: key=" + r.key() + " value=" + r.value());
                            totalConsumed++;
                        }
                        if (totalConsumed > 0) break;
                    } catch (Exception e) {
                        fail("Consumer error: " + e.getMessage());
                        break;
                    }
                }
                data("Total consumed: " + totalConsumed + " message(s)");
                consumer.close();
            }

            System.out.println("\n" + Y + "  Stopping Kafka..." + X);
            kafka.stop();
            ok("Kafka stopped.");

            // ═══════════════════ Done ═══════════════════

            System.out.println("\n" + B + G
                    + "══════════════════════════════════════════════════════" + X);
            System.out.println(B + G
                    + "  ✓ Demo complete!" + X);
            System.out.println(B + G
                    + "══════════════════════════════════════════════════════" + X);
            System.out.println();
            System.out.println(C + "  Next: Open the UI for the UI Tour slide" + X);
            System.out.println(C + "    1. cd ui/ui-app && npm run dev" + X);
            System.out.println(C + "    2. Open http://localhost:8888" + X);
            System.out.println(C + "    3. Explore > odcs-example > OrderEvent > Contract tab" + X);
            System.out.println();

        } catch (Exception e) {
            System.err.println("\n" + R + "Error: " + e.getMessage() + X);
        } finally {
            if (scanner != null) scanner.close();
            DefaultVertxInstance.close();
        }
    }

    private static void banner(String title, String subtitle) {
        System.out.println();
        System.out.println(B + M + "╔══════════════════════════════════════════════════════╗" + X);
        System.out.println(B + M + "║  " + title + X);
        System.out.println(B + M + "║  " + Y + subtitle + X);
        System.out.println(B + M + "╚══════════════════════════════════════════════════════╝" + X);
        System.out.println();
    }

    private static void phase(String tag, String description) {
        System.out.println();
        System.out.println(B + Y + "  ┌──────────────────────────────────────────────────┐" + X);
        System.out.println(B + Y + "  │  " + tag + ": " + description + X);
        System.out.println(B + Y + "  └──────────────────────────────────────────────────┘" + X);
    }

    private static void step(int num, String title, String description) {
        System.out.println();
        System.out.println(B + C + "  ══════════════════════════════════════════════════════" + X);
        System.out.println(B + C + "  ▶ Step " + num + ": " + title + X);
        System.out.println("    " + description);
        if (interactive) {
            System.out.print(Y + "    Press ENTER to continue..." + X);
            scanner.nextLine();
        }
        System.out.println(B + C + "  ══════════════════════════════════════════════════════" + X);
    }

    private static void ok(String msg) {
        System.out.println("  " + G + "✓ " + msg + X);
    }

    private static void fail(String msg) {
        System.out.println("  " + R + "✗ " + msg + X);
    }

    private static void data(String msg) {
        System.out.println("  " + C + "  " + msg + X);
    }

    private static void warn(String msg) {
        System.out.println("  " + Y + "⚠ " + msg + X);
    }

    private static void cleanup(RegistryClient client) {
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
    }

    private static java.util.Properties kafkaProducerProps(String bootstrapServers) {
        var props = new java.util.Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer",
                "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer",
                "io.apicurio.registry.serde.avro.AvroKafkaSerializer");
        props.put("apicurio.registry.url", REGISTRY_URL);
        props.put("apicurio.registry.auto-register", "true");
        props.put("apicurio.registry.artifact.group-id", GROUP_ID);
        props.put("apicurio.registry.artifact.artifact-id", ARTIFACT_ID);
        props.put("apicurio.registry.serde.contract-rules.enabled", "true");
        props.put("apicurio.registry.serde.contract-rules.fail-on-error", "true");
        return props;
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
