package io.apicurio.registry.noprofile.serde;

import com.google.protobuf.Descriptors;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer;
import io.apicurio.registry.support.TestCmmn;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorToProtoConverter;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchemaUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static io.apicurio.registry.utils.tests.TestUtils.waitForSchema;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that auto-registration produces the same canonical content as manual upload.
 * This is critical to ensure producers with auto-registration don't create duplicate schemas.
 */
@QuarkusTest
public class ProtobufAutoRegistrationConsistencyTest extends AbstractResourceTestBase {

    /**
     * Critical test: Verify that auto-registering from a Message produces the same canonical content
     * (and thus the same content hash) as manually uploading the .proto file.
     *
     * This ensures that:
     * 1. A producer with auto-registration enabled won't create a duplicate schema
     * 2. Content-based deduplication works correctly
     * 3. The FileDescriptorToProtoConverter generates canonical .proto text
     */
    @Test
    public void testAutoRegistrationProducesSameContentHashAsManualUpload() throws Exception {
        String groupId = "auto-reg-consistency";
        String artifactId = "TestUUID";

        // Step 1: Manually upload the .proto file
        String protoContent = "syntax = \"proto3\";\n" +
                "package io.apicurio.registry.common.proto;\n" +
                "\n" +
                "option java_package = \"io.apicurio.registry.support\";\n" +
                "option java_outer_classname = \"TestCmmn\";\n" +
                "option java_multiple_files = true;\n" +
                "\n" +
                "message UUID {\n" +
                "  int64 msb = 1;\n" +
                "  int64 lsb = 2;\n" +
                "}\n";

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.PROTOBUF);
        createArtifact.setFirstVersion(new CreateVersion());
        createArtifact.getFirstVersion().setContent(new VersionContent());
        createArtifact.getFirstVersion().getContent().setContent(protoContent);
        createArtifact.getFirstVersion().getContent().setContentType(ContentTypes.APPLICATION_PROTOBUF);

        CreateArtifactResponse manualResponse = clientV3.groups().byGroupId(groupId)
                .artifacts().post(createArtifact);

        long manualContentId = manualResponse.getVersion().getContentId();

        // Step 2: Auto-register the same schema from a compiled Message
        try (Serializer<TestCmmn.UUID> serializer = new ProtobufKafkaSerializer<>()) {
            Map<String, Object> config = Map.of(
                    SerdeConfig.REGISTRY_URL, registryV3ApiUrl,
                    SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId,
                    SerdeConfig.AUTO_REGISTER_ARTIFACT, "true"
            );
            serializer.configure(config, false);

            TestCmmn.UUID message = TestCmmn.UUID.newBuilder()
                    .setMsb(123L)
                    .setLsb(456L)
                    .build();

            String topic = "test-topic";
            byte[] bytes = serializer.serialize(topic, message);

            // Wait for auto-registration to complete
            waitForSchema(globalId -> {
                try {
                    VersionMetaData versionMeta = clientV3.groups().byGroupId(groupId)
                            .artifacts().byArtifactId(artifactId)
                            .versions().byVersionExpression("branch=latest").get();
                    return versionMeta != null;
                } catch (Exception e) {
                    return false;
                }
            }, bytes);

            // Step 3: Verify both produce the same content ID
            VersionMetaData autoRegMeta = clientV3.groups().byGroupId(groupId)
                    .artifacts().byArtifactId(artifactId)
                    .versions().byVersionExpression("branch=latest").get();

            long autoRegContentId = autoRegMeta.getContentId();

            // THE CRITICAL ASSERTION: Same content ID means same canonical content
            assertEquals(manualContentId, autoRegContentId,
                    "Auto-registration should produce the same content hash as manual upload. " +
                    "Different content IDs mean the canonical forms differ, which would cause " +
                    "duplicate schemas to be registered.");

            // Verify content is actually the same
            byte[] manualContent = clientV3.ids().contentIds()
                    .byContentId(manualContentId).get().readAllBytes();
            byte[] autoRegContent = clientV3.ids().contentIds()
                    .byContentId(autoRegContentId).get().readAllBytes();

            String manualText = new String(manualContent);
            String autoRegText = new String(autoRegContent);

            assertEquals(manualText, autoRegText,
                    "The actual canonical .proto text should be identical");
        }
    }

    /**
     * Verify that our FileDescriptorToProtoConverter produces deterministic output.
     * This is critical for content hashing.
     */
    @Test
    public void testFileDescriptorToProtoConverterIsDeterministic() throws Exception {
        String protoText = "syntax = \"proto3\";\n" +
                "package test;\n" +
                "\n" +
                "message Sample {\n" +
                "  string field1 = 1;\n" +
                "  int32 field2 = 2;\n" +
                "  bool field3 = 3;\n" +
                "}\n";

        // Compile to FileDescriptor multiple times
        Descriptors.FileDescriptor fd1 = ProtobufSchemaUtils.parseAndCompile(
                "test.proto", protoText, Collections.emptyMap());
        Descriptors.FileDescriptor fd2 = ProtobufSchemaUtils.parseAndCompile(
                "test.proto", protoText, Collections.emptyMap());

        // Convert to .proto text multiple times
        String output1 = FileDescriptorToProtoConverter.convert(fd1);
        String output2 = FileDescriptorToProtoConverter.convert(fd2);
        String output3 = FileDescriptorToProtoConverter.convert(fd1); // Same FD again

        // All outputs must be identical
        assertEquals(output1, output2, "Same schema should produce identical output");
        assertEquals(output2, output3, "Same FileDescriptor should always produce identical output");

        // Verify output can be re-parsed
        Descriptors.FileDescriptor fd3 = ProtobufSchemaUtils.parseAndCompile(
                "test.proto", output1, Collections.emptyMap());

        String output4 = FileDescriptorToProtoConverter.convert(fd3);
        assertEquals(output1, output4, "Round-trip should produce identical output");
    }
}
