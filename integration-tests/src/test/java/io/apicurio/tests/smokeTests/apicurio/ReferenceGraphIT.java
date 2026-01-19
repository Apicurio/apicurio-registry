package io.apicurio.tests.smokeTests.apicurio;

import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.ReferenceGraph;
import io.apicurio.registry.rest.client.models.ReferenceGraphDirection;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the Reference Graph API endpoint.
 */
@Tag(Constants.SMOKE)
@QuarkusIntegrationTest
class ReferenceGraphIT extends ApicurioRegistryBaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceGraphIT.class);

    private static final String AVRO_SCHEMA_TEMPLATE = """
            {
                "type": "record",
                "name": "%s",
                "namespace": "com.example",
                "fields": [
                    {"name": "id", "type": "string"},
                    {"name": "name", "type": "string"}
                ]
            }
            """;

    /**
     * Test that the graph endpoint returns an empty graph when there are no references.
     */
    @Test
    void testEmptyReferenceGraph() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create an artifact without references
        String content = String.format(AVRO_SCHEMA_TEMPLATE, "TestRecord");
        CreateArtifactResponse response = createArtifact(groupId, artifactId, ArtifactType.AVRO, content,
                ContentTypes.APPLICATION_JSON, null, null);

        assertNotNull(response);
        String version = response.getVersion().getVersion();

        // Get the reference graph
        ReferenceGraph graph = registryClient.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression(version).references().graph()
                .get();

        assertNotNull(graph);
        assertNotNull(graph.getRoot());
        assertNotNull(graph.getNodes());
        assertNotNull(graph.getEdges());
        assertNotNull(graph.getMetadata());

        // Should have exactly 1 node (the root) and 0 edges
        assertEquals(1, graph.getNodes().size());
        assertEquals(0, graph.getEdges().size());
        assertEquals(1, graph.getMetadata().getTotalNodes());
        assertEquals(0, graph.getMetadata().getTotalEdges());
        assertFalse(graph.getMetadata().getHasCycles());

        // Verify root node
        assertTrue(graph.getRoot().getIsRoot());
        assertEquals(groupId, graph.getRoot().getGroupId());
        assertEquals(artifactId, graph.getRoot().getArtifactId());
        assertEquals(version, graph.getRoot().getVersion());
    }

    /**
     * Test the graph endpoint with outbound references.
     */
    @Test
    void testOutboundReferenceGraph() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create the referenced artifact first (no references)
        String referencedArtifactId = TestUtils.generateArtifactId();
        String referencedContent = String.format(AVRO_SCHEMA_TEMPLATE, "ReferencedRecord");
        CreateArtifactResponse referencedResponse = createArtifact(groupId, referencedArtifactId,
                ArtifactType.AVRO, referencedContent, ContentTypes.APPLICATION_JSON, null, null);
        String referencedVersion = referencedResponse.getVersion().getVersion();

        // Create the main artifact with a reference to the first one
        String mainArtifactId = TestUtils.generateArtifactId();
        String mainContent = String.format(AVRO_SCHEMA_TEMPLATE, "MainRecord");

        List<ArtifactReference> references = new ArrayList<>();
        ArtifactReference ref = new ArtifactReference();
        ref.setGroupId(groupId);
        ref.setArtifactId(referencedArtifactId);
        ref.setVersion(referencedVersion);
        ref.setName("referenced.avsc");
        references.add(ref);

        CreateArtifactResponse mainResponse = createArtifact(groupId, mainArtifactId, ArtifactType.AVRO,
                mainContent, ContentTypes.APPLICATION_JSON, null, createArtifact -> {
                    createArtifact.getFirstVersion().getContent().setReferences(references);
                });
        String mainVersion = mainResponse.getVersion().getVersion();

        // Get the reference graph with default direction (OUTBOUND)
        ReferenceGraph graph = registryClient.groups().byGroupId(groupId).artifacts()
                .byArtifactId(mainArtifactId).versions().byVersionExpression(mainVersion).references()
                .graph().get();

        assertNotNull(graph);

        // Should have 2 nodes (root + referenced) and 1 edge
        assertEquals(2, graph.getNodes().size());
        assertEquals(1, graph.getEdges().size());
        assertEquals(2, graph.getMetadata().getTotalNodes());
        assertEquals(1, graph.getMetadata().getTotalEdges());
        assertFalse(graph.getMetadata().getHasCycles());

        // Verify root node
        assertTrue(graph.getRoot().getIsRoot());
        assertEquals(mainArtifactId, graph.getRoot().getArtifactId());

        // Verify edge
        assertEquals(graph.getRoot().getId(), graph.getEdges().get(0).getSourceNodeId());
        assertEquals("referenced.avsc", graph.getEdges().get(0).getName());
    }

    /**
     * Test the graph endpoint with inbound references.
     */
    @Test
    void testInboundReferenceGraph() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create the referenced artifact first (no references)
        String referencedArtifactId = TestUtils.generateArtifactId();
        String referencedContent = String.format(AVRO_SCHEMA_TEMPLATE, "ReferencedRecord");
        CreateArtifactResponse referencedResponse = createArtifact(groupId, referencedArtifactId,
                ArtifactType.AVRO, referencedContent, ContentTypes.APPLICATION_JSON, null, null);
        String referencedVersion = referencedResponse.getVersion().getVersion();

        // Create the main artifact with a reference to the first one
        String mainArtifactId = TestUtils.generateArtifactId();
        String mainContent = String.format(AVRO_SCHEMA_TEMPLATE, "MainRecord");

        List<ArtifactReference> references = new ArrayList<>();
        ArtifactReference ref = new ArtifactReference();
        ref.setGroupId(groupId);
        ref.setArtifactId(referencedArtifactId);
        ref.setVersion(referencedVersion);
        ref.setName("referenced.avsc");
        references.add(ref);

        createArtifact(groupId, mainArtifactId, ArtifactType.AVRO, mainContent,
                ContentTypes.APPLICATION_JSON, null, createArtifact -> {
                    createArtifact.getFirstVersion().getContent().setReferences(references);
                });

        // Get the reference graph for the REFERENCED artifact with INBOUND direction
        ReferenceGraph graph = registryClient.groups().byGroupId(groupId).artifacts()
                .byArtifactId(referencedArtifactId).versions().byVersionExpression(referencedVersion)
                .references().graph().get(config -> {
                    config.queryParameters.direction = ReferenceGraphDirection.INBOUND;
                });

        assertNotNull(graph);

        // Should have 2 nodes (root + referencing artifact) and 1 edge
        assertEquals(2, graph.getNodes().size());
        assertEquals(1, graph.getEdges().size());
        assertFalse(graph.getMetadata().getHasCycles());

        // Verify root node is the referenced artifact
        assertTrue(graph.getRoot().getIsRoot());
        assertEquals(referencedArtifactId, graph.getRoot().getArtifactId());

        // Verify edge points TO the root (inbound)
        assertEquals(graph.getRoot().getId(), graph.getEdges().get(0).getTargetNodeId());
    }

    /**
     * Test the graph endpoint with depth limit.
     */
    @Test
    void testReferenceGraphWithDepthLimit() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create a chain of 3 artifacts: A -> B -> C
        String artifactC = TestUtils.generateArtifactId();
        String contentC = String.format(AVRO_SCHEMA_TEMPLATE, "RecordC");
        CreateArtifactResponse responseC = createArtifact(groupId, artifactC, ArtifactType.AVRO, contentC,
                ContentTypes.APPLICATION_JSON, null, null);
        String versionC = responseC.getVersion().getVersion();

        // Create B with reference to C
        String artifactB = TestUtils.generateArtifactId();
        String contentB = String.format(AVRO_SCHEMA_TEMPLATE, "RecordB");
        List<ArtifactReference> refsB = new ArrayList<>();
        ArtifactReference refC = new ArtifactReference();
        refC.setGroupId(groupId);
        refC.setArtifactId(artifactC);
        refC.setVersion(versionC);
        refC.setName("c.avsc");
        refsB.add(refC);

        CreateArtifactResponse responseB = createArtifact(groupId, artifactB, ArtifactType.AVRO, contentB,
                ContentTypes.APPLICATION_JSON, null, createArtifact -> {
                    createArtifact.getFirstVersion().getContent().setReferences(refsB);
                });
        String versionB = responseB.getVersion().getVersion();

        // Create A with reference to B
        String artifactA = TestUtils.generateArtifactId();
        String contentA = String.format(AVRO_SCHEMA_TEMPLATE, "RecordA");
        List<ArtifactReference> refsA = new ArrayList<>();
        ArtifactReference refB = new ArtifactReference();
        refB.setGroupId(groupId);
        refB.setArtifactId(artifactB);
        refB.setVersion(versionB);
        refB.setName("b.avsc");
        refsA.add(refB);

        CreateArtifactResponse responseA = createArtifact(groupId, artifactA, ArtifactType.AVRO, contentA,
                ContentTypes.APPLICATION_JSON, null, createArtifact -> {
                    createArtifact.getFirstVersion().getContent().setReferences(refsA);
                });
        String versionA = responseA.getVersion().getVersion();

        // Get the graph with depth=1 (should only include A and B)
        ReferenceGraph graphDepth1 = registryClient.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactA).versions().byVersionExpression(versionA).references().graph()
                .get(config -> {
                    config.queryParameters.depth = 1;
                });

        assertNotNull(graphDepth1);
        assertEquals(2, graphDepth1.getNodes().size()); // A and B only
        assertEquals(1, graphDepth1.getEdges().size());

        // Get the graph with depth=2 (should include all: A, B, C)
        ReferenceGraph graphDepth2 = registryClient.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactA).versions().byVersionExpression(versionA).references().graph()
                .get(config -> {
                    config.queryParameters.depth = 2;
                });

        assertNotNull(graphDepth2);
        assertEquals(3, graphDepth2.getNodes().size()); // A, B, and C
        assertEquals(2, graphDepth2.getEdges().size());
    }

    /**
     * Test the graph endpoint with circular references (cycle detection).
     */
    @Test
    void testReferenceGraphWithCycles() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create artifact A first (no references initially)
        String artifactA = TestUtils.generateArtifactId();
        String contentA = String.format(AVRO_SCHEMA_TEMPLATE, "RecordA");
        CreateArtifactResponse responseA = createArtifact(groupId, artifactA, ArtifactType.AVRO, contentA,
                ContentTypes.APPLICATION_JSON, null, null);
        String versionA = responseA.getVersion().getVersion();

        // Create artifact B with reference to A
        String artifactB = TestUtils.generateArtifactId();
        String contentB = String.format(AVRO_SCHEMA_TEMPLATE, "RecordB");
        List<ArtifactReference> refsB = new ArrayList<>();
        ArtifactReference refA = new ArtifactReference();
        refA.setGroupId(groupId);
        refA.setArtifactId(artifactA);
        refA.setVersion(versionA);
        refA.setName("a.avsc");
        refsB.add(refA);

        CreateArtifactResponse responseB = createArtifact(groupId, artifactB, ArtifactType.AVRO, contentB,
                ContentTypes.APPLICATION_JSON, null, createArtifact -> {
                    createArtifact.getFirstVersion().getContent().setReferences(refsB);
                });
        String versionB = responseB.getVersion().getVersion();

        // Create a new version of A with reference back to B (creating a cycle)
        String contentA2 = String.format(AVRO_SCHEMA_TEMPLATE, "RecordAv2");
        List<ArtifactReference> refsA2 = new ArrayList<>();
        ArtifactReference refB = new ArtifactReference();
        refB.setGroupId(groupId);
        refB.setArtifactId(artifactB);
        refB.setVersion(versionB);
        refB.setName("b.avsc");
        refsA2.add(refB);

        CreateVersion createVersion = new CreateVersion();
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(contentA2);
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
        versionContent.setReferences(refsA2);
        createVersion.setContent(versionContent);

        var versionA2Meta = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactA)
                .versions().post(createVersion);
        String versionA2 = versionA2Meta.getVersion();

        // Wait for sync
        Thread.sleep(1000);

        // Get the graph from A v2 - should detect cycle
        ReferenceGraph graph = registryClient.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactA).versions().byVersionExpression(versionA2).references().graph()
                .get(config -> {
                    config.queryParameters.depth = 3;
                });

        assertNotNull(graph);
        assertTrue(graph.getMetadata().getHasCycles(), "Graph should detect circular reference");

        // Verify that cycle nodes are marked
        boolean foundCycleNode = graph.getNodes().stream()
                .anyMatch(node -> Boolean.TRUE.equals(node.getIsCycleNode()));
        assertTrue(foundCycleNode, "At least one node should be marked as cycle node");
    }

    /**
     * Test the graph endpoint returns 404 for non-existent artifact.
     */
    @Test
    void testReferenceGraphNotFound() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "non-existent-artifact";

        // Try to get graph for non-existent artifact
        assertClientError("ArtifactNotFoundException", 404, () -> {
            registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                    .byVersionExpression("1.0").references().graph().get();
        }, errorCodeExtractor);
    }

    /**
     * Test that cross-version references are allowed when the NO_CIRCULAR_REFERENCES integrity rule is
     * enabled. A reference chain like A@2 -> B@1 -> A@1 is NOT a cycle because A@1 has no references, so
     * there's no path back to A@2.
     */
    @Test
    void testCrossVersionReferencesAllowedWithIntegrityRule() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create a global INTEGRITY rule with NO_CIRCULAR_REFERENCES
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.INTEGRITY);
        createRule.setConfig(IntegrityLevel.NO_CIRCULAR_REFERENCES.name());
        registryClient.admin().rules().post(createRule);

        try {
            // Create artifact A first (no references initially)
            String artifactA = TestUtils.generateArtifactId();
            String contentA = String.format(AVRO_SCHEMA_TEMPLATE, "RecordA");
            CreateArtifactResponse responseA = createArtifact(groupId, artifactA, ArtifactType.AVRO, contentA,
                    ContentTypes.APPLICATION_JSON, null, null);
            String versionA = responseA.getVersion().getVersion();

            // Create artifact B with reference to A
            String artifactB = TestUtils.generateArtifactId();
            String contentB = String.format(AVRO_SCHEMA_TEMPLATE, "RecordB");
            List<ArtifactReference> refsB = new ArrayList<>();
            ArtifactReference refA = new ArtifactReference();
            refA.setGroupId(groupId);
            refA.setArtifactId(artifactA);
            refA.setVersion(versionA);
            refA.setName("a.avsc");
            refsB.add(refA);

            CreateArtifactResponse responseB = createArtifact(groupId, artifactB, ArtifactType.AVRO, contentB,
                    ContentTypes.APPLICATION_JSON, null, createArtifact -> {
                        createArtifact.getFirstVersion().getContent().setReferences(refsB);
                    });
            String versionB = responseB.getVersion().getVersion();

            // Create a new version of A with reference back to B
            // This forms: A@2 -> B@1 -> A@1, which is NOT a cycle because A@1 has no references
            String contentA2 = String.format(AVRO_SCHEMA_TEMPLATE, "RecordAv2");
            List<ArtifactReference> refsA2 = new ArrayList<>();
            ArtifactReference refB = new ArtifactReference();
            refB.setGroupId(groupId);
            refB.setArtifactId(artifactB);
            refB.setVersion(versionB);
            refB.setName("b.avsc");
            refsA2.add(refB);

            CreateVersion createVersion = new CreateVersion();
            VersionContent versionContent = new VersionContent();
            versionContent.setContent(contentA2);
            versionContent.setContentType(ContentTypes.APPLICATION_JSON);
            versionContent.setReferences(refsA2);
            createVersion.setContent(versionContent);

            // This should succeed - it's a valid reference chain, not a cycle
            VersionMetaData newVersion = registryClient.groups().byGroupId(groupId).artifacts()
                    .byArtifactId(artifactA).versions().post(createVersion);
            Assertions.assertNotNull(newVersion);
            Assertions.assertEquals("2", newVersion.getVersion());

            LOGGER.info("Cross-version reference chain was correctly allowed");
        } finally {
            // Clean up the global rule
            try {
                registryClient.admin().rules().byRuleType(RuleType.INTEGRITY.name()).delete();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    /**
     * Test that cross-version references to the same artifact are allowed when the NO_CIRCULAR_REFERENCES
     * integrity rule is enabled. A@2 referencing A@1 is valid because A@1 has no references.
     */
    @Test
    void testCrossVersionReferenceToSameArtifactAllowedWithIntegrityRule() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create a global INTEGRITY rule with NO_CIRCULAR_REFERENCES
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.INTEGRITY);
        createRule.setConfig(IntegrityLevel.NO_CIRCULAR_REFERENCES.name());
        registryClient.admin().rules().post(createRule);

        try {
            // Create artifact A first (no references initially)
            String artifactA = TestUtils.generateArtifactId();
            String contentA = String.format(AVRO_SCHEMA_TEMPLATE, "RecordA");
            CreateArtifactResponse responseA = createArtifact(groupId, artifactA, ArtifactType.AVRO, contentA,
                    ContentTypes.APPLICATION_JSON, null, null);
            String versionA = responseA.getVersion().getVersion();

            // Create a new version of A that references the previous version
            // A@2 -> A@1 is valid because A@1 has no references, so there's no cycle
            String contentA2 = String.format(AVRO_SCHEMA_TEMPLATE, "RecordAv2");
            List<ArtifactReference> selfRef = new ArrayList<>();
            ArtifactReference refA = new ArtifactReference();
            refA.setGroupId(groupId);
            refA.setArtifactId(artifactA);
            refA.setVersion(versionA);
            refA.setName("self.avsc");
            selfRef.add(refA);

            CreateVersion createVersion = new CreateVersion();
            VersionContent versionContent = new VersionContent();
            versionContent.setContent(contentA2);
            versionContent.setContentType(ContentTypes.APPLICATION_JSON);
            versionContent.setReferences(selfRef);
            createVersion.setContent(versionContent);

            // This should succeed - referencing an older version of the same artifact is valid
            VersionMetaData newVersion = registryClient.groups().byGroupId(groupId).artifacts()
                    .byArtifactId(artifactA).versions().post(createVersion);
            Assertions.assertNotNull(newVersion);
            Assertions.assertEquals("2", newVersion.getVersion());

            LOGGER.info("Cross-version reference to same artifact was correctly allowed");
        } finally {
            // Clean up the global rule
            try {
                registryClient.admin().rules().byRuleType(RuleType.INTEGRITY.name()).delete();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    /**
     * Test that transitive reference chains are allowed (C@2 -> A@1 -> B@1 -> C@1). This is NOT a cycle
     * because C@1 has no references, so there's no path back to C@2.
     */
    @Test
    void testTransitiveReferenceChainAllowedWithIntegrityRule() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create a global INTEGRITY rule with NO_CIRCULAR_REFERENCES
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.INTEGRITY);
        createRule.setConfig(IntegrityLevel.NO_CIRCULAR_REFERENCES.name());
        registryClient.admin().rules().post(createRule);

        try {
            // Create artifact C first (no references)
            String artifactC = TestUtils.generateArtifactId();
            String contentC = String.format(AVRO_SCHEMA_TEMPLATE, "RecordC");
            CreateArtifactResponse responseC = createArtifact(groupId, artifactC, ArtifactType.AVRO, contentC,
                    ContentTypes.APPLICATION_JSON, null, null);
            String versionC = responseC.getVersion().getVersion();

            // Create artifact B referencing C
            String artifactB = TestUtils.generateArtifactId();
            String contentB = String.format(AVRO_SCHEMA_TEMPLATE, "RecordB");
            List<ArtifactReference> refsB = new ArrayList<>();
            ArtifactReference refC = new ArtifactReference();
            refC.setGroupId(groupId);
            refC.setArtifactId(artifactC);
            refC.setVersion(versionC);
            refC.setName("c.avsc");
            refsB.add(refC);

            CreateArtifactResponse responseB = createArtifact(groupId, artifactB, ArtifactType.AVRO, contentB,
                    ContentTypes.APPLICATION_JSON, null, createArtifact -> {
                        createArtifact.getFirstVersion().getContent().setReferences(refsB);
                    });
            String versionB = responseB.getVersion().getVersion();

            // Create artifact A referencing B
            String artifactA = TestUtils.generateArtifactId();
            String contentA = String.format(AVRO_SCHEMA_TEMPLATE, "RecordA");
            List<ArtifactReference> refsA = new ArrayList<>();
            ArtifactReference refB = new ArtifactReference();
            refB.setGroupId(groupId);
            refB.setArtifactId(artifactB);
            refB.setVersion(versionB);
            refB.setName("b.avsc");
            refsA.add(refB);

            CreateArtifactResponse responseA = createArtifact(groupId, artifactA, ArtifactType.AVRO, contentA,
                    ContentTypes.APPLICATION_JSON, null, createArtifact -> {
                        createArtifact.getFirstVersion().getContent().setReferences(refsA);
                    });
            String versionA = responseA.getVersion().getVersion();

            // Create C@2 referencing A@1
            // This forms: C@2 -> A@1 -> B@1 -> C@1, which is NOT a cycle because C@1 has no references
            String contentC2 = String.format(AVRO_SCHEMA_TEMPLATE, "RecordCv2");
            List<ArtifactReference> refsC2 = new ArrayList<>();
            ArtifactReference refA = new ArtifactReference();
            refA.setGroupId(groupId);
            refA.setArtifactId(artifactA);
            refA.setVersion(versionA);
            refA.setName("a.avsc");
            refsC2.add(refA);

            CreateVersion createVersion = new CreateVersion();
            VersionContent versionContent = new VersionContent();
            versionContent.setContent(contentC2);
            versionContent.setContentType(ContentTypes.APPLICATION_JSON);
            versionContent.setReferences(refsC2);
            createVersion.setContent(versionContent);

            // This should succeed - it's a valid reference chain, not a cycle
            VersionMetaData newVersion = registryClient.groups().byGroupId(groupId).artifacts()
                    .byArtifactId(artifactC).versions().post(createVersion);
            Assertions.assertNotNull(newVersion);
            Assertions.assertEquals("2", newVersion.getVersion());

            LOGGER.info("Transitive reference chain was correctly allowed");
        } finally {
            // Clean up the global rule
            try {
                registryClient.admin().rules().byRuleType(RuleType.INTEGRITY.name()).delete();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }
}
