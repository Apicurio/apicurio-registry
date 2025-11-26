package io.apicurio.tests.serdes.apicurio;

import com.google.protobuf.Message;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.common.serdes.proto.TestCmmn;
import io.apicurio.tests.protobuf.ProtobufTestMessage;
import io.apicurio.tests.utils.Constants;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Performance comparison test: Measures and compares the serialization/deserialization
 * performance between:
 * - OLD: Apicurio wire-schema based serializer (v3.1.2)
 * - NEW: Apicurio protobuf4j based serializer (current version)
 * - CONFLUENT: Confluent's KafkaProtobufSerializer
 */
@Tag(Constants.SERDES)
@QuarkusIntegrationTest
public class ProtobufSerdesPerformanceIT extends ApicurioRegistryBaseIT {

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

    /**
     * Performance test comparing old vs new vs confluent serializer performance.
     */
    @Test
    @Tag(Constants.ACCEPTANCE)
    void testSerializerPerformanceComparison() throws Exception {
        String groupId = "perf-test-serializer";
        String oldArtifactId = TestUtils.generateSubject() + "-old";
        String newArtifactId = TestUtils.generateSubject() + "-new";

        // Create test messages of different sizes
        TestCmmn.UUID simpleMessage = TestCmmn.UUID.newBuilder()
                .setMsb(123L)
                .setLsb(456L)
                .build();

        ProtobufTestMessageFactory factory = new ProtobufTestMessageFactory();
        ProtobufTestMessage complexMessage = factory.generateMessage(1);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("PROTOBUF SERIALIZER PERFORMANCE COMPARISON");
        System.out.println("=".repeat(80));
        System.out.println("Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("Measured iterations: " + MEASURED_ITERATIONS);
        System.out.println("=".repeat(80));

        // Test with simple message (TestCmmn.UUID)
        testSerializerWithMessage(groupId, oldArtifactId + "-simple", newArtifactId + "-simple",
                simpleMessage, "Simple (UUID)");

        // Test with complex message (ProtobufTestMessage)
        testSerializerWithMessage(groupId, oldArtifactId + "-complex", newArtifactId + "-complex",
                complexMessage, "Complex (ProtobufTestMessage)");

        System.out.println("=".repeat(80));
    }

    /**
     * Performance test comparing old vs new vs confluent deserializer performance.
     */
    @Test
    @Tag(Constants.ACCEPTANCE)
    void testDeserializerPerformanceComparison() throws Exception {
        String groupId = "perf-test-deserializer";
        String oldArtifactId = TestUtils.generateSubject() + "-old";
        String newArtifactId = TestUtils.generateSubject() + "-new";

        TestCmmn.UUID simpleMessage = TestCmmn.UUID.newBuilder()
                .setMsb(123L)
                .setLsb(456L)
                .build();

        ProtobufTestMessageFactory factory = new ProtobufTestMessageFactory();
        ProtobufTestMessage complexMessage = factory.generateMessage(1);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("PROTOBUF DESERIALIZER PERFORMANCE COMPARISON");
        System.out.println("=".repeat(80));
        System.out.println("Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("Measured iterations: " + MEASURED_ITERATIONS);
        System.out.println("=".repeat(80));

        // Test with simple message
        testDeserializerWithMessage(groupId, oldArtifactId + "-simple", newArtifactId + "-simple",
                simpleMessage, "Simple (UUID)");

        // Test with complex message
        testDeserializerWithMessage(groupId, oldArtifactId + "-complex", newArtifactId + "-complex",
                complexMessage, "Complex (ProtobufTestMessage)");

        System.out.println("=".repeat(80));
    }

    /**
     * Full round-trip performance test (serialize + deserialize).
     */
    @Test
    @Tag(Constants.ACCEPTANCE)
    void testRoundTripPerformanceComparison() throws Exception {
        String groupId = "perf-test-roundtrip";
        String oldArtifactId = TestUtils.generateSubject() + "-old";
        String newArtifactId = TestUtils.generateSubject() + "-new";

        TestCmmn.UUID simpleMessage = TestCmmn.UUID.newBuilder()
                .setMsb(123L)
                .setLsb(456L)
                .build();

        ProtobufTestMessageFactory factory = new ProtobufTestMessageFactory();
        ProtobufTestMessage complexMessage = factory.generateMessage(1);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("PROTOBUF ROUND-TRIP (SERIALIZE + DESERIALIZE) PERFORMANCE COMPARISON");
        System.out.println("=".repeat(80));
        System.out.println("Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("Measured iterations: " + MEASURED_ITERATIONS);
        System.out.println("=".repeat(80));

        // Test with simple message
        testRoundTripWithMessage(groupId, oldArtifactId + "-simple", newArtifactId + "-simple",
                simpleMessage, "Simple (UUID)");

        // Test with complex message
        testRoundTripWithMessage(groupId, oldArtifactId + "-complex", newArtifactId + "-complex",
                complexMessage, "Complex (ProtobufTestMessage)");

        System.out.println("=".repeat(80));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T extends Message> void testSerializerWithMessage(String groupId, String oldArtifactId,
            String newArtifactId, T message, String messageType) throws Exception {

        System.out.println("\n--- " + messageType + " Message Serialization ---");

        // Setup OLD serializer (Apicurio wire-schema v3.1.2)
        io.apicurio.registry.old.serde.protobuf.ProtobufKafkaSerializer<Message> oldSerializer =
                new io.apicurio.registry.old.serde.protobuf.ProtobufKafkaSerializer<>();
        Map<String, Object> oldConfig = new HashMap<>();
        oldConfig.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
        oldConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        oldConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, oldArtifactId);
        oldConfig.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
        oldSerializer.configure(oldConfig, false);

        // Setup NEW serializer (Apicurio protobuf4j)
        io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer<Message> newSerializer =
                new io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer<>();
        Map<String, Object> newConfig = new HashMap<>();
        newConfig.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
        newConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        newConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, newArtifactId);
        newConfig.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
        newSerializer.configure(newConfig, false);

        // Setup CONFLUENT serializer
        KafkaProtobufSerializer<Message> confluentSerializer = new KafkaProtobufSerializer<>();
        Map<String, String> confluentProps = new HashMap<>();
        confluentProps.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                getRegistryBaseUrl() + "/apis/ccompat/v7");
        confluentProps.put(KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, "true");
        confluentSerializer.configure(confluentProps, false);

        try {
            RecordHeaders headers = new RecordHeaders();

            // Register schemas first (serialize once to trigger auto-registration)
            oldSerializer.serialize("test-topic", headers, message);
            newSerializer.serialize("test-topic", headers, message);
            confluentSerializer.serialize("confluent-topic-" + messageType.replace(" ", "-"), message);

            // Wait for registrations
            waitForArtifact(groupId, oldArtifactId);
            waitForArtifact(groupId, newArtifactId);

            // Warmup OLD
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                oldSerializer.serialize("test-topic", new RecordHeaders(), message);
            }

            // Warmup NEW
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                newSerializer.serialize("test-topic", new RecordHeaders(), message);
            }

            // Warmup CONFLUENT
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                confluentSerializer.serialize("confluent-topic", message);
            }

