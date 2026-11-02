package io.apicurio.registry.noprofile.references;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.ReferenceGraph;
import io.apicurio.registry.rest.client.models.ReferenceGraphDirection;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
}
