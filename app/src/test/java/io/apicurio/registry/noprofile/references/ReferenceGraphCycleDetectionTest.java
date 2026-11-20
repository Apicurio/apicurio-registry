package io.apicurio.registry.noprofile.references;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.ReferenceGraph;
import io.apicurio.registry.rest.client.models.ReferenceGraphDirection;
import io.apicurio.registry.rest.client.models.ReferenceGraphNode;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Covers #9069: the reference-graph endpoint must key cycle detection by artifact *version*, not just
 * artifact. {@code A@2 -> B@1 -> A@1} is a valid chain - A@1 and A@2 are different nodes, and A@1 has no
 * path back to A@2 - so it must not be reported as a cycle, even though both nodes share an artifactId.
 */
@QuarkusTest
public class ReferenceGraphCycleDetectionTest extends AbstractResourceTestBase {

    private static final String AVRO_SCHEMA_TEMPLATE = """
            {
                "type": "record",
                "name": "%s",
                "namespace": "com.example",
                "fields": [
                    {"name": "id", "type": "string"}
                ]
            }
            """;

    private ArtifactReference refTo(String groupId, String artifactId, String version, String name) {
        ArtifactReference ref = new ArtifactReference();
        ref.setGroupId(groupId);
        ref.setArtifactId(artifactId);
        ref.setVersion(version);
        ref.setName(name);
        return ref;
    }

    @Test
    public void crossVersionChainToTheSameArtifactIsNotReportedAsACycle() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // A@1: no references.
        String artifactA = TestUtils.generateArtifactId();
        CreateArtifact createA = new CreateArtifact();
        createA.setArtifactId(artifactA);
        createA.setArtifactType(ArtifactType.AVRO);
        CreateVersion versionA = new CreateVersion();
        VersionContent contentA = new VersionContent();
        contentA.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordA"));
        contentA.setContentType(ContentTypes.APPLICATION_JSON);
        versionA.setContent(contentA);
        createA.setFirstVersion(versionA);
        var responseA = clientV3.groups().byGroupId(groupId).artifacts().post(createA);
        String versionAStr = responseA.getVersion().getVersion();

        // B@1: references A@1.
        String artifactB = TestUtils.generateArtifactId();
        CreateArtifact createB = new CreateArtifact();
        createB.setArtifactId(artifactB);
        createB.setArtifactType(ArtifactType.AVRO);
        CreateVersion versionB = new CreateVersion();
        VersionContent contentB = new VersionContent();
        contentB.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordB"));
        contentB.setContentType(ContentTypes.APPLICATION_JSON);
        List<ArtifactReference> refsB = new ArrayList<>();
        refsB.add(refTo(groupId, artifactA, versionAStr, "a.avsc"));
        contentB.setReferences(refsB);
        versionB.setContent(contentB);
        createB.setFirstVersion(versionB);
        var responseB = clientV3.groups().byGroupId(groupId).artifacts().post(createB);
        String versionBStr = responseB.getVersion().getVersion();

