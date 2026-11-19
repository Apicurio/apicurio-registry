package io.apicurio.registry.noprofile.serde;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractClientFacadeTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.resolver.client.RegistryClientFacadeImpl;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.avro.DefaultAvroDatumProvider;
import io.apicurio.registry.serde.avro.strategy.TopicRecordIdStrategy;
import io.apicurio.registry.serde.avro.AvroSerdeConfig;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

@QuarkusTest
public class ContractRuleSerdeTest extends AbstractClientFacadeTestBase {

    private static final String SCHEMA = "{\"type\":\"record\",\"name\":\"OrderEvent\","
            + "\"namespace\":\"com.example\","
            + "\"fields\":["
            + "{\"name\":\"orderId\",\"type\":\"string\"},"
            + "{\"name\":\"totalAmount\",\"type\":\"double\"}"
            + "]}";

    private void setContractRuleset(String groupId, String artifactId, String rulesetJson)
            throws Exception {
        String url = TestUtils.getRegistryV3ApiUrl(testPort)
                + "/groups/" + groupId + "/artifacts/" + artifactId + "/contract/ruleset";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(rulesetJson)).build();
        HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
    }

    private void send(String method, String path, String json) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(TestUtils.getRegistryV3ApiUrl(testPort) + path))
                .header("Content-Type", "application/json");
        req = json == null ? req.method(method, HttpRequest.BodyPublishers.noBody())
                : req.method(method, HttpRequest.BodyPublishers.ofString(json));
        HttpResponse<String> response = HttpClient.newHttpClient().send(req.build(),
                HttpResponse.BodyHandlers.ofString());
        Assertions.assertTrue(response.statusCode() < 300, method + " " + path + ": " + response.body());
    }

    private static String nonEmptyOrderIdRule(String expr) {
        return "{\"domainRules\":[{\"name\":\"order-id-present\",\"kind\":\"CONDITION\",\"type\":\"CEL\","
                + "\"mode\":\"WRITE\",\"expr\":\"" + expr + "\",\"onFailure\":\"ERROR\"}],\"migrationRules\":[]}";
    }

    private Serializer<GenericData.Record> contractSerializer(String groupId) {
        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV3ApiUrl(testPort));
        config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
        config.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
        config.put(SerdeConfig.CONTRACT_RULES_ENABLED, "true");
        config.put(SerdeConfig.CONTRACT_RULES_FAIL_ON_ERROR, "true");
        Serializer<GenericData.Record> serializer = new AvroKafkaSerializer<>();
        serializer.configure(config, false);
        return serializer;
    }

    @Test
    public void testFacadeTellsMissingRulesetsFromFailedFetches() {
        // An artifact without rules: an empty ruleset
        RegistryClientFacadeImpl facade = new RegistryClientFacadeImpl(isolatedClientV3);
        var empty = facade.getContractRuleset(TestUtils.generateGroupId(), "no-rules");
        Assertions.assertTrue(empty.getDomainRules() == null || empty.getDomainRules().isEmpty());

        // Unreachable registry: an exception, never "no rules"
        RegistryClientFacadeImpl unreachable = new RegistryClientFacadeImpl(RegistryClientFactory.create(
                RegistryClientOptions.create().registryUrl("http://localhost:1/apis/registry/v3").vertx(vertx)));
        IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
                () -> unreachable.getContractRuleset("g", "a"));
        Assertions.assertTrue(e.getMessage().startsWith("Failed to fetch the contract ruleset of g/a"),
                e.getMessage());
    }

    @Test
    public void testGlobalRulesAreEnforcedAndVersionRulesOverrideThem() throws Exception {
        Schema schema = new Schema.Parser().parse(SCHEMA);
        String groupId = TestUtils.generateGroupId();
        String topic = generateArtifactId();
        String artifactId = topic + "-OrderEvent";
        createArtifact(groupId, artifactId, ArtifactType.AVRO, SCHEMA, ContentTypes.APPLICATION_JSON);
        GenericData.Record emptyId = new GenericData.Record(schema);
        emptyId.put("orderId", "");
        emptyId.put("totalAmount", 10.0);

        send("PUT", "/admin/contracts/ruleset", nonEmptyOrderIdRule("size(orderId) > 0"));
        try {
            // The global rule applies, and the message names the rule and why it failed
            Serializer<GenericData.Record> serializer = contractSerializer(groupId);
            RuntimeException ex = Assertions.assertThrows(RuntimeException.class,
                    () -> serializer.serialize(topic, emptyId));
            Assertions.assertTrue(ex.getMessage().contains(
                    "order-id-present (ERROR): Condition failed: order-id-present"), ex.getMessage());
            serializer.close();

            // A version rule with the same name replaces it. A new serializer has an empty cache.
            send("PUT", "/groups/" + groupId + "/artifacts/" + artifactId + "/versions/1/contract/ruleset",
                    nonEmptyOrderIdRule("true"));
            Serializer<GenericData.Record> overridden = contractSerializer(groupId);
            Assertions.assertTrue(overridden.serialize(topic, emptyId).length > 0);
            overridden.close();
        } finally {
            send("DELETE", "/admin/contracts/ruleset", null);
        }
    }

    @Test
    public void testFacadeFindsVersionCoordinatesByGlobalIdAndContentId() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();
        var created = createArtifact(groupId, artifactId, ArtifactType.AVRO, SCHEMA, ContentTypes.APPLICATION_JSON);
        long globalId = created.getVersion().getGlobalId();
        long contentId = created.getVersion().getContentId();
        RegistryClientFacadeImpl facade = new RegistryClientFacadeImpl(isolatedClientV3);

        var byGlobalId = facade.getVersionCoordinatesByGlobalId(globalId);
        Assertions.assertEquals(groupId, byGlobalId.getGroupId());
        Assertions.assertEquals(artifactId, byGlobalId.getArtifactId());
        Assertions.assertEquals("1", byGlobalId.getVersion());
        Assertions.assertEquals(contentId, byGlobalId.getContentId());

        var byContentId = facade.getVersionCoordinatesByContentId(contentId, groupId, artifactId);
        Assertions.assertEquals("1", byContentId.getVersion());
        Assertions.assertEquals(globalId, byContentId.getGlobalId());

        Assertions.assertNull(facade.getVersionCoordinatesByGlobalId(Long.MAX_VALUE));
        Assertions.assertNull(facade.getVersionCoordinatesByContentId(contentId, groupId, "other-artifact"));
    }

    @Test
    public void testContentIdLookupMatchesTheArtifactIdExactly() throws Exception {
        // '*' is a wildcard in search filters but a legal character in artifact IDs
        String groupId = TestUtils.generateGroupId();
        String base = generateArtifactId();
        var wild = createArtifact(groupId, base + "*", ArtifactType.AVRO, SCHEMA, ContentTypes.APPLICATION_JSON);
        var newer = createArtifact(groupId, base + "-prod", ArtifactType.AVRO, SCHEMA, ContentTypes.APPLICATION_JSON);
        long contentId = wild.getVersion().getContentId();
        Assertions.assertEquals(contentId, newer.getVersion().getContentId());

        RegistryClientFacadeImpl facade = new RegistryClientFacadeImpl(isolatedClientV3);
        var found = facade.getVersionCoordinatesByContentId(contentId, groupId, base + "*");
        Assertions.assertEquals(base + "*", found.getArtifactId());
        Assertions.assertEquals(wild.getVersion().getGlobalId(), found.getGlobalId());
    }

    private void assertConsumerRejectsRecordWrittenWith(String idOption) throws Exception {
        Schema schema = new Schema.Parser().parse(SCHEMA);
        String groupId = TestUtils.generateGroupId();
        String topic = generateArtifactId();
        String artifactId = topic + "-OrderEvent";
        createArtifact(groupId, artifactId, ArtifactType.AVRO, SCHEMA, ContentTypes.APPLICATION_JSON);
        setContractRuleset(groupId, artifactId,
                "{\"domainRules\":[{\"name\":\"positive-amount\",\"kind\":\"CONDITION\",\"type\":\"CEL\","
                        + "\"mode\":\"READ\",\"expr\":\"totalAmount > 0\",\"onFailure\":\"ERROR\"}],"
                        + "\"migrationRules\":[]}");

        // The producer does not check contract rules, so it writes the invalid record
        Map<String, Object> serConfig = new HashMap<>();
        serConfig.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV3ApiUrl(testPort));
        serConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        serConfig.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
        serConfig.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
        serConfig.put(SerdeConfig.USE_ID, idOption);
        Serializer<GenericData.Record> serializer = new AvroKafkaSerializer<>();
        serializer.configure(serConfig, false);

        Map<String, Object> deserConfig = new HashMap<>(serConfig);
        deserConfig.put(SerdeConfig.CONTRACT_RULES_ENABLED, "true");
        deserConfig.put(SerdeConfig.CONTRACT_RULES_FAIL_ON_ERROR, "true");
        Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>();
        deserializer.configure(deserConfig, false);

        GenericData.Record invalid = new GenericData.Record(schema);
        invalid.put("orderId", "ORD-READ");
        invalid.put("totalAmount", -5.0);
        GenericData.Record valid = new GenericData.Record(schema);
        valid.put("orderId", "ORD-READ");
        valid.put("totalAmount", 5.0);

        byte[] invalidBytes = serializer.serialize(topic, invalid);
        RuntimeException ex = Assertions.assertThrows(RuntimeException.class,
                () -> deserializer.deserialize(topic, invalidBytes));
        Assertions.assertTrue(ex.getMessage().startsWith("Contract rule validation failed (READ)"),
                ex.getMessage());
        Assertions.assertTrue(ex.getMessage().contains("positive-amount"), ex.getMessage());

        GenericData.Record read = deserializer.deserialize(topic, serializer.serialize(topic, valid));
        Assertions.assertEquals(5.0, (double) read.get("totalAmount"), 0.001);

        serializer.close();
        deserializer.close();
    }

    @Test
    public void testReadRulesApplyToRecordsWrittenWithContentId() throws Exception {
        assertConsumerRejectsRecordWrittenWith("contentId");
    }

    @Test
    public void testReadRulesApplyToRecordsWrittenWithGlobalId() throws Exception {
        assertConsumerRejectsRecordWrittenWith("globalId");
    }

    @Test
    public void testContractRulesPassDuringSerialization() throws Exception {
        Schema schema = new Schema.Parser().parse(SCHEMA);
        String groupId = TestUtils.generateGroupId();
        String topic = generateArtifactId();
        String artifactId = topic + "-OrderEvent";

        createArtifact(groupId, artifactId, ArtifactType.AVRO, SCHEMA,
                ContentTypes.APPLICATION_JSON);

        setContractRuleset(groupId, artifactId,
                "{\"domainRules\":[{\"name\":\"positive-amount\","
                        + "\"kind\":\"CONDITION\",\"type\":\"CEL\",\"mode\":\"WRITE\","
                        + "\"expr\":\"totalAmount > 0\",\"onFailure\":\"ERROR\","
                        + "\"disabled\":false}],\"migrationRules\":[]}");

        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV3ApiUrl(testPort));
        config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
        config.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
        config.put(SerdeConfig.CONTRACT_RULES_ENABLED, "true");
        config.put(SerdeConfig.CONTRACT_RULES_FAIL_ON_ERROR, "true");

        Serializer<GenericData.Record> serializer = new AvroKafkaSerializer<>();
        serializer.configure(config, false);

        TestUtils.retry(() -> {
            GenericData.Record record = new GenericData.Record(schema);
            record.put("orderId", "ORD-001");
            record.put("totalAmount", 99.99);

            byte[] bytes = serializer.serialize(topic, record);
            Assertions.assertNotNull(bytes);
            Assertions.assertTrue(bytes.length > 0);
        });

        serializer.close();
    }

    @Test
    public void testContractRulesBlockInvalidDuringSerialization() throws Exception {
        Schema schema = new Schema.Parser().parse(SCHEMA);
        String groupId = TestUtils.generateGroupId();
        String topic = generateArtifactId();
        String artifactId = topic + "-OrderEvent";

        createArtifact(groupId, artifactId, ArtifactType.AVRO, SCHEMA,
                ContentTypes.APPLICATION_JSON);

        setContractRuleset(groupId, artifactId,
                "{\"domainRules\":[{\"name\":\"positive-amount\","
                        + "\"kind\":\"CONDITION\",\"type\":\"CEL\",\"mode\":\"WRITE\","
                        + "\"expr\":\"totalAmount > 0\",\"onFailure\":\"ERROR\","
                        + "\"disabled\":false}],\"migrationRules\":[]}");

        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV3ApiUrl(testPort));
        config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
        config.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
        config.put(SerdeConfig.CONTRACT_RULES_ENABLED, "true");
        config.put(SerdeConfig.CONTRACT_RULES_FAIL_ON_ERROR, "true");

        Serializer<GenericData.Record> serializer = new AvroKafkaSerializer<>();
        serializer.configure(config, false);

        TestUtils.retry(() -> {
            GenericData.Record record = new GenericData.Record(schema);
            record.put("orderId", "ORD-002");
            record.put("totalAmount", -5.0);

            RuntimeException ex = Assertions.assertThrows(RuntimeException.class,
                    () -> serializer.serialize(topic, record));
            Assertions.assertTrue(ex.getMessage().contains("Contract rule validation failed"),
                    "Expected contract rule failure but got: " + ex.getMessage());
        });

        serializer.close();
    }

    @Test
    public void testContractRulesRoundTrip() throws Exception {
        Schema schema = new Schema.Parser().parse(SCHEMA);
        String groupId = TestUtils.generateGroupId();
        String topic = generateArtifactId();
        String artifactId = topic + "-OrderEvent";

        createArtifact(groupId, artifactId, ArtifactType.AVRO, SCHEMA,
                ContentTypes.APPLICATION_JSON);

        setContractRuleset(groupId, artifactId,
                "{\"domainRules\":[{\"name\":\"positive-amount\","
                        + "\"kind\":\"CONDITION\",\"type\":\"CEL\",\"mode\":\"WRITE\","
                        + "\"expr\":\"totalAmount > 0\",\"onFailure\":\"ERROR\","
                        + "\"disabled\":false}],\"migrationRules\":[]}");

        Map<String, Object> serConfig = new HashMap<>();
        serConfig.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV3ApiUrl(testPort));
        serConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        serConfig.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
        serConfig.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
        serConfig.put(SerdeConfig.CONTRACT_RULES_ENABLED, "true");
        serConfig.put(SerdeConfig.CONTRACT_RULES_FAIL_ON_ERROR, "true");

        Serializer<GenericData.Record> serializer = new AvroKafkaSerializer<>();
        serializer.configure(serConfig, false);

        Map<String, Object> deserConfig = new HashMap<>();
        deserConfig.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV3ApiUrl(testPort));
        deserConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        deserConfig.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
        deserConfig.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
        deserConfig.put(SerdeConfig.CONTRACT_RULES_ENABLED, "true");
        deserConfig.put(SerdeConfig.CONTRACT_RULES_FAIL_ON_ERROR, "true");

        Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>();
        deserializer.configure(deserConfig, false);

        TestUtils.retry(() -> {
            GenericData.Record record = new GenericData.Record(schema);
            record.put("orderId", "ORD-003");
            record.put("totalAmount", 42.0);

            byte[] bytes = serializer.serialize(topic, record);
            GenericData.Record deserialized = deserializer.deserialize(topic, bytes);

            Assertions.assertNotNull(deserialized);
            Assertions.assertEquals("ORD-003", deserialized.get("orderId").toString());
            Assertions.assertEquals(42.0, (double) deserialized.get("totalAmount"), 0.001);
        });

        serializer.close();
        deserializer.close();
    }

    private static final String UPGRADE_RULESET = "{\"domainRules\":[],\"migrationRules\":[{"
            + "\"name\":\"default-currency\",\"kind\":\"TRANSFORM\",\"type\":\"JSONATA\","
            + "\"mode\":\"UPGRADE\",\"expr\":\"$merge([$, {\\\"currency\\\": \\\"USD\\\"}])\","
            + "\"onFailure\":\"ERROR\"}]}";

    private void addVersionWithUpgradeRule(String groupId, String artifactId, String schema) throws Exception {
        String content = new ObjectMapper().writeValueAsString(schema);
        send("POST", "/groups/" + groupId + "/artifacts/" + artifactId + "/versions",
                "{\"version\":\"2\",\"content\":{\"content\":" + content
                        + ",\"contentType\":\"application/json\"}}");
        send("PUT", "/groups/" + groupId + "/artifacts/" + artifactId + "/versions/2/contract/ruleset",
                UPGRADE_RULESET);
    }

    private Map<String, Object> migratingConsumerConfig(String groupId) {
        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV3ApiUrl(testPort));
        config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        config.put(SerdeConfig.CONTRACT_RULES_ENABLED, "true");
        config.put(SerdeConfig.CONTRACT_RULES_FAIL_ON_ERROR, "true");
        config.put(SerdeConfig.CONTRACT_RULES_MIGRATION_ENABLED, "true");
        config.put(SerdeConfig.CONTRACT_RULES_MIGRATION_TARGET_VERSION, "2");
        return config;
    }

    @Test
    public void testConsumerReceivesAvroRecordMigratedToTheTargetVersion() throws Exception {
        Schema v1 = new Schema.Parser().parse(SCHEMA);
        String schemaV2 = SCHEMA.replace("]}", ",{\"name\":\"currency\",\"type\":\"string\","
                + "\"default\":\"EUR\"}]}");
        String groupId = TestUtils.generateGroupId();
        String topic = generateArtifactId();
        String artifactId = topic + "-OrderEvent";
        createArtifact(groupId, artifactId, ArtifactType.AVRO, SCHEMA, ContentTypes.APPLICATION_JSON);

        Map<String, Object> serConfig = new HashMap<>();
        serConfig.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV3ApiUrl(testPort));
        serConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        serConfig.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
        serConfig.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
        Serializer<GenericData.Record> serializer = new AvroKafkaSerializer<>();
        serializer.configure(serConfig, false);
        GenericData.Record record = new GenericData.Record(v1);
        record.put("orderId", "ORD-MIG");
        record.put("totalAmount", 99.99);
        byte[] bytes = serializer.serialize(topic, record);

        addVersionWithUpgradeRule(groupId, artifactId, schemaV2);

        Map<String, Object> deserConfig = migratingConsumerConfig(groupId);
        deserConfig.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
        deserConfig.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
        Deserializer<GenericRecord> deserializer = new AvroKafkaDeserializer<>();
        deserializer.configure(deserConfig, false);

        GenericRecord migrated = deserializer.deserialize(topic, bytes);
        Assertions.assertEquals(new Schema.Parser().parse(schemaV2), migrated.getSchema());
        Assertions.assertEquals("USD", migrated.get("currency").toString());
        Assertions.assertEquals("ORD-MIG", migrated.get("orderId").toString());
        Assertions.assertEquals(99.99, (double) migrated.get("totalAmount"), 0.0);

        // Without a migration target, the record is delivered as written
        Map<String, Object> plainConfig = new HashMap<>(deserConfig);
        plainConfig.remove(SerdeConfig.CONTRACT_RULES_MIGRATION_TARGET_VERSION);
        Deserializer<GenericRecord> plain = new AvroKafkaDeserializer<>();
        plain.configure(plainConfig, false);
        GenericRecord unmigrated = plain.deserialize(topic, bytes);
        Assertions.assertEquals(v1, unmigrated.getSchema());
        Assertions.assertFalse(unmigrated.hasField("currency"));

        serializer.close();
        deserializer.close();
        plain.close();
    }

    @Test
    public void testConsumerReceivesJsonSchemaRecordMigratedToTheTargetVersion() throws Exception {
        String schemaV1 = "{\"$schema\":\"http://json-schema.org/draft-07/schema#\",\"type\":\"object\","
                + "\"required\":[\"orderId\"],\"properties\":{\"orderId\":{\"type\":\"string\"},"
                + "\"totalAmount\":{\"type\":\"number\"}}}";
        String schemaV2 = schemaV1.replace("[\"orderId\"]", "[\"orderId\",\"currency\"]")
                .replace("}}}", "},\"currency\":{\"type\":\"string\"}}}");
        String groupId = TestUtils.generateGroupId();
        String topic = generateArtifactId();
        createArtifact(groupId, topic, ArtifactType.JSON, schemaV1, ContentTypes.APPLICATION_JSON);

        Map<String, Object> serConfig = new HashMap<>();
        serConfig.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV3ApiUrl(testPort));
        serConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        serConfig.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
        Serializer<JsonNode> serializer = new JsonSchemaKafkaSerializer<>();
        serializer.configure(serConfig, false);
        ObjectMapper mapper = new ObjectMapper();
        byte[] bytes = serializer.serialize(topic,
                mapper.readTree("{\"orderId\":\"ORD-MIG\",\"totalAmount\":99.99}"));

        addVersionWithUpgradeRule(groupId, topic, schemaV2);

        Map<String, Object> deserConfig = migratingConsumerConfig(groupId);
        deserConfig.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
        Deserializer<JsonNode> deserializer = new JsonSchemaKafkaDeserializer<>();
        deserializer.configure(deserConfig, false);

        // Validated against version 2, which requires currency
        JsonNode migrated = deserializer.deserialize(topic, bytes);
        Assertions.assertEquals(mapper.readTree(
                "{\"orderId\":\"ORD-MIG\",\"totalAmount\":99.99,\"currency\":\"USD\"}"), migrated);

        serializer.close();
        deserializer.close();
    }

    @Test
    public void testContractRulesFailOnErrorFalse_SerializesAnyway() throws Exception {
        Schema schema = new Schema.Parser().parse(SCHEMA);
        String groupId = TestUtils.generateGroupId();
        String topic = generateArtifactId();
        String artifactId = topic + "-OrderEvent";

        createArtifact(groupId, artifactId, ArtifactType.AVRO, SCHEMA,
                ContentTypes.APPLICATION_JSON);

        setContractRuleset(groupId, artifactId,
                "{\"domainRules\":[{\"name\":\"always-fail\","
                        + "\"kind\":\"CONDITION\",\"type\":\"CEL\",\"mode\":\"WRITE\","
                        + "\"expr\":\"false\",\"onFailure\":\"DLQ\","
                        + "\"disabled\":false}],\"migrationRules\":[]}");

        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV3ApiUrl(testPort));
        config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
        config.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
        config.put(SerdeConfig.CONTRACT_RULES_ENABLED, "true");
        config.put(SerdeConfig.CONTRACT_RULES_FAIL_ON_ERROR, "false");

        Serializer<GenericData.Record> serializer = new AvroKafkaSerializer<>();
        serializer.configure(config, false);

        TestUtils.retry(() -> {
            GenericData.Record record = new GenericData.Record(schema);
            record.put("orderId", "ORD-DLQ");
            record.put("totalAmount", -1.0);

            byte[] bytes = serializer.serialize(topic, record);
            Assertions.assertNotNull(bytes, "Should serialize even when rule fails with DLQ action and fail-on-error=false");
            Assertions.assertTrue(bytes.length > 0);
        });

        serializer.close();
    }

    @Test
    public void testContractRulesDisabledByDefault() throws Exception {
        Schema schema = new Schema.Parser().parse(SCHEMA);
        String groupId = TestUtils.generateGroupId();
        String topic = generateArtifactId();
        String artifactId = topic + "-OrderEvent";

        createArtifact(groupId, artifactId, ArtifactType.AVRO, SCHEMA,
                ContentTypes.APPLICATION_JSON);

        setContractRuleset(groupId, artifactId,
                "{\"domainRules\":[{\"name\":\"always-fail\","
                        + "\"kind\":\"CONDITION\",\"type\":\"CEL\",\"mode\":\"WRITE\","
                        + "\"expr\":\"false\",\"onFailure\":\"ERROR\","
                        + "\"disabled\":false}],\"migrationRules\":[]}");

        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV3ApiUrl(testPort));
        config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
        config.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
        // CONTRACT_RULES_ENABLED is NOT set — defaults to false

        Serializer<GenericData.Record> serializer = new AvroKafkaSerializer<>();
        serializer.configure(config, false);

        TestUtils.retry(() -> {
            GenericData.Record record = new GenericData.Record(schema);
            record.put("orderId", "ORD-004");
            record.put("totalAmount", -999.0);

            byte[] bytes = serializer.serialize(topic, record);
            Assertions.assertNotNull(bytes, "Should serialize even with failing rule when disabled");
        });

        serializer.close();
    }
}
