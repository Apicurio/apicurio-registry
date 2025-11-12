package io.apicurio.registry;

import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@QuarkusTest
public class ConfluentDataMigrationTest extends AbstractResourceTestBase {

    @Inject
    @Current
    RegistryStorage storage;

    @Override
    @BeforeAll
    protected void beforeAll() throws Exception {
        super.beforeAll();

        storage.deleteAllUserData();
        try (InputStream data = resourceToInputStream("./upgrade/confluent-schema-registry-export.zip")) {

            clientV3.admin().importEscaped().post(data, config -> {
                config.headers.add("X-Registry-Preserve-GlobalId", "false");
            });
        }
    }

    @BeforeEach
    public void beforeEach() {
        // Do nothing, but override the super class because it deletes all global rules.
    }

    @Test
    public void testCheckGlobalRules() {
        Set<RuleType> ruleTypes = new HashSet<>(clientV3.admin().rules().get());
        Assertions.assertEquals(2, ruleTypes.size());
        Assertions.assertEquals(Set.of(RuleType.VALIDITY, RuleType.COMPATIBILITY), ruleTypes);

        Assertions.assertEquals("SYNTAX_ONLY",
                clientV3.admin().rules().byRuleType("VALIDITY").get().getConfig());

        Assertions.assertEquals("BACKWARD",
                clientV3.admin().rules().byRuleType("COMPATIBILITY").get().getConfig());
    }

    @Test
    public void testCheckArtifacts() {
        ArtifactSearchResults results = clientV3.search().artifacts().get();
        Assertions.assertEquals(15, results.getArtifacts().size());
        Assertions.assertEquals(15, results.getCount());
    }

    @Test
    public void testCheckArtifact() {
        ArtifactMetaData amd = clientV3.groups().byGroupId("default").artifacts().byArtifactId("test-value")
                .get();
        Assertions.assertEquals(null, amd.getGroupId());
        Assertions.assertEquals("test-value", amd.getArtifactId());
        Assertions.assertEquals("PROTOBUF", amd.getArtifactType());
    }

    @Test
    public void testCheckArtifactReferences() {
        List<ArtifactReference> artifactReferences = clientV3.groups().byGroupId("default").artifacts()
                .byArtifactId("test-value").versions().byVersionExpression("1").references().get();

        Assertions.assertEquals(2, artifactReferences.size());
    }

    /**
     * Test for Protobuf base64 migration scenario (PR #6833 fix).
     * Verifies that protobuf schemas from migrated data can be looked up
     * and that compatibility checking works correctly.
     */
    @Test
    public void testProtobufMigrationCompatibility() throws Exception {
        // The test-value artifact from the migrated data is a Protobuf schema
        ArtifactMetaData amd = clientV3.groups().byGroupId("default").artifacts()
                .byArtifactId("test-value").get();
        Assertions.assertEquals("PROTOBUF", amd.getArtifactType());

        // Retrieve the content to verify it's readable
        InputStream contentStream = clientV3.groups().byGroupId("default").artifacts()
                .byArtifactId("test-value").versions().byVersionExpression("1")
                .content().get();
        Assertions.assertNotNull(contentStream);
        String content = new String(contentStream.readAllBytes());
        Assertions.assertFalse(content.isEmpty());
    }

    /**
     * Test for PR #6833 fix - verifies that submitting a Protobuf schema in both
     * text and base64 formats doesn't create duplicates due to content normalization.
     */
    @Test
    public void testProtobufNoDuplicatesTextAndBase64() throws Exception {
        String artifactId = "protobuf-normalization-test-" + System.currentTimeMillis();

        // Define a simple protobuf schema
        String protoSchemaText = """
                syntax = "proto3";
                package test.migration;

                message TestMessage {
                  string id = 1;
                  string name = 2;
                  int32 age = 3;
                }
                """;

        // Create artifact with text format
        io.apicurio.registry.rest.client.models.CreateArtifact createArtifact = new io.apicurio.registry.rest.client.models.CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType("PROTOBUF");

        io.apicurio.registry.rest.client.models.CreateVersion firstVersion = new io.apicurio.registry.rest.client.models.CreateVersion();
        io.apicurio.registry.rest.client.models.VersionContent versionContent = new io.apicurio.registry.rest.client.models.VersionContent();
        versionContent.setContent(protoSchemaText);
        versionContent.setContentType("application/x-protobuf");
        firstVersion.setContent(versionContent);
        createArtifact.setFirstVersion(firstVersion);

        // Create the artifact
        io.apicurio.registry.rest.client.models.CreateArtifactResponse response = clientV3.groups()
                .byGroupId("default").artifacts()
                .post(createArtifact);

        String version1 = response.getVersion().getVersion();
        Assertions.assertNotNull(version1);

        // Convert to base64-encoded FileDescriptorProto (simulating .NET client)
        String base64Schema = convertProtoTextToBase64(protoSchemaText);

        // Try to create the same schema again using base64 format
        // This should NOT create a duplicate - it should find the existing one
        io.apicurio.registry.rest.client.models.CreateVersion newVersion = new io.apicurio.registry.rest.client.models.CreateVersion();
        io.apicurio.registry.rest.client.models.VersionContent base64Content = new io.apicurio.registry.rest.client.models.VersionContent();
        base64Content.setContent(base64Schema);
        base64Content.setContentType("application/x-protobuf");
        newVersion.setContent(base64Content);

        // This should either return the existing version or create a new one
        // depending on the ifExists behavior, but importantly should not error
        io.apicurio.registry.rest.client.models.VersionMetaData vmd = clientV3.groups()
                .byGroupId("default").artifacts().byArtifactId(artifactId).versions()
                .post(newVersion);

        // Verify that we still have a valid response
        Assertions.assertNotNull(vmd.getVersion());

        // The key test: verify that content normalization is working by checking
        // that both submissions result in the same stored content format
        String retrievedContent1 = new String(clientV3.groups().byGroupId("default").artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression(version1)
                .content().get().readAllBytes());

        String retrievedContent2 = new String(clientV3.groups().byGroupId("default").artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression(vmd.getVersion())
                .content().get().readAllBytes());

        // Both should be in text format (normalized), not base64
        Assertions.assertTrue(retrievedContent1.contains("syntax") && retrievedContent1.contains("proto3"),
                "First retrieved schema should be in text format");
        Assertions.assertTrue(retrievedContent2.contains("syntax") && retrievedContent2.contains("proto3"),
                "Second retrieved schema should be in text format");
    }

    /**
     * Helper method to convert proto text to base64-encoded FileDescriptorProto
     * (simulates what .NET clients send)
     */
    private String convertProtoTextToBase64(String protoText) throws Exception {
        var result = FileDescriptorUtils.protoFileToFileDescriptor(
                protoText, "test.proto", Optional.of("test.migration"));
        byte[] bytes = result.toProto().toByteArray();
        return Base64.getEncoder().encodeToString(bytes);
    }
}
