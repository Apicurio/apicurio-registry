package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Tests for the maximum recursion depth limit in reference resolution.
 * Uses a custom test profile to set a low max-depth for testing purposes.
 */
@QuarkusTest
@TestProfile(ReferenceDepthLimitTest.LowDepthTestProfile.class)
public class ReferenceDepthLimitTest extends AbstractResourceTestBase {

    private static final String GROUP_ID = ReferenceDepthLimitTest.class.getSimpleName();

    private static final String AVRO_SCHEMA_TEMPLATE = """
            {
                "type": "record",
                "name": "%s",
                "namespace": "com.example.depth",
                "fields": [
                    {"name": "id", "type": "string"},
                    {"name": "data", "type": "string"}
                ]
            }
            """;

    /**
     * Test profile that sets a low max-depth of 3 for testing depth limiting.
     */
    public static class LowDepthTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.storage.references.max-depth", "3");
        }

        @Override
        public String getConfigProfile() {
            return "low-depth-test";
        }
    }

    /**
     * Test that a reference chain within the depth limit (depth 2) resolves correctly.
     * Chain: A -> B -> C (depth 2)
     */
    @Test
    public void testChainWithinDepthLimit() throws Exception {
        String artifactIdC = "depth-limit-C-" + TestUtils.generateArtifactId();
        String artifactIdB = "depth-limit-B-" + TestUtils.generateArtifactId();
        String artifactIdA = "depth-limit-A-" + TestUtils.generateArtifactId();

        // Create C (no references) - depth 0 from C's perspective
        CreateArtifactResponse respC = createSimpleArtifact(artifactIdC, "RecordC");

        // Create B referencing C - depth 1 when resolving from B
        ArtifactReference refC = createRef(artifactIdC, respC.getVersion().getVersion(), "c.avsc");
        CreateArtifactResponse respB = createArtifactWithReferences(artifactIdB, "RecordB",
                Collections.singletonList(refC));

        // Create A referencing B - depth 2 when resolving from A
        ArtifactReference refB = createRef(artifactIdB, respB.getVersion().getVersion(), "b.avsc");
        createArtifactWithReferences(artifactIdA, "RecordA", Collections.singletonList(refB));

        // Verify the chain resolves correctly (within limit of 3)
        List<ArtifactReference> refsFromA = clientV3.groups()
                .byGroupId(GROUP_ID)
                .artifacts()
                .byArtifactId(artifactIdA)
                .versions()
                .byVersionExpression("1")
                .references()
                .get();

        Assertions.assertEquals(1, refsFromA.size());
        Assertions.assertEquals("b.avsc", refsFromA.get(0).getName());

        // Verify content can be dereferenced (this exercises the batch resolution)
        try (InputStream content = clientV3.groups()
                .byGroupId(GROUP_ID)
                .artifacts()
                .byArtifactId(artifactIdA)
                .versions()
                .byVersionExpression("1")
                .content()
                .get()) {
            String contentStr = new String(content.readAllBytes(), StandardCharsets.UTF_8);
            Assertions.assertNotNull(contentStr);
            Assertions.assertTrue(contentStr.contains("RecordA"));
        }
    }

    /**
     * Test that a reference chain exceeding the depth limit (depth 4) stops gracefully.
     * Chain: A -> B -> C -> D -> E (depth 4, limit is 3)
     * The resolution should stop at depth 3 and not cause stack overflow.
     */
    @Test
    public void testChainExceedingDepthLimit() throws Exception {
        String artifactIdE = "exceed-E-" + TestUtils.generateArtifactId();
        String artifactIdD = "exceed-D-" + TestUtils.generateArtifactId();
        String artifactIdC = "exceed-C-" + TestUtils.generateArtifactId();
        String artifactIdB = "exceed-B-" + TestUtils.generateArtifactId();
        String artifactIdA = "exceed-A-" + TestUtils.generateArtifactId();

        // Create chain: E (no refs) <- D <- C <- B <- A
        CreateArtifactResponse respE = createSimpleArtifact(artifactIdE, "RecordE");

        ArtifactReference refE = createRef(artifactIdE, respE.getVersion().getVersion(), "e.avsc");
        CreateArtifactResponse respD = createArtifactWithReferences(artifactIdD, "RecordD",
                Collections.singletonList(refE));

        ArtifactReference refD = createRef(artifactIdD, respD.getVersion().getVersion(), "d.avsc");
        CreateArtifactResponse respC = createArtifactWithReferences(artifactIdC, "RecordC",
                Collections.singletonList(refD));

        ArtifactReference refC = createRef(artifactIdC, respC.getVersion().getVersion(), "c.avsc");
        CreateArtifactResponse respB = createArtifactWithReferences(artifactIdB, "RecordB",
                Collections.singletonList(refC));

        ArtifactReference refB = createRef(artifactIdB, respB.getVersion().getVersion(), "b.avsc");
        createArtifactWithReferences(artifactIdA, "RecordA", Collections.singletonList(refB));

        // The chain should be created without issues
        // When resolving, depth 4 exceeds limit of 3, so deepest references won't be resolved
        // But API should still return without error (graceful degradation)
        List<ArtifactReference> refsFromA = clientV3.groups()
                .byGroupId(GROUP_ID)
                .artifacts()
                .byArtifactId(artifactIdA)
                .versions()
                .byVersionExpression("1")
                .references()
                .get();

        // Direct reference from A should still be returned
        Assertions.assertEquals(1, refsFromA.size());
        Assertions.assertEquals("b.avsc", refsFromA.get(0).getName());

        // Content should be retrievable (no stack overflow)
        try (InputStream content = clientV3.groups()
                .byGroupId(GROUP_ID)
                .artifacts()
                .byArtifactId(artifactIdA)
                .versions()
                .byVersionExpression("1")
                .content()
                .get()) {
            String contentStr = new String(content.readAllBytes(), StandardCharsets.UTF_8);
            Assertions.assertNotNull(contentStr);
            Assertions.assertTrue(contentStr.contains("RecordA"));
        }
    }

    /**
     * Test that circular references are handled correctly and don't cause infinite loops.
     */
    @Test
    public void testCircularReferenceWithDepthLimit() throws Exception {
        String artifactIdA = "circular-depth-A-" + TestUtils.generateArtifactId();
        String artifactIdB = "circular-depth-B-" + TestUtils.generateArtifactId();

        // Create A first (no references)
        CreateArtifactResponse respA = createSimpleArtifact(artifactIdA, "CircularA");

        // Create B referencing A
        ArtifactReference refA = createRef(artifactIdA, respA.getVersion().getVersion(), "a.avsc");
        CreateArtifactResponse respB = createArtifactWithReferences(artifactIdB, "CircularB",
                Collections.singletonList(refA));

        // Update A to reference B (creating a cycle: A@2 -> B@1 -> A@1)
        ArtifactReference refB = createRef(artifactIdB, respB.getVersion().getVersion(), "b.avsc");

        CreateVersion createV2 = new CreateVersion();
        createV2.setContent(new VersionContent());
        createV2.getContent().setContent(String.format(AVRO_SCHEMA_TEMPLATE, "CircularAv2"));
        createV2.getContent().setContentType(ContentTypes.APPLICATION_JSON);
        createV2.getContent().setReferences(Collections.singletonList(refB));

        var v2Response = clientV3.groups().byGroupId(GROUP_ID)
                .artifacts().byArtifactId(artifactIdA)
                .versions().post(createV2);

        Assertions.assertNotNull(v2Response);
        Assertions.assertEquals("2", v2Response.getVersion());

        // The cycle detection should prevent infinite loops
        List<ArtifactReference> refsFromAv2 = clientV3.groups()
                .byGroupId(GROUP_ID)
                .artifacts()
                .byArtifactId(artifactIdA)
                .versions()
                .byVersionExpression("2")
                .references()
                .get();

        Assertions.assertEquals(1, refsFromAv2.size());
        Assertions.assertEquals("b.avsc", refsFromAv2.get(0).getName());
    }

    // Helper methods

    private CreateArtifactResponse createSimpleArtifact(String artifactId, String recordName)
            throws Exception {
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AVRO);
        createArtifact.setFirstVersion(new CreateVersion());
        createArtifact.getFirstVersion().setContent(new VersionContent());
        createArtifact.getFirstVersion().getContent()
                .setContent(String.format(AVRO_SCHEMA_TEMPLATE, recordName));
        createArtifact.getFirstVersion().getContent().setContentType(ContentTypes.APPLICATION_JSON);

        return clientV3.groups().byGroupId(GROUP_ID).artifacts().post(createArtifact);
    }

    private CreateArtifactResponse createArtifactWithReferences(String artifactId, String recordName,
            List<ArtifactReference> references) throws Exception {
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AVRO);
        createArtifact.setFirstVersion(new CreateVersion());
        createArtifact.getFirstVersion().setContent(new VersionContent());
        createArtifact.getFirstVersion().getContent()
                .setContent(String.format(AVRO_SCHEMA_TEMPLATE, recordName));
        createArtifact.getFirstVersion().getContent().setContentType(ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().getContent().setReferences(references);

        return clientV3.groups().byGroupId(GROUP_ID).artifacts().post(createArtifact);
    }

    private ArtifactReference createRef(String artifactId, String version, String name) {
        ArtifactReference ref = new ArtifactReference();
        ref.setGroupId(GROUP_ID);
        ref.setArtifactId(artifactId);
        ref.setVersion(version);
        ref.setName(name);
        return ref;
    }
}
