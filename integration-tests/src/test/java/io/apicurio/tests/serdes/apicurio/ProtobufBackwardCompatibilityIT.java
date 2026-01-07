package io.apicurio.tests.serdes.apicurio;

import com.google.protobuf.Message;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.common.serdes.proto.TestCmmn;
import io.apicurio.tests.utils.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Critical backward compatibility test: Verifies that protobuf schemas auto-registered
 * using the OLD wire-schema based serializer (v3.1.2) produce the same content ID
 * as schemas registered using the NEW protobuf4j based serializer (current version).
 * This ensures that existing Kafka producers that auto-registered schemas before
 * the wire-schema → protobuf4j migration will not create duplicate schemas after
 * the migration.
 */
@Tag(Constants.SERDES)
@QuarkusIntegrationTest
public class ProtobufBackwardCompatibilityIT extends ApicurioRegistryBaseIT {

    /**
     * Test that auto-registration produces the same content ID regardless of whether
     * the old (wire-schema) or new (protobuf4j) serializer is used.
     *
     * Test flow:
     * 1. Use OLD serializer (v3.1.2 with wire-schema) to serialize a message with auto-register=true
     * 2. Extract the content ID from the registered schema
     * 3. Use NEW serializer (current with protobuf4j) to serialize the same message type with auto-register=true
     * 4. Extract the content ID from the registered schema
     * 5. Assert that both content IDs are identical
     *
     * Note: We use separate artifact IDs to avoid deletion (which may be disabled).
     */
    @Test
    @Tag(Constants.ACCEPTANCE)
    void testOldAndNewSerializersProduceSameContentId() throws Exception {
        String groupId = "backward-compat-test";
        String oldArtifactId = TestUtils.generateSubject() + "-old";
        String newArtifactId = TestUtils.generateSubject() + "-new";

        // Create a test message
        TestCmmn.UUID message = TestCmmn.UUID.newBuilder()
                .setMsb(123L)
                .setLsb(456L)
                .build();

        // Test 1: Use OLD serializer (wire-schema, v3.1.2)
        long oldContentId;
        try (io.apicurio.registry.old.serde.protobuf.ProtobufKafkaSerializer<Message> oldSerializer =
                new io.apicurio.registry.old.serde.protobuf.ProtobufKafkaSerializer<>()) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, oldArtifactId);
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");

            oldSerializer.configure(config, false);

            // Serialize the message - this will auto-register the schema
            byte[] serialized = oldSerializer.serialize("test-topic", new RecordHeaders(), message);

            // Wait for registration to complete
            TestUtils.retry(() -> {
                VersionMetaData meta = registryClient.groups().byGroupId(groupId)
                        .artifacts().byArtifactId(oldArtifactId)
                        .versions().byVersionExpression("branch=latest").get();
                return meta != null;
            });

            // Get the content ID
            VersionMetaData oldMeta = registryClient.groups().byGroupId(groupId)
                    .artifacts().byArtifactId(oldArtifactId)
                    .versions().byVersionExpression("branch=latest").get();

            oldContentId = oldMeta.getContentId();

            System.out.println("=== OLD SERIALIZER (wire-schema, v3.1.2) ===");
            System.out.println("Content ID: " + oldContentId);

            // Fetch and print the canonical content
            byte[] oldContent = registryClient.ids().contentIds().byContentId(oldContentId).get().readAllBytes();
            System.out.println("Canonical .proto text:");
            System.out.println(new String(oldContent));
        }

        // Test 2: Use NEW serializer (protobuf4j, current version)
        long newContentId;
        try (io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer<Message> newSerializer =
                new io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer<>()) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, newArtifactId);
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");

            newSerializer.configure(config, false);

            // Serialize the same message - this will auto-register the schema again
            byte[] serialized = newSerializer.serialize("test-topic", new RecordHeaders(), message);

            // Wait for registration to complete
            TestUtils.retry(() -> {
                VersionMetaData meta = registryClient.groups().byGroupId(groupId)
                        .artifacts().byArtifactId(newArtifactId)
                        .versions().byVersionExpression("branch=latest").get();
                return meta != null;
            });

            // Get the content ID
            VersionMetaData newMeta = registryClient.groups().byGroupId(groupId)
                    .artifacts().byArtifactId(newArtifactId)
                    .versions().byVersionExpression("branch=latest").get();

            newContentId = newMeta.getContentId();

            System.out.println("=== NEW SERIALIZER (protobuf4j, current) ===");
            System.out.println("Content ID: " + newContentId);

            // Fetch and print the canonical content
            byte[] newContent = registryClient.ids().contentIds().byContentId(newContentId).get().readAllBytes();
            System.out.println("Canonical .proto text:");
            System.out.println(new String(newContent));
        }

        // THE CRITICAL ASSERTION: Both must produce the same content ID
        assertEquals(oldContentId, newContentId,
                String.format("BACKWARD COMPATIBILITY FAILURE: Old serializer (wire-schema) produced content ID %d " +
                        "but new serializer (protobuf4j) produced content ID %d. This means existing Kafka producers " +
                        "that auto-registered schemas before the migration will create duplicate schemas after the migration!",
                        oldContentId, newContentId));

        System.out.println("BACKWARD COMPATIBILITY SUCCESS: Both serializers produced content ID " + oldContentId);
    }
}
