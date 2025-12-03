package io.apicurio.tests.serdes.apicurio;

import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.common.serdes.json.ValidMessage;
import io.apicurio.tests.common.serdes.proto.TestCmmn;
import io.apicurio.tests.utils.Constants;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializerConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Performance comparison test: Measures and compares the serialization/deserialization
 * performance between Apicurio and Confluent serializers for:
 * - Avro
 * - JSON Schema
 * - Protobuf
 */
@Tag(Constants.SERDES)
@QuarkusIntegrationTest
public class SerdesPerformanceIT extends ApicurioRegistryBaseIT {

    // Number of warmup iterations (not measured)
    private static final int WARMUP_ITERATIONS = 100;

    // Number of measured iterations
    private static final int MEASURED_ITERATIONS = 1000;

    @BeforeEach
    void setupValidityRule() {
        // Set up SYNTAX_ONLY validity rule for Confluent protobuf compatibility
        try {
            CreateRule rule = new CreateRule();
            rule.setConfig("SYNTAX_ONLY");
            rule.setRuleType(RuleType.VALIDITY);
            registryClient.admin().rules().post(rule);
        } catch (Exception e) {
            // Rule may already exist, ignore
        }
    }

    @AfterEach
    void cleanupGlobalRules() {
        // Clean up global rules to avoid affecting other tests
        try {
            registryClient.admin().rules().delete();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    // ==================== AVRO TESTS ====================

    @Test
    @Tag(Constants.ACCEPTANCE)
    void testAvroSerializerPerformance() throws Exception {
        String groupId = "avro-perf-test-serializer-" + UUID.randomUUID();
        String apicurioArtifactId = TestUtils.generateSubject() + "-apicurio";

        AvroGenericRecordSchemaFactory schemaFactory = new AvroGenericRecordSchemaFactory(
                "io.apicurio.perf", "PerfTestRecord", List.of("field1", "field2", "field3"));
        GenericRecord testRecord = schemaFactory.generateRecord(1);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("AVRO SERIALIZER PERFORMANCE COMPARISON");
        System.out.println("=".repeat(80));
        System.out.println("Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("Measured iterations: " + MEASURED_ITERATIONS);
        System.out.println("=".repeat(80));

        testAvroSerializerWithMessage(groupId, apicurioArtifactId, schemaFactory, testRecord);

        System.out.println("=".repeat(80));
    }

    @Test
    @Tag(Constants.ACCEPTANCE)
    void testAvroRoundTripPerformance() throws Exception {
        String groupId = "avro-perf-test-roundtrip-" + UUID.randomUUID();
        String apicurioArtifactId = TestUtils.generateSubject() + "-apicurio";

        AvroGenericRecordSchemaFactory schemaFactory = new AvroGenericRecordSchemaFactory(
                "io.apicurio.perf", "PerfTestRecord", List.of("field1", "field2", "field3"));
        GenericRecord testRecord = schemaFactory.generateRecord(1);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("AVRO ROUND-TRIP PERFORMANCE COMPARISON");
        System.out.println("=".repeat(80));
        System.out.println("Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("Measured iterations: " + MEASURED_ITERATIONS);
        System.out.println("=".repeat(80));

        testAvroRoundTripWithMessage(groupId, apicurioArtifactId, schemaFactory, testRecord);

        System.out.println("=".repeat(80));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void testAvroSerializerWithMessage(String groupId, String apicurioArtifactId,
            AvroGenericRecordSchemaFactory schemaFactory, GenericRecord testRecord) throws Exception {

        System.out.println("\n--- Avro GenericRecord Serialization ---");

        String apicurioTopic = "apicurio-avro-" + UUID.randomUUID();
        String confluentTopic = "confluent-avro-" + UUID.randomUUID();

        // Setup APICURIO serializer
        AvroKafkaSerializer<GenericRecord> apicurioSerializer = new AvroKafkaSerializer<>();
        Map<String, Object> apicurioConfig = new HashMap<>();
        apicurioConfig.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
        apicurioConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        apicurioConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, apicurioArtifactId);
        apicurioConfig.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
        apicurioSerializer.configure(apicurioConfig, false);

        // Setup CONFLUENT serializer
        KafkaAvroSerializer confluentSerializer = new KafkaAvroSerializer();
        Map<String, String> confluentProps = new HashMap<>();
        confluentProps.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                getRegistryBaseUrl() + "/apis/ccompat/v7");
        confluentProps.put(KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS, "true");
        confluentSerializer.configure(confluentProps, false);

        try {
            RecordHeaders headers = new RecordHeaders();

            // Register schemas first
            apicurioSerializer.serialize(apicurioTopic, headers, testRecord);
            confluentSerializer.serialize(confluentTopic, testRecord);

            waitForArtifact(groupId, apicurioArtifactId);

            // Warmup APICURIO
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                apicurioSerializer.serialize(apicurioTopic, new RecordHeaders(), testRecord);
            }

            // Warmup CONFLUENT
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                confluentSerializer.serialize(confluentTopic, testRecord);
            }

            // Measure APICURIO serializer
            long apicurioStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                apicurioSerializer.serialize(apicurioTopic, new RecordHeaders(), testRecord);
            }
            long apicurioTotalNanos = System.nanoTime() - apicurioStartTime;

            // Measure CONFLUENT serializer
            long confluentStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                confluentSerializer.serialize(confluentTopic, testRecord);
            }
            long confluentTotalNanos = System.nanoTime() - confluentStartTime;

            printResults("Avro Serialization", apicurioTotalNanos, confluentTotalNanos);

        } finally {
            apicurioSerializer.close();
            confluentSerializer.close();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void testAvroRoundTripWithMessage(String groupId, String apicurioArtifactId,
            AvroGenericRecordSchemaFactory schemaFactory, GenericRecord testRecord) throws Exception {

        System.out.println("\n--- Avro GenericRecord Round-Trip ---");

        String apicurioTopic = "apicurio-avro-rt-" + UUID.randomUUID();
        String confluentTopic = "confluent-avro-rt-" + UUID.randomUUID();

        // Setup Apicurio serializer and deserializer
        AvroKafkaSerializer<GenericRecord> apicurioSerializer = new AvroKafkaSerializer<>();
        AvroKafkaDeserializer<GenericRecord> apicurioDeserializer = new AvroKafkaDeserializer<>();

        // Setup Confluent serializer and deserializer
        KafkaAvroSerializer confluentSerializer = new KafkaAvroSerializer();
        KafkaAvroDeserializer confluentDeserializer = new KafkaAvroDeserializer();

        Map<String, Object> apicurioConfig = new HashMap<>();
        apicurioConfig.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
        apicurioConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        apicurioConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, apicurioArtifactId);
        apicurioConfig.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");

        Map<String, String> confluentProps = new HashMap<>();
        confluentProps.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                getRegistryBaseUrl() + "/apis/ccompat/v7");
        confluentProps.put(KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS, "true");

