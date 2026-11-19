package io.apicurio.registry.noprofile.serde;

import io.apicurio.registry.AbstractClientFacadeTestBase;
import io.apicurio.registry.contracts.rules.RuleViolation;
import io.apicurio.registry.serde.ContractRuleViolationException;
import io.apicurio.registry.serde.DeadLetterQueue;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.avro.AvroSerdeConfig;
import io.apicurio.registry.serde.avro.AvroSerializer;
import io.apicurio.registry.serde.avro.DefaultAvroDatumProvider;
import io.apicurio.registry.serde.avro.strategy.TopicRecordIdStrategy;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The serializers and deserializers use the output of contract TRANSFORM rules, and send the records
 * failing a rule with {@code onFailure: DLQ} to the dead letter queue.
 */
@QuarkusTest
public class ContractRuleTransformAndDlqSerdeTest extends AbstractClientFacadeTestBase {

    private static final String SCHEMA = "{\"type\":\"record\",\"name\":\"OrderEvent\",\"namespace\":\"com.example\","
            + "\"fields\":[{\"name\":\"orderId\",\"type\":\"string\"},{\"name\":\"totalAmount\",\"type\":\"double\"}]}";
    private static final Schema AVRO_SCHEMA = new Schema.Parser().parse(SCHEMA);
    private static final String UPPERCASE_ORDER_ID = "$merge([$, {\\\"orderId\\\": $uppercase(orderId)}])";

    private static String rule(String name, String kind, String type, String mode, String expr, String onFailure) {
        return "{\"name\":\"" + name + "\",\"kind\":\"" + kind + "\",\"type\":\"" + type + "\",\"mode\":\"" + mode
                + "\",\"expr\":\"" + expr + "\",\"onFailure\":\"" + onFailure + "\"}";
    }

    private String createOrderArtifact(String groupId, String topic, String rule) throws Exception {
        String artifactId = topic + "-OrderEvent";
        createArtifact(groupId, artifactId, ArtifactType.AVRO, SCHEMA, ContentTypes.APPLICATION_JSON);
        given().when().contentType(CT_JSON).body("{\"domainRules\":[" + rule + "],\"migrationRules\":[]}")
                .pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset").then().statusCode(200);
        return artifactId;
    }

    private Map<String, Object> config(String groupId, boolean contractRules) {
        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV3ApiUrl(testPort));
        config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
        config.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
        config.put(SerdeConfig.CONTRACT_RULES_ENABLED, String.valueOf(contractRules));
        return config;
    }

    private static GenericData.Record order(String orderId) {
        GenericData.Record record = new GenericData.Record(AVRO_SCHEMA);
        record.put("orderId", orderId);
        record.put("totalAmount", 10.0);
        return record;
    }

    @Test
    public void testWriteTransformOutputIsSerialized() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String topic = generateArtifactId();
        createOrderArtifact(groupId, topic, rule("uppercase-id", "TRANSFORM", "JSONATA", "WRITE", UPPERCASE_ORDER_ID, "ERROR"));

        try (Serializer<GenericData.Record> serializer = new AvroKafkaSerializer<>();
                Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>()) {
            serializer.configure(config(groupId, true), false);
            deserializer.configure(config(groupId, false), false);

            GenericData.Record read = deserializer.deserialize(topic, serializer.serialize(topic, order("ord-1")));

            assertEquals("ORD-1", read.get("orderId").toString());
            assertEquals(10.0, (double) read.get("totalAmount"));
        }
    }

    @Test
    public void testReadTransformOutputIsReturned() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String topic = generateArtifactId();
        createOrderArtifact(groupId, topic, rule("uppercase-id", "TRANSFORM", "JSONATA", "READ", UPPERCASE_ORDER_ID, "ERROR"));

        try (Serializer<GenericData.Record> serializer = new AvroKafkaSerializer<>();
                Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>()) {
            serializer.configure(config(groupId, false), false);
            deserializer.configure(config(groupId, true), false);

            GenericData.Record read = deserializer.deserialize(topic, serializer.serialize(topic, order("ord-2")));

            assertEquals("ORD-2", read.get("orderId").toString());
        }
    }

    @Test
    public void testDeadLetterQueueReceivesTheFailingRecord() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String topic = generateArtifactId();
        createOrderArtifact(groupId, topic, rule("order-id-present", "CONDITION", "CEL", "WRITE", "size(orderId) > 0", "DLQ"));

        List<byte[]> sent = new ArrayList<>();
        List<String> sentFrom = new ArrayList<>();
        AvroSerializer<GenericData.Record> serializer = new AvroSerializer<>();
        serializer.configure(new SerdeConfig(config(groupId, true)), false);
        serializer.setDeadLetterQueue(new DeadLetterQueue() {
            @Override
            public String topic() {
                return "orders-dlq";
            }

            @Override
            public void send(String sourceTopic, byte[] payload, List<RuleViolation> violations) {
                sentFrom.add(sourceTopic + " " + violations.get(0).getRuleName());
                sent.add(payload);
            }
        });

        ContractRuleViolationException e = assertThrows(ContractRuleViolationException.class,
                () -> serializer.serializeData(topic, order("")));

        assertEquals("orders-dlq", e.getDeadLetterTopic());
        assertEquals(List.of(topic + " order-id-present"), sentFrom);
        try (Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>()) {
            deserializer.configure(config(groupId, false), false);
            assertEquals("", deserializer.deserialize(topic, sent.get(0)).get("orderId").toString());
        }
    }

    @Test
    public void testDeadLetterQueueRuleWithoutTopicFails() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String topic = generateArtifactId();
        createOrderArtifact(groupId, topic, rule("order-id-present", "CONDITION", "CEL", "WRITE", "size(orderId) > 0", "DLQ"));

        try (Serializer<GenericData.Record> serializer = new AvroKafkaSerializer<>()) {
            serializer.configure(config(groupId, true), false);

            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> serializer.serialize(topic, order("")));
            assertEquals("Contract rule(s) order-id-present failed with onFailure DLQ, but no dead letter queue "
                    + "topic is configured. Set apicurio.registry.serde.contract-rules.dlq.topic.", e.getMessage());
        }
    }
}
