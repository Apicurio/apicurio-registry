package io.apicurio.registry.noprofile.serde;

import io.apicurio.registry.AbstractClientFacadeTestBase;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.avro.DefaultAvroDatumProvider;
import io.apicurio.registry.serde.avro.strategy.TopicRecordIdStrategy;
import io.apicurio.registry.serde.avro.AvroSerdeConfig;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
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