        // A@2: references B@1. Chain is A@2 -> B@1 -> A@1 - not a cycle, since A@1 has no
        // reference back to A@2.
        CreateVersion versionA2 = new CreateVersion();
        VersionContent contentA2 = new VersionContent();
        contentA2.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordAv2"));
        contentA2.setContentType(ContentTypes.APPLICATION_JSON);
        List<ArtifactReference> refsA2 = new ArrayList<>();
        refsA2.add(refTo(groupId, artifactB, versionBStr, "b.avsc"));
        contentA2.setReferences(refsA2);
        versionA2.setContent(contentA2);
        var responseA2 = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactA).versions()
                .post(versionA2);
        String versionA2Str = responseA2.getVersion();

        ReferenceGraph graph = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactA)
                .versions().byVersionExpression(versionA2Str).references().graph()
                .get(config -> config.queryParameters.direction = ReferenceGraphDirection.OUTBOUND);

        Assertions.assertNotNull(graph);
        Assertions.assertFalse(graph.getMetadata().getHasCycles(),
                "A@2 -> B@1 -> A@1 is a valid cross-version chain, not a cycle");
        Assertions.assertTrue(graph.getNodes().stream().noneMatch(n -> Boolean.TRUE.equals(n.getIsCycleNode())),
                "no node should be marked as a cycle node");

        // A traversal that stopped early (e.g. treating A@1 as already-visited-via-A@2 and cutting the
        // walk short there) would also report hasCycles=false. Pin the full shape of the graph instead:
        // exactly the three nodes and two edges this chain actually has.
        String nodeA2 = groupId + ":" + artifactA + ":" + versionA2Str;
        String nodeB1 = groupId + ":" + artifactB + ":" + versionBStr;
        String nodeA1 = groupId + ":" + artifactA + ":" + versionAStr;
        Set<String> nodeIds = graph.getNodes().stream().map(ReferenceGraphNode::getId)
                .collect(Collectors.toSet());
        Assertions.assertEquals(Set.of(nodeA2, nodeB1, nodeA1), nodeIds);
        Assertions.assertEquals(2, graph.getEdges().size());
        Assertions.assertTrue(graph.getEdges().stream()
                .anyMatch(e -> e.getSourceNodeId().equals(nodeA2) && e.getTargetNodeId().equals(nodeB1)));
        Assertions.assertTrue(graph.getEdges().stream()
                .anyMatch(e -> e.getSourceNodeId().equals(nodeB1) && e.getTargetNodeId().equals(nodeA1)));
    }

    @Test
    public void crossVersionChainIsNotACycleFromTheInboundSide() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Same chain as above (A@2 -> B@1 -> A@1), this time queried from A@1 with
        // direction=INBOUND: A@1 <- B@1 <- A@2. The old artifact-level key flagged this one too,
        // since it never distinguished which side of the edge it was walking.
        String artifactA = TestUtils.generateArtifactId();
        CreateArtifact createA = new CreateArtifact();
        createA.setArtifactId(artifactA);
        createA.setArtifactType(ArtifactType.AVRO);
        CreateVersion versionA = new CreateVersion();
        VersionContent contentA = new VersionContent();
        contentA.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordA"));
        contentA.setContentType(ContentTypes.APPLICATION_JSON);
        versionA.setContent(contentA);
        createA.setFirstVersion(versionA);
        var responseA = clientV3.groups().byGroupId(groupId).artifacts().post(createA);
        String versionAStr = responseA.getVersion().getVersion();

        String artifactB = TestUtils.generateArtifactId();
        CreateArtifact createB = new CreateArtifact();
        createB.setArtifactId(artifactB);
        createB.setArtifactType(ArtifactType.AVRO);
        CreateVersion versionB = new CreateVersion();
        VersionContent contentB = new VersionContent();
        contentB.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordB"));
        contentB.setContentType(ContentTypes.APPLICATION_JSON);
        List<ArtifactReference> refsB = new ArrayList<>();
        refsB.add(refTo(groupId, artifactA, versionAStr, "a.avsc"));
        contentB.setReferences(refsB);
        versionB.setContent(contentB);
        createB.setFirstVersion(versionB);
        var responseB = clientV3.groups().byGroupId(groupId).artifacts().post(createB);
        String versionBStr = responseB.getVersion().getVersion();

        CreateVersion versionA2 = new CreateVersion();
        VersionContent contentA2 = new VersionContent();
        contentA2.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordAv2"));
        contentA2.setContentType(ContentTypes.APPLICATION_JSON);
        List<ArtifactReference> refsA2 = new ArrayList<>();
        refsA2.add(refTo(groupId, artifactB, versionBStr, "b.avsc"));
        contentA2.setReferences(refsA2);
        versionA2.setContent(contentA2);
        var responseA2 = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactA).versions()
                .post(versionA2);
        String versionA2Str = responseA2.getVersion();

        ReferenceGraph graph = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactA)
                .versions().byVersionExpression(versionAStr).references().graph()
                .get(config -> config.queryParameters.direction = ReferenceGraphDirection.INBOUND);

        Assertions.assertNotNull(graph);
        Assertions.assertFalse(graph.getMetadata().getHasCycles(),
                "A@1 <- B@1 <- A@2 is a valid cross-version chain, not a cycle, from the inbound side too");
        Assertions.assertTrue(graph.getNodes().stream().noneMatch(n -> Boolean.TRUE.equals(n.getIsCycleNode())));

        String nodeA1 = groupId + ":" + artifactA + ":" + versionAStr;
        String nodeB1 = groupId + ":" + artifactB + ":" + versionBStr;
        String nodeA2 = groupId + ":" + artifactA + ":" + versionA2Str;
        Set<String> nodeIds = graph.getNodes().stream().map(ReferenceGraphNode::getId)
                .collect(Collectors.toSet());
        Assertions.assertEquals(Set.of(nodeA1, nodeB1, nodeA2), nodeIds);
    }

    @Test
    public void trueVersionLevelCycleIsStillReported() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // A self-referencing version: A@v1 references A@v1 - an explicit client-chosen version lets
        // the reference target its own not-yet-created version. This is a true version-level cycle
        // (not merely a same-artifact, different-version chain), since the path returns to the very
        // same node it started from.
        String artifactA = TestUtils.generateArtifactId();
        CreateArtifact createA = new CreateArtifact();
        createA.setArtifactId(artifactA);
        createA.setArtifactType(ArtifactType.AVRO);
        CreateVersion versionA = new CreateVersion();
        versionA.setVersion("v1");
        VersionContent contentA = new VersionContent();
        contentA.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordA"));
        contentA.setContentType(ContentTypes.APPLICATION_JSON);
        List<ArtifactReference> selfRefs = new ArrayList<>();
        selfRefs.add(refTo(groupId, artifactA, "v1", "self.avsc"));
        contentA.setReferences(selfRefs);
        versionA.setContent(contentA);
        createA.setFirstVersion(versionA);
        clientV3.groups().byGroupId(groupId).artifacts().post(createA);

        ReferenceGraph graph = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactA)
                .versions().byVersionExpression("v1").references().graph()
                .get(config -> config.queryParameters.direction = ReferenceGraphDirection.OUTBOUND);

        Assertions.assertNotNull(graph);
        Assertions.assertTrue(graph.getMetadata().getHasCycles(),
                "A@v1 referencing itself is a true version-level cycle");
        Assertions.assertTrue(graph.getNodes().stream().anyMatch(n -> Boolean.TRUE.equals(n.getIsCycleNode())),
                "the self-referencing node should be marked as a cycle node");
    }

    @Test
    public void twoHopVersionLevelCycleIsReportedBothDirections() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // A@1 -> B@1 -> A@1: a genuine two-hop cycle, as opposed to the self-reference above, which the
        // old artifact-level code also caught and so didn't exercise the version-level path set or its
        // backtracking. B doesn't exist yet when A@1 is created - references to a not-yet-existing
        // version are accepted (the REFS_EXIST rule isn't enabled here) - then B@1 closes the cycle back
        // to A@1.
        String artifactA = TestUtils.generateArtifactId();
        String artifactB = TestUtils.generateArtifactId();

        CreateArtifact createA = new CreateArtifact();
        createA.setArtifactId(artifactA);
        createA.setArtifactType(ArtifactType.AVRO);
        CreateVersion versionA = new CreateVersion();
        versionA.setVersion("1");
        VersionContent contentA = new VersionContent();
        contentA.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordA"));
        contentA.setContentType(ContentTypes.APPLICATION_JSON);
        List<ArtifactReference> refsA = new ArrayList<>();
        refsA.add(refTo(groupId, artifactB, "1", "b.avsc"));
        contentA.setReferences(refsA);
        versionA.setContent(contentA);
        createA.setFirstVersion(versionA);
        clientV3.groups().byGroupId(groupId).artifacts().post(createA);

        CreateArtifact createB = new CreateArtifact();
        createB.setArtifactId(artifactB);
        createB.setArtifactType(ArtifactType.AVRO);
        CreateVersion versionB = new CreateVersion();
        versionB.setVersion("1");
        VersionContent contentB = new VersionContent();
        contentB.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordB"));
        contentB.setContentType(ContentTypes.APPLICATION_JSON);
        List<ArtifactReference> refsB = new ArrayList<>();
        refsB.add(refTo(groupId, artifactA, "1", "a.avsc"));
        contentB.setReferences(refsB);
        versionB.setContent(contentB);
        createB.setFirstVersion(versionB);
        clientV3.groups().byGroupId(groupId).artifacts().post(createB);

        String nodeA1 = groupId + ":" + artifactA + ":1";
        String nodeB1 = groupId + ":" + artifactB + ":1";

        ReferenceGraph outbound = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactA)
                .versions().byVersionExpression("1").references().graph()
                .get(config -> config.queryParameters.direction = ReferenceGraphDirection.OUTBOUND);
        Assertions.assertTrue(outbound.getMetadata().getHasCycles());
        Set<String> outboundCycleNodes = outbound.getNodes().stream()
                .filter(n -> Boolean.TRUE.equals(n.getIsCycleNode())).map(ReferenceGraphNode::getId)
                .collect(Collectors.toSet());
        Assertions.assertEquals(Set.of(nodeA1, nodeB1), outboundCycleNodes);

        ReferenceGraph inbound = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactA)
                .versions().byVersionExpression("1").references().graph()
                .get(config -> config.queryParameters.direction = ReferenceGraphDirection.INBOUND);
        Assertions.assertTrue(inbound.getMetadata().getHasCycles());
        Set<String> inboundCycleNodes = inbound.getNodes().stream()
                .filter(n -> Boolean.TRUE.equals(n.getIsCycleNode())).map(ReferenceGraphNode::getId)
                .collect(Collectors.toSet());
        Assertions.assertEquals(Set.of(nodeA1, nodeB1), inboundCycleNodes);
    }
}