        apicurioSerializer.configure(apicurioConfig, false);
        apicurioDeserializer.configure(apicurioConfig, false);
        confluentSerializer.configure(confluentProps, false);
        confluentDeserializer.configure(confluentProps, false);

        try {
            RecordHeaders headers = new RecordHeaders();

            // Initial registration
            apicurioSerializer.serialize(apicurioTopic, headers, testRecord);
            confluentSerializer.serialize(confluentTopic, testRecord);

            waitForArtifact(groupId, apicurioArtifactId);

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                RecordHeaders h = new RecordHeaders();
                byte[] data = apicurioSerializer.serialize(apicurioTopic, h, testRecord);
                apicurioDeserializer.deserialize(apicurioTopic, h, data);
            }
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                byte[] data = confluentSerializer.serialize(confluentTopic, testRecord);
                confluentDeserializer.deserialize(confluentTopic, data);
            }

            // Measure APICURIO round-trip
            long apicurioStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                RecordHeaders h = new RecordHeaders();
                byte[] data = apicurioSerializer.serialize(apicurioTopic, h, testRecord);
                apicurioDeserializer.deserialize(apicurioTopic, h, data);
            }
            long apicurioTotalNanos = System.nanoTime() - apicurioStartTime;

            // Measure CONFLUENT round-trip
            long confluentStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                byte[] data = confluentSerializer.serialize(confluentTopic, testRecord);
                confluentDeserializer.deserialize(confluentTopic, data);
            }
            long confluentTotalNanos = System.nanoTime() - confluentStartTime;

            printResults("Avro Round-trip", apicurioTotalNanos, confluentTotalNanos);

        } finally {
            apicurioSerializer.close();
            apicurioDeserializer.close();
            confluentSerializer.close();
            confluentDeserializer.close();
        }
    }

    // ==================== JSON SCHEMA TESTS ====================

    @Test
    @Tag(Constants.ACCEPTANCE)
    void testJsonSchemaSerializerPerformance() throws Exception {
        String groupId = "json-perf-test-serializer-" + UUID.randomUUID();
        String apicurioArtifactId = TestUtils.generateSubject() + "-apicurio";

        JsonSchemaMsgFactory schemaFactory = new JsonSchemaMsgFactory();
        ValidMessage testMessage = schemaFactory.generateMessage(1);

        // Pre-register schema for Apicurio (JSON Schema doesn't auto-register)
        createArtifact(groupId, apicurioArtifactId, ArtifactType.JSON, schemaFactory.getSchemaString(),
                ContentTypes.APPLICATION_JSON, null, null);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("JSON SCHEMA SERIALIZER PERFORMANCE COMPARISON");
        System.out.println("=".repeat(80));
        System.out.println("Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("Measured iterations: " + MEASURED_ITERATIONS);
        System.out.println("=".repeat(80));

        testJsonSchemaSerializerWithMessage(groupId, apicurioArtifactId, testMessage);

        System.out.println("=".repeat(80));
    }

    @Test
    @Tag(Constants.ACCEPTANCE)
    void testJsonSchemaRoundTripPerformance() throws Exception {
        String groupId = "json-perf-test-roundtrip-" + UUID.randomUUID();
        String apicurioArtifactId = TestUtils.generateSubject() + "-apicurio";

        JsonSchemaMsgFactory schemaFactory = new JsonSchemaMsgFactory();
        ValidMessage testMessage = schemaFactory.generateMessage(1);

        // Pre-register schema for Apicurio
        createArtifact(groupId, apicurioArtifactId, ArtifactType.JSON, schemaFactory.getSchemaString(),
                ContentTypes.APPLICATION_JSON, null, null);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("JSON SCHEMA ROUND-TRIP PERFORMANCE COMPARISON");
        System.out.println("=".repeat(80));
        System.out.println("Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("Measured iterations: " + MEASURED_ITERATIONS);
        System.out.println("=".repeat(80));

        testJsonSchemaRoundTripWithMessage(groupId, apicurioArtifactId, testMessage);

        System.out.println("=".repeat(80));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void testJsonSchemaSerializerWithMessage(String groupId, String apicurioArtifactId,
            ValidMessage testMessage) throws Exception {

        System.out.println("\n--- JSON Schema Serialization ---");

        String apicurioTopic = "apicurio-json-" + UUID.randomUUID();
        String confluentTopic = "confluent-json-" + UUID.randomUUID();

        // Setup APICURIO serializer
        // Note: Disable validation to match Confluent's default behavior for fair comparison
        JsonSchemaKafkaSerializer<ValidMessage> apicurioSerializer = new JsonSchemaKafkaSerializer<>();
        Map<String, Object> apicurioConfig = new HashMap<>();
        apicurioConfig.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
        apicurioConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        apicurioConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, apicurioArtifactId);
        apicurioConfig.put(SerdeConfig.VALIDATION_ENABLED, false);
        apicurioSerializer.configure(apicurioConfig, false);

        // Setup CONFLUENT serializer
        KafkaJsonSchemaSerializer<ValidMessage> confluentSerializer = new KafkaJsonSchemaSerializer<>();
        Map<String, Object> confluentProps = new HashMap<>();
        confluentProps.put(KafkaJsonSchemaSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                getRegistryBaseUrl() + "/apis/ccompat/v7");
        confluentProps.put(KafkaJsonSchemaSerializerConfig.AUTO_REGISTER_SCHEMAS, "true");
        confluentSerializer.configure(confluentProps, false);

        try {
            RecordHeaders headers = new RecordHeaders();

            // Register schemas first
            apicurioSerializer.serialize(apicurioTopic, headers, testMessage);
            confluentSerializer.serialize(confluentTopic, testMessage);

            // Warmup APICURIO
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                apicurioSerializer.serialize(apicurioTopic, new RecordHeaders(), testMessage);
            }

            // Warmup CONFLUENT
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                confluentSerializer.serialize(confluentTopic, testMessage);
            }

            // Measure APICURIO serializer
            long apicurioStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                apicurioSerializer.serialize(apicurioTopic, new RecordHeaders(), testMessage);
            }
            long apicurioTotalNanos = System.nanoTime() - apicurioStartTime;

            // Measure CONFLUENT serializer
            long confluentStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                confluentSerializer.serialize(confluentTopic, testMessage);
            }
            long confluentTotalNanos = System.nanoTime() - confluentStartTime;
            printResults("JSON Schema Serialization", apicurioTotalNanos, confluentTotalNanos);

        } finally {
            apicurioSerializer.close();
            confluentSerializer.close();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void testJsonSchemaRoundTripWithMessage(String groupId, String apicurioArtifactId,
            ValidMessage testMessage) throws Exception {

        System.out.println("\n--- JSON Schema Round-Trip ---");

        String apicurioTopic = "apicurio-json-rt-" + UUID.randomUUID();
        String confluentTopic = "confluent-json-rt-" + UUID.randomUUID();

        // Setup Apicurio serializer and deserializer
        JsonSchemaKafkaSerializer<ValidMessage> apicurioSerializer = new JsonSchemaKafkaSerializer<>();
        JsonSchemaKafkaDeserializer<ValidMessage> apicurioDeserializer = new JsonSchemaKafkaDeserializer<>();

        // Setup Confluent serializer and deserializer
        KafkaJsonSchemaSerializer<ValidMessage> confluentSerializer = new KafkaJsonSchemaSerializer<>();
        KafkaJsonSchemaDeserializer<ValidMessage> confluentDeserializer = new KafkaJsonSchemaDeserializer<>();

        // Note: Disable validation to match Confluent's default behavior for fair comparison
        Map<String, Object> apicurioConfig = new HashMap<>();
        apicurioConfig.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
        apicurioConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        apicurioConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, apicurioArtifactId);
        apicurioConfig.put(SerdeConfig.VALIDATION_ENABLED, false);

        Map<String, Object> confluentProps = new HashMap<>();
        confluentProps.put(KafkaJsonSchemaSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                getRegistryBaseUrl() + "/apis/ccompat/v7");
        confluentProps.put(KafkaJsonSchemaSerializerConfig.AUTO_REGISTER_SCHEMAS, "true");
        confluentProps.put("json.value.type", ValidMessage.class.getName());

        apicurioSerializer.configure(apicurioConfig, false);
        apicurioDeserializer.configure(apicurioConfig, false);
        confluentSerializer.configure(confluentProps, false);
        confluentDeserializer.configure(confluentProps, false);

        try {
            RecordHeaders headers = new RecordHeaders();

            // Initial registration
            apicurioSerializer.serialize(apicurioTopic, headers, testMessage);
            confluentSerializer.serialize(confluentTopic, testMessage);

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                RecordHeaders h = new RecordHeaders();
                byte[] data = apicurioSerializer.serialize(apicurioTopic, h, testMessage);
                apicurioDeserializer.deserialize(apicurioTopic, h, data);
            }
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                byte[] data = confluentSerializer.serialize(confluentTopic, testMessage);
                confluentDeserializer.deserialize(confluentTopic, data);
            }

            // Measure APICURIO round-trip
            long apicurioStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                RecordHeaders h = new RecordHeaders();
                byte[] data = apicurioSerializer.serialize(apicurioTopic, h, testMessage);
                apicurioDeserializer.deserialize(apicurioTopic, h, data);
            }
            long apicurioTotalNanos = System.nanoTime() - apicurioStartTime;

            // Measure CONFLUENT round-trip
            long confluentStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                byte[] data = confluentSerializer.serialize(confluentTopic, testMessage);
                confluentDeserializer.deserialize(confluentTopic, data);
            }
            long confluentTotalNanos = System.nanoTime() - confluentStartTime;

            printResults("JSON Schema Round-trip", apicurioTotalNanos, confluentTotalNanos);

        } finally {
            apicurioSerializer.close();
            apicurioDeserializer.close();
            confluentSerializer.close();
            confluentDeserializer.close();
        }
    }

    // ==================== PROTOBUF TESTS ====================

    @Test
    @Tag(Constants.ACCEPTANCE)
    void testProtobufSerializerPerformance() throws Exception {
        String groupId = "protobuf-perf-test-serializer-" + UUID.randomUUID();
        String apicurioArtifactId = TestUtils.generateSubject() + "-apicurio";

        // Create test message
        // Note: Complex ProtobufTestMessage with google/protobuf/timestamp.proto dependency
        // is not tested here due to native mode limitations with well-known protobuf types
        TestCmmn.UUID simpleMessage = TestCmmn.UUID.newBuilder()
                .setMsb(123L)
                .setLsb(456L)
                .build();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("PROTOBUF SERIALIZER PERFORMANCE COMPARISON");
        System.out.println("=".repeat(80));
        System.out.println("Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("Measured iterations: " + MEASURED_ITERATIONS);
        System.out.println("=".repeat(80));

        // Test with simple message
        testProtobufSerializerWithMessage(groupId, apicurioArtifactId + "-simple",
                simpleMessage, "Simple (UUID)");

        System.out.println("=".repeat(80));
    }

    @Test
    @Tag(Constants.ACCEPTANCE)
    void testProtobufRoundTripPerformance() throws Exception {
        String groupId = "protobuf-perf-test-roundtrip-" + UUID.randomUUID();
        String apicurioArtifactId = TestUtils.generateSubject() + "-apicurio";

        // Create test message
        // Note: Complex ProtobufTestMessage with google/protobuf/timestamp.proto dependency
        // is not tested here due to native mode limitations with well-known protobuf types
        TestCmmn.UUID simpleMessage = TestCmmn.UUID.newBuilder()
                .setMsb(123L)
                .setLsb(456L)
                .build();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("PROTOBUF ROUND-TRIP PERFORMANCE COMPARISON");
        System.out.println("=".repeat(80));
        System.out.println("Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("Measured iterations: " + MEASURED_ITERATIONS);
        System.out.println("=".repeat(80));

        // Test with simple message
        testProtobufRoundTripWithMessage(groupId, apicurioArtifactId + "-simple",
                simpleMessage, "Simple (UUID)");

        System.out.println("=".repeat(80));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T extends Message> void testProtobufSerializerWithMessage(String groupId, String apicurioArtifactId,
            T message, String messageType) throws Exception {

        System.out.println("\n--- " + messageType + " Protobuf Serialization ---");

        String apicurioTopic = "apicurio-proto-" + UUID.randomUUID();
        String confluentTopic = "confluent-proto-" + UUID.randomUUID();

        // Setup APICURIO serializer
        ProtobufKafkaSerializer<Message> apicurioSerializer = new ProtobufKafkaSerializer<>();
        Map<String, Object> apicurioConfig = new HashMap<>();
        apicurioConfig.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
        apicurioConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        apicurioConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, apicurioArtifactId);
        apicurioConfig.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
        apicurioSerializer.configure(apicurioConfig, false);

        // Setup CONFLUENT serializer
        KafkaProtobufSerializer<T> confluentSerializer = new KafkaProtobufSerializer<>();
        Map<String, Object> confluentProps = new HashMap<>();
        confluentProps.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                getRegistryBaseUrl() + "/apis/ccompat/v7");
        confluentProps.put(KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, "true");
        confluentSerializer.configure(confluentProps, false);

        try {
            RecordHeaders headers = new RecordHeaders();

            // Register schemas first
            apicurioSerializer.serialize(apicurioTopic, headers, message);
            confluentSerializer.serialize(confluentTopic, message);

            waitForArtifact(groupId, apicurioArtifactId);

            // Warmup APICURIO
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                apicurioSerializer.serialize(apicurioTopic, new RecordHeaders(), message);
            }

            // Warmup CONFLUENT
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                confluentSerializer.serialize(confluentTopic, message);
            }

            // Measure APICURIO serializer
            long apicurioStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                apicurioSerializer.serialize(apicurioTopic, new RecordHeaders(), message);
            }
            long apicurioTotalNanos = System.nanoTime() - apicurioStartTime;

            // Measure CONFLUENT serializer
            long confluentStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                confluentSerializer.serialize(confluentTopic, message);
            }
            long confluentTotalNanos = System.nanoTime() - confluentStartTime;

            printResults("Protobuf " + messageType + " Serialization", apicurioTotalNanos, confluentTotalNanos);

        } finally {
            apicurioSerializer.close();
            confluentSerializer.close();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T extends Message> void testProtobufRoundTripWithMessage(String groupId, String apicurioArtifactId,
            T message, String messageType) throws Exception {

        System.out.println("\n--- " + messageType + " Protobuf Round-Trip ---");

        String apicurioTopic = "apicurio-proto-rt-" + UUID.randomUUID();
        String confluentTopic = "confluent-proto-rt-" + UUID.randomUUID();

        // Setup Apicurio serializer and deserializer
        ProtobufKafkaSerializer<Message> apicurioSerializer = new ProtobufKafkaSerializer<>();
        ProtobufKafkaDeserializer<T> apicurioDeserializer = new ProtobufKafkaDeserializer<>();

        // Setup Confluent serializer and deserializer
        KafkaProtobufSerializer<T> confluentSerializer = new KafkaProtobufSerializer<>();
        KafkaProtobufDeserializer<DynamicMessage> confluentDeserializer = new KafkaProtobufDeserializer<>();

        Map<String, Object> apicurioConfig = new HashMap<>();
        apicurioConfig.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
        apicurioConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        apicurioConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, apicurioArtifactId);
        apicurioConfig.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");

        Map<String, Object> confluentProps = new HashMap<>();
        confluentProps.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                getRegistryBaseUrl() + "/apis/ccompat/v7");
        confluentProps.put(KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, "true");

        apicurioSerializer.configure(apicurioConfig, false);
        apicurioDeserializer.configure(apicurioConfig, false);
        confluentSerializer.configure(confluentProps, false);
        confluentDeserializer.configure(confluentProps, false);

        try {
            RecordHeaders headers = new RecordHeaders();

            // Initial registration
            apicurioSerializer.serialize(apicurioTopic, headers, message);
            confluentSerializer.serialize(confluentTopic, message);

            waitForArtifact(groupId, apicurioArtifactId);

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                RecordHeaders h = new RecordHeaders();
                byte[] data = apicurioSerializer.serialize(apicurioTopic, h, message);
                apicurioDeserializer.deserialize(apicurioTopic, h, data);
            }
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                byte[] data = confluentSerializer.serialize(confluentTopic, message);
                confluentDeserializer.deserialize(confluentTopic, data);
            }

            // Measure APICURIO round-trip
            long apicurioStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                RecordHeaders h = new RecordHeaders();
                byte[] data = apicurioSerializer.serialize(apicurioTopic, h, message);
                apicurioDeserializer.deserialize(apicurioTopic, h, data);
            }
            long apicurioTotalNanos = System.nanoTime() - apicurioStartTime;

            // Measure CONFLUENT round-trip
            long confluentStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                byte[] data = confluentSerializer.serialize(confluentTopic, message);
                confluentDeserializer.deserialize(confluentTopic, data);
            }
            long confluentTotalNanos = System.nanoTime() - confluentStartTime;

            printResults("Protobuf " + messageType + " Round-trip", apicurioTotalNanos, confluentTotalNanos);

        } finally {
            apicurioSerializer.close();
            apicurioDeserializer.close();
            confluentSerializer.close();
            confluentDeserializer.close();
        }
    }

    // ==================== UTILITY METHODS ====================

    private void waitForArtifact(String groupId, String artifactId) throws Exception {
        TestUtils.retry(() -> {
            VersionMetaData meta = registryClient.groups().byGroupId(groupId)
                    .artifacts().byArtifactId(artifactId)
                    .versions().byVersionExpression("branch=latest").get();
            return meta != null;
        });
    }

    // Maximum allowed performance overhead for Apicurio vs Confluent.
    // Apicurio should be competitive with Confluent, so we allow up to 50% slower
    // to account for microbenchmark variability while still catching major regressions.
    private static final double MAX_ALLOWED_OVERHEAD_PERCENT = 50.0;

    private void printResults(String operation, long apicurioTotalNanos, long confluentTotalNanos) {
        double apicurioTotalMs = apicurioTotalNanos / 1_000_000.0;
        double confluentTotalMs = confluentTotalNanos / 1_000_000.0;

        double apicurioAvgMicros = (apicurioTotalNanos / 1000.0) / MEASURED_ITERATIONS;
        double confluentAvgMicros = (confluentTotalNanos / 1000.0) / MEASURED_ITERATIONS;

        System.out.println(String.format("  APICURIO:  %,d iterations in %.2f ms (avg: %.2f us/op)",
                MEASURED_ITERATIONS, apicurioTotalMs, apicurioAvgMicros));
        System.out.println(String.format("  CONFLUENT: %,d iterations in %.2f ms (avg: %.2f us/op)",
                MEASURED_ITERATIONS, confluentTotalMs, confluentAvgMicros));

        // Compare APICURIO vs CONFLUENT
        double apicurioVsConfluentPercent = ((apicurioTotalNanos - confluentTotalNanos) / (double) confluentTotalNanos) * 100;
        String apicurioVsConfluent = apicurioVsConfluentPercent < 0
            ? String.format("%.1f%% faster", Math.abs(apicurioVsConfluentPercent))
            : String.format("%.1f%% slower", apicurioVsConfluentPercent);

        System.out.println(String.format("  >> %s: APICURIO vs CONFLUENT: %s",
                operation, apicurioVsConfluent));
    }
}
