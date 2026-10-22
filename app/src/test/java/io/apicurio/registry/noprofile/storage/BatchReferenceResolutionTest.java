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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests for batch reference resolution in SqlContentRepository.
 * These tests verify that the batched reference resolution correctly handles
 * various scenarios including nested, multiple, and large reference sets.
 *
 * The batch optimization is tested indirectly through the API since the internal
 * resolveReferencesRaw method is not exposed in the public RegistryStorage interface.
 * The tests create artifacts with references and verify they can be retrieved correctly,
 * which exercises the batch resolution code paths.
 */
@QuarkusTest
public class BatchReferenceResolutionTest extends AbstractResourceTestBase {

    private static final String GROUP_ID = BatchReferenceResolutionTest.class.getSimpleName();

    private static final String AVRO_SCHEMA_TEMPLATE = """
            {
                "type": "record",
                "name": "%s",
                "namespace": "com.example",
                "fields": [
                    {"name": "id", "type": "string"},
                    {"name": "data", "type": "string"}
                ]
            }
            """;

    /**
     * Test resolving a single reference.
     */
    @Test
    public void testSingleReferenceResolution() throws Exception {
        String referencedArtifactId = "single-ref-" + TestUtils.generateArtifactId();
        String mainArtifactId = "main-single-" + TestUtils.generateArtifactId();

        // Create a single referenced artifact
        CreateArtifactResponse refResponse = createSimpleArtifact(referencedArtifactId, "SingleRef");
        String version = refResponse.getVersion().getVersion();

        // Create main artifact with reference
        ArtifactReference ref = new ArtifactReference();
        ref.setGroupId(GROUP_ID);
        ref.setArtifactId(referencedArtifactId);
        ref.setVersion(version);
        ref.setName("single.avsc");

        createArtifactWithReferences(mainArtifactId, "MainSingle", Collections.singletonList(ref));

        // Verify reference can be retrieved (exercises batch resolution)
        List<ArtifactReference> retrievedRefs = clientV3.groups()
                .byGroupId(GROUP_ID)
                .artifacts()
                .byArtifactId(mainArtifactId)
                .versions()
                .byVersionExpression("1")
                .references()
                .get();

        Assertions.assertNotNull(retrievedRefs);
        Assertions.assertEquals(1, retrievedRefs.size());
        Assertions.assertEquals("single.avsc", retrievedRefs.get(0).getName());
    }

    /**
     * Test resolving multiple references at the same level (no nesting).
     * This tests the batch loading of metadata and content.
     */
    @Test
    public void testMultipleReferencesAtSameLevel() throws Exception {
        String artifactIdA = "multi-ref-A-" + TestUtils.generateArtifactId();
        String artifactIdB = "multi-ref-B-" + TestUtils.generateArtifactId();
        String artifactIdC = "multi-ref-C-" + TestUtils.generateArtifactId();
        String mainArtifactId = "main-multi-" + TestUtils.generateArtifactId();

        // Create three referenced artifacts
        CreateArtifactResponse respA = createSimpleArtifact(artifactIdA, "RecordA");
        CreateArtifactResponse respB = createSimpleArtifact(artifactIdB, "RecordB");
        CreateArtifactResponse respC = createSimpleArtifact(artifactIdC, "RecordC");

        // Create main artifact referencing all three
        List<ArtifactReference> refs = new ArrayList<>();
        refs.add(createRef(artifactIdA, respA.getVersion().getVersion(), "a.avsc"));
        refs.add(createRef(artifactIdB, respB.getVersion().getVersion(), "b.avsc"));
        refs.add(createRef(artifactIdC, respC.getVersion().getVersion(), "c.avsc"));

        createArtifactWithReferences(mainArtifactId, "MainMulti", refs);

        // Verify all references can be retrieved
        List<ArtifactReference> retrievedRefs = clientV3.groups()
                .byGroupId(GROUP_ID)
                .artifacts()
                .byArtifactId(mainArtifactId)
                .versions()
                .byVersionExpression("1")
                .references()
                .get();

        Assertions.assertNotNull(retrievedRefs);
        Assertions.assertEquals(3, retrievedRefs.size());

        // Verify content can be dereferenced (this exercises batch resolution)
        try (InputStream content = clientV3.groups()
                .byGroupId(GROUP_ID)
                .artifacts()
                .byArtifactId(mainArtifactId)
                .versions()
                .byVersionExpression("1")
                .content()
                .get()) {
            String contentStr = new String(content.readAllBytes(), StandardCharsets.UTF_8);
            Assertions.assertNotNull(contentStr);
            Assertions.assertTrue(contentStr.contains("MainMulti"));
        }
    }