            // Measure OLD serializer
            long oldStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                oldSerializer.serialize("test-topic", new RecordHeaders(), message);
            }
            long oldEndTime = System.nanoTime();
            long oldTotalNanos = oldEndTime - oldStartTime;

            // Measure NEW serializer
            long newStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                newSerializer.serialize("test-topic", new RecordHeaders(), message);
            }
            long newEndTime = System.nanoTime();
            long newTotalNanos = newEndTime - newStartTime;

            // Measure CONFLUENT serializer
            long confluentStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                confluentSerializer.serialize("confluent-topic", message);
            }
            long confluentEndTime = System.nanoTime();
            long confluentTotalNanos = confluentEndTime - confluentStartTime;

            printResults("Serialization", oldTotalNanos, newTotalNanos, confluentTotalNanos);

        } finally {
            oldSerializer.close();
            newSerializer.close();
            confluentSerializer.close();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T extends Message> void testDeserializerWithMessage(String groupId, String oldArtifactId,
            String newArtifactId, T message, String messageType) throws Exception {

        System.out.println("\n--- " + messageType + " Message Deserialization ---");

        // Setup Apicurio serializers and deserializers
        io.apicurio.registry.old.serde.protobuf.ProtobufKafkaSerializer<Message> oldSerializer =
                new io.apicurio.registry.old.serde.protobuf.ProtobufKafkaSerializer<>();
        io.apicurio.registry.old.serde.protobuf.ProtobufKafkaDeserializer<T> oldDeserializer =
                new io.apicurio.registry.old.serde.protobuf.ProtobufKafkaDeserializer<>();

        io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer<Message> newSerializer =
                new io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer<>();
        io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer<T> newDeserializer =
                new io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer<>();

        // Setup Confluent serializer and deserializer
        KafkaProtobufSerializer<Message> confluentSerializer = new KafkaProtobufSerializer<>();
        KafkaProtobufDeserializer<T> confluentDeserializer = new KafkaProtobufDeserializer<>();

        Map<String, Object> oldConfig = new HashMap<>();
        oldConfig.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
        oldConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        oldConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, oldArtifactId);
        oldConfig.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");

        Map<String, Object> newConfig = new HashMap<>();
        newConfig.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
        newConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        newConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, newArtifactId);
        newConfig.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");

        Map<String, String> confluentProps = new HashMap<>();
        confluentProps.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                getRegistryBaseUrl() + "/apis/ccompat/v7");
        confluentProps.put(KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, "true");

        oldSerializer.configure(oldConfig, false);
        oldDeserializer.configure(oldConfig, false);
        newSerializer.configure(newConfig, false);
        newDeserializer.configure(newConfig, false);
        confluentSerializer.configure(confluentProps, false);
        confluentDeserializer.configure(confluentProps, false);

        try {
            RecordHeaders headers = new RecordHeaders();

            // Serialize messages to create test data
            byte[] oldSerializedData = oldSerializer.serialize("test-topic", headers, message);
            byte[] newSerializedData = newSerializer.serialize("test-topic", headers, message);
            byte[] confluentSerializedData = confluentSerializer.serialize("confluent-topic", message);

            // Wait for registrations
            waitForArtifact(groupId, oldArtifactId);
            waitForArtifact(groupId, newArtifactId);

            // Warmup OLD
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                oldDeserializer.deserialize("test-topic", new RecordHeaders(), oldSerializedData);
            }

            // Warmup NEW
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                newDeserializer.deserialize("test-topic", new RecordHeaders(), newSerializedData);
            }

            // Warmup CONFLUENT
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                confluentDeserializer.deserialize("confluent-topic", confluentSerializedData);
            }

            // Measure OLD deserializer
            long oldStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                oldDeserializer.deserialize("test-topic", new RecordHeaders(), oldSerializedData);
            }
            long oldEndTime = System.nanoTime();
            long oldTotalNanos = oldEndTime - oldStartTime;

            // Measure NEW deserializer
            long newStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                newDeserializer.deserialize("test-topic", new RecordHeaders(), newSerializedData);
            }
            long newEndTime = System.nanoTime();
            long newTotalNanos = newEndTime - newStartTime;

            // Measure CONFLUENT deserializer
            long confluentStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                confluentDeserializer.deserialize("confluent-topic", confluentSerializedData);
            }
            long confluentEndTime = System.nanoTime();
            long confluentTotalNanos = confluentEndTime - confluentStartTime;

            printResults("Deserialization", oldTotalNanos, newTotalNanos, confluentTotalNanos);

        } finally {
            oldSerializer.close();
            oldDeserializer.close();
            newSerializer.close();
            newDeserializer.close();
            confluentSerializer.close();
            confluentDeserializer.close();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T extends Message> void testRoundTripWithMessage(String groupId, String oldArtifactId,
            String newArtifactId, T message, String messageType) throws Exception {

        System.out.println("\n--- " + messageType + " Message Round-Trip ---");

        // Setup Apicurio serializers and deserializers
        io.apicurio.registry.old.serde.protobuf.ProtobufKafkaSerializer<Message> oldSerializer =
                new io.apicurio.registry.old.serde.protobuf.ProtobufKafkaSerializer<>();
        io.apicurio.registry.old.serde.protobuf.ProtobufKafkaDeserializer<T> oldDeserializer =
                new io.apicurio.registry.old.serde.protobuf.ProtobufKafkaDeserializer<>();

        io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer<Message> newSerializer =
                new io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer<>();
        io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer<T> newDeserializer =
                new io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer<>();

        // Setup Confluent serializer and deserializer
        KafkaProtobufSerializer<Message> confluentSerializer = new KafkaProtobufSerializer<>();
        KafkaProtobufDeserializer<T> confluentDeserializer = new KafkaProtobufDeserializer<>();

        Map<String, Object> oldConfig = new HashMap<>();
        oldConfig.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
        oldConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        oldConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, oldArtifactId);
        oldConfig.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");

        Map<String, Object> newConfig = new HashMap<>();
        newConfig.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
        newConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        newConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, newArtifactId);
        newConfig.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");

        Map<String, String> confluentProps = new HashMap<>();
        confluentProps.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                getRegistryBaseUrl() + "/apis/ccompat/v7");
        confluentProps.put(KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, "true");

        oldSerializer.configure(oldConfig, false);
        oldDeserializer.configure(oldConfig, false);
        newSerializer.configure(newConfig, false);
        newDeserializer.configure(newConfig, false);
        confluentSerializer.configure(confluentProps, false);
        confluentDeserializer.configure(confluentProps, false);

        try {
            RecordHeaders headers = new RecordHeaders();

            // Initial registration
            oldSerializer.serialize("test-topic", headers, message);
            newSerializer.serialize("test-topic", headers, message);
            confluentSerializer.serialize("confluent-topic", message);

            waitForArtifact(groupId, oldArtifactId);
            waitForArtifact(groupId, newArtifactId);

            // Warmup OLD
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                RecordHeaders h = new RecordHeaders();
                byte[] data = oldSerializer.serialize("test-topic", h, message);
                oldDeserializer.deserialize("test-topic", h, data);
            }

            // Warmup NEW
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                RecordHeaders h = new RecordHeaders();
                byte[] data = newSerializer.serialize("test-topic", h, message);
                newDeserializer.deserialize("test-topic", h, data);
            }

            // Warmup CONFLUENT
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                byte[] data = confluentSerializer.serialize("confluent-topic", message);
                confluentDeserializer.deserialize("confluent-topic", data);
            }

            // Measure OLD round-trip
            long oldStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                RecordHeaders h = new RecordHeaders();
                byte[] data = oldSerializer.serialize("test-topic", h, message);
                oldDeserializer.deserialize("test-topic", h, data);
            }
            long oldEndTime = System.nanoTime();
            long oldTotalNanos = oldEndTime - oldStartTime;

            // Measure NEW round-trip
            long newStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                RecordHeaders h = new RecordHeaders();
                byte[] data = newSerializer.serialize("test-topic", h, message);
                newDeserializer.deserialize("test-topic", h, data);
            }
            long newEndTime = System.nanoTime();
            long newTotalNanos = newEndTime - newStartTime;

            // Measure CONFLUENT round-trip
            long confluentStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                byte[] data = confluentSerializer.serialize("confluent-topic", message);
                confluentDeserializer.deserialize("confluent-topic", data);
            }
            long confluentEndTime = System.nanoTime();
            long confluentTotalNanos = confluentEndTime - confluentStartTime;

            printResults("Round-trip", oldTotalNanos, newTotalNanos, confluentTotalNanos);

        } finally {
            oldSerializer.close();
            oldDeserializer.close();
            newSerializer.close();
            newDeserializer.close();
            confluentSerializer.close();
            confluentDeserializer.close();
        }
    }

    private void waitForArtifact(String groupId, String artifactId) throws Exception {
        TestUtils.retry(() -> {
            VersionMetaData meta = registryClient.groups().byGroupId(groupId)
                    .artifacts().byArtifactId(artifactId)
                    .versions().byVersionExpression("branch=latest").get();
            return meta != null;
        });
    }

    private void printResults(String operation, long oldTotalNanos, long newTotalNanos, long confluentTotalNanos) {
        double oldTotalMs = oldTotalNanos / 1_000_000.0;
        double newTotalMs = newTotalNanos / 1_000_000.0;
        double confluentTotalMs = confluentTotalNanos / 1_000_000.0;

        double oldAvgMicros = (oldTotalNanos / 1000.0) / MEASURED_ITERATIONS;
        double newAvgMicros = (newTotalNanos / 1000.0) / MEASURED_ITERATIONS;
        double confluentAvgMicros = (confluentTotalNanos / 1000.0) / MEASURED_ITERATIONS;

        System.out.println(String.format("  OLD (Apicurio wire-schema): %,d iterations in %.2f ms (avg: %.2f µs/op)",
                MEASURED_ITERATIONS, oldTotalMs, oldAvgMicros));
        System.out.println(String.format("  NEW (Apicurio protobuf4j):  %,d iterations in %.2f ms (avg: %.2f µs/op)",
                MEASURED_ITERATIONS, newTotalMs, newAvgMicros));
        System.out.println(String.format("  CONFLUENT:                  %,d iterations in %.2f ms (avg: %.2f µs/op)",
                MEASURED_ITERATIONS, confluentTotalMs, confluentAvgMicros));

        // Compare NEW vs OLD
        double newVsOldPercent = ((newTotalNanos - oldTotalNanos) / (double) oldTotalNanos) * 100;
        String newVsOld = newVsOldPercent < 0
            ? String.format("%.1f%% faster", Math.abs(newVsOldPercent))
            : String.format("%.1f%% slower", newVsOldPercent);

        // Compare NEW vs CONFLUENT
        double newVsConfluentPercent = ((newTotalNanos - confluentTotalNanos) / (double) confluentTotalNanos) * 100;
        String newVsConfluent = newVsConfluentPercent < 0
            ? String.format("%.1f%% faster", Math.abs(newVsConfluentPercent))
            : String.format("%.1f%% slower", newVsConfluentPercent);

        System.out.println(String.format("  >> %s: NEW vs OLD: %s | NEW vs CONFLUENT: %s",
                operation, newVsOld, newVsConfluent));
    }
}