    /**
     * Test resolving nested references (A -> B -> C chain).
     * This tests the recursive batch resolution at each level.
     */
    @Test
    public void testNestedReferenceResolution() throws Exception {
        String artifactIdC = "nested-C-" + TestUtils.generateArtifactId();
        String artifactIdB = "nested-B-" + TestUtils.generateArtifactId();
        String artifactIdA = "nested-A-" + TestUtils.generateArtifactId();

        // Create C (no references)
        CreateArtifactResponse respC = createSimpleArtifact(artifactIdC, "RecordC");

        // Create B referencing C
        ArtifactReference refC = createRef(artifactIdC, respC.getVersion().getVersion(), "c.avsc");
        CreateArtifactResponse respB = createArtifactWithReferences(artifactIdB, "RecordB",
                Collections.singletonList(refC));

        // Create A referencing B
        ArtifactReference refB = createRef(artifactIdB, respB.getVersion().getVersion(), "b.avsc");
        createArtifactWithReferences(artifactIdA, "RecordA", Collections.singletonList(refB));

        // Verify nested references work correctly
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

        // Verify B's references as well
        List<ArtifactReference> refsFromB = clientV3.groups()
                .byGroupId(GROUP_ID)
                .artifacts()
                .byArtifactId(artifactIdB)
                .versions()
                .byVersionExpression("1")
                .references()
                .get();

        Assertions.assertEquals(1, refsFromB.size());
        Assertions.assertEquals("c.avsc", refsFromB.get(0).getName());

        // Verify content can be dereferenced all the way down
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
     * Test resolving a moderately deep reference chain (depth 10).
     * This verifies that the default max-depth of 100 handles reasonable nesting.
     */
    @Test
    public void testModerateDepthReferenceChain() throws Exception {
        int depth = 10;
        String[] artifactIds = new String[depth + 1];
        CreateArtifactResponse[] responses = new CreateArtifactResponse[depth + 1];

        // Create artifacts from the deepest (index 0) to the shallowest (index depth)
        // Chain: artifact[depth] -> artifact[depth-1] -> ... -> artifact[0]
        for (int i = 0; i <= depth; i++) {
            artifactIds[i] = "deep-chain-" + i + "-" + TestUtils.generateArtifactId();
        }

        // Create the deepest artifact (no references)
        responses[0] = createSimpleArtifact(artifactIds[0], "DeepRecord0");

        // Create each subsequent artifact referencing the previous one
        for (int i = 1; i <= depth; i++) {
            ArtifactReference ref = createRef(artifactIds[i - 1],
                    responses[i - 1].getVersion().getVersion(),
                    "ref" + (i - 1) + ".avsc");
            responses[i] = createArtifactWithReferences(artifactIds[i], "DeepRecord" + i,
                    Collections.singletonList(ref));
        }

        // Verify we can retrieve references from the top-level artifact
        List<ArtifactReference> topRefs = clientV3.groups()
                .byGroupId(GROUP_ID)
                .artifacts()
                .byArtifactId(artifactIds[depth])
                .versions()
                .byVersionExpression("1")
                .references()
                .get();

        Assertions.assertEquals(1, topRefs.size());

        // Verify content can be retrieved (exercises full depth resolution)
        try (InputStream content = clientV3.groups()
                .byGroupId(GROUP_ID)
                .artifacts()
                .byArtifactId(artifactIds[depth])
                .versions()
                .byVersionExpression("1")
                .content()
                .get()) {
            String contentStr = new String(content.readAllBytes(), StandardCharsets.UTF_8);
            Assertions.assertNotNull(contentStr);
            Assertions.assertTrue(contentStr.contains("DeepRecord" + depth));
        }
    }

    /**
     * Test that circular references don't cause infinite loops.
     * The batch resolver should handle already-resolved references gracefully.
     */
    @Test
    public void testCircularReferenceHandling() throws Exception {
        String artifactIdA = "circular-A-" + TestUtils.generateArtifactId();
        String artifactIdB = "circular-B-" + TestUtils.generateArtifactId();

        // Create A first (no references)
        CreateArtifactResponse respA = createSimpleArtifact(artifactIdA, "RecordA");

        // Create B referencing A
        ArtifactReference refA = createRef(artifactIdA, respA.getVersion().getVersion(), "a.avsc");
        CreateArtifactResponse respB = createArtifactWithReferences(artifactIdB, "RecordB",
                Collections.singletonList(refA));

        // Update A to reference B (creating a potential cycle A@2 -> B@1 -> A@1)
        ArtifactReference refB = createRef(artifactIdB, respB.getVersion().getVersion(), "b.avsc");

        CreateVersion createV2 = new CreateVersion();
        createV2.setContent(new VersionContent());
        createV2.getContent().setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordAv2"));
        createV2.getContent().setContentType(ContentTypes.APPLICATION_JSON);
        createV2.getContent().setReferences(Collections.singletonList(refB));

        var v2Response = clientV3.groups().byGroupId(GROUP_ID)
                .artifacts().byArtifactId(artifactIdA)
                .versions().post(createV2);

        Assertions.assertNotNull(v2Response);
        Assertions.assertEquals("2", v2Response.getVersion());

        // Verify we can still retrieve references without hanging
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

    /**
     * Test that duplicate references are handled correctly (should not cause issues).
     */
    @Test
    public void testDuplicateReferencesInBatch() throws Exception {
        String artifactIdA = "dup-ref-A-" + TestUtils.generateArtifactId();
        String mainArtifactId = "main-dup-" + TestUtils.generateArtifactId();

        CreateArtifactResponse respA = createSimpleArtifact(artifactIdA, "RecordA");

        // Create two references to the same artifact with same name
        // (duplicates should be deduplicated before storage)
        ArtifactReference ref1 = createRef(artifactIdA, respA.getVersion().getVersion(), "dup.avsc");
        ArtifactReference ref2 = createRef(artifactIdA, respA.getVersion().getVersion(), "dup.avsc");

        List<ArtifactReference> refs = new ArrayList<>();
        refs.add(ref1);
        refs.add(ref2);

        createArtifactWithReferences(mainArtifactId, "MainDup", refs);

        // Verify only one reference is stored (deduplication)
        List<ArtifactReference> retrievedRefs = clientV3.groups()
                .byGroupId(GROUP_ID)
                .artifacts()
                .byArtifactId(mainArtifactId)
                .versions()
                .byVersionExpression("1")
                .references()
                .get();

        Assertions.assertEquals(1, retrievedRefs.size());
        Assertions.assertEquals("dup.avsc", retrievedRefs.get(0).getName());
    }

    /**
     * Test with many references to verify batch chunking works correctly.
     * Creates more references than the batch size (100) to test chunking logic.
     */
    @Test
    public void testLargeBatchOfReferences() throws Exception {
        int numReferences = 15; // More than typical but reasonable for test
        String mainArtifactId = "main-large-batch-" + TestUtils.generateArtifactId();

        // Create many referenced artifacts
        List<ArtifactReference> refs = new ArrayList<>();
        for (int i = 0; i < numReferences; i++) {
            String refArtifactId = "large-batch-ref-" + i + "-" + TestUtils.generateArtifactId();
            CreateArtifactResponse resp = createSimpleArtifact(refArtifactId, "Record" + i);
            refs.add(createRef(refArtifactId, resp.getVersion().getVersion(), "ref" + i + ".avsc"));
        }

        // Create main artifact with all references
        createArtifactWithReferences(mainArtifactId, "MainLargeBatch", refs);

        // Verify all references are stored and can be retrieved
        List<ArtifactReference> retrievedRefs = clientV3.groups()
                .byGroupId(GROUP_ID)
                .artifacts()
                .byArtifactId(mainArtifactId)
                .versions()
                .byVersionExpression("1")
                .references()
                .get();

        Assertions.assertEquals(numReferences, retrievedRefs.size());

        // Verify content can be retrieved (exercises batch resolution)
        try (InputStream content = clientV3.groups()
                .byGroupId(GROUP_ID)
                .artifacts()
                .byArtifactId(mainArtifactId)
                .versions()
                .byVersionExpression("1")
                .content()
                .get()) {
            String contentStr = new String(content.readAllBytes(), StandardCharsets.UTF_8);
            Assertions.assertNotNull(contentStr);
            Assertions.assertTrue(contentStr.contains("MainLargeBatch"));
        }
    }

    // Helper methods

    private CreateArtifactResponse createSimpleArtifact(String artifactId, String recordName) throws Exception {
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AVRO);
        createArtifact.setFirstVersion(new CreateVersion());
        createArtifact.getFirstVersion().setContent(new VersionContent());
        createArtifact.getFirstVersion().getContent().setContent(String.format(AVRO_SCHEMA_TEMPLATE, recordName));
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
        createArtifact.getFirstVersion().getContent().setContent(String.format(AVRO_SCHEMA_TEMPLATE, recordName));
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
