package io.apicurio.registry.noprofile.integrity;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for circular reference detection in the Integrity rule.
 */
@QuarkusTest
public class CircularReferenceIntegrityRuleTest extends AbstractResourceTestBase {

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

    @AfterEach
    void cleanup() {
        // Clean up global rules after each test
        try {
            clientV3.admin().rules().byRuleType(RuleType.INTEGRITY.name()).delete();
        } catch (Exception e) {
            // Ignore if rule doesn't exist
        }
    }

    @Test
    public void testDirectCircularReferenceBlocked() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create a global INTEGRITY rule with NO_CIRCULAR_REFERENCES
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.INTEGRITY);
        createRule.setConfig(IntegrityLevel.NO_CIRCULAR_REFERENCES.name());
        clientV3.admin().rules().post(createRule);

        // Create artifact A first (no references)
        String artifactA = TestUtils.generateArtifactId();
        CreateArtifact createArtifactA = new CreateArtifact();
        createArtifactA.setArtifactId(artifactA);
        createArtifactA.setArtifactType(ArtifactType.AVRO);
        CreateVersion versionA = new CreateVersion();
        VersionContent contentA = new VersionContent();
        contentA.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordA"));
        contentA.setContentType(ContentTypes.APPLICATION_JSON);
        versionA.setContent(contentA);
        createArtifactA.setFirstVersion(versionA);

        var responseA = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifactA);
        String versionAStr = responseA.getVersion().getVersion();

        // Create artifact B with reference to A
        String artifactB = TestUtils.generateArtifactId();
        CreateArtifact createArtifactB = new CreateArtifact();
        createArtifactB.setArtifactId(artifactB);
        createArtifactB.setArtifactType(ArtifactType.AVRO);
        CreateVersion versionB = new CreateVersion();
        VersionContent contentB = new VersionContent();
        contentB.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordB"));
        contentB.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refsB = new ArrayList<>();
        ArtifactReference refA = new ArtifactReference();
        refA.setGroupId(groupId);
        refA.setArtifactId(artifactA);
        refA.setVersion(versionAStr);
        refA.setName("a.avsc");
        refsB.add(refA);
        contentB.setReferences(refsB);

        versionB.setContent(contentB);
        createArtifactB.setFirstVersion(versionB);

        var responseB = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifactB);
        String versionBStr = responseB.getVersion().getVersion();

        // Now try to create a new version of A that references B (creating a cycle: A -> B -> A)
        CreateVersion newVersionA = new CreateVersion();
        VersionContent newContentA = new VersionContent();
        newContentA.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordAv2"));
        newContentA.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refsA2 = new ArrayList<>();
        ArtifactReference refB = new ArtifactReference();
        refB.setGroupId(groupId);
        refB.setArtifactId(artifactB);
        refB.setVersion(versionBStr);
        refB.setName("b.avsc");
        refsA2.add(refB);
        newContentA.setReferences(refsA2);

        newVersionA.setContent(newContentA);

        // This should throw a RuleViolationException
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactA).versions()
                    .post(newVersionA);
        }, "Should have thrown exception due to circular reference");
    }

    @Test
    public void testSelfReferenceBlocked() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create a global INTEGRITY rule with NO_CIRCULAR_REFERENCES
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.INTEGRITY);
        createRule.setConfig(IntegrityLevel.NO_CIRCULAR_REFERENCES.name());
        clientV3.admin().rules().post(createRule);

        // Create artifact A first (no references)
        String artifactA = TestUtils.generateArtifactId();
        CreateArtifact createArtifactA = new CreateArtifact();
        createArtifactA.setArtifactId(artifactA);
        createArtifactA.setArtifactType(ArtifactType.AVRO);
        CreateVersion versionA = new CreateVersion();
        VersionContent contentA = new VersionContent();
        contentA.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordA"));
        contentA.setContentType(ContentTypes.APPLICATION_JSON);
        versionA.setContent(contentA);
        createArtifactA.setFirstVersion(versionA);

        var responseA = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifactA);
        String versionAStr = responseA.getVersion().getVersion();

        // Now try to create a new version of A that references itself
        CreateVersion newVersionA = new CreateVersion();
        VersionContent newContentA = new VersionContent();
        newContentA.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordAv2"));
        newContentA.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> selfRefs = new ArrayList<>();
        ArtifactReference selfRef = new ArtifactReference();
        selfRef.setGroupId(groupId);
        selfRef.setArtifactId(artifactA);
        selfRef.setVersion(versionAStr);
        selfRef.setName("self.avsc");
        selfRefs.add(selfRef);
        newContentA.setReferences(selfRefs);

        newVersionA.setContent(newContentA);

        // This should throw a RuleViolationException
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactA).versions()
                    .post(newVersionA);
        }, "Should have thrown exception due to self-reference");
    }

    @Test
    public void testNonCircularReferencesAllowed() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create a global INTEGRITY rule with NO_CIRCULAR_REFERENCES
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.INTEGRITY);
        createRule.setConfig(IntegrityLevel.NO_CIRCULAR_REFERENCES.name());
        clientV3.admin().rules().post(createRule);

        // Create artifact A first (no references)
        String artifactA = TestUtils.generateArtifactId();
        CreateArtifact createArtifactA = new CreateArtifact();
        createArtifactA.setArtifactId(artifactA);
        createArtifactA.setArtifactType(ArtifactType.AVRO);
        CreateVersion versionA = new CreateVersion();
        VersionContent contentA = new VersionContent();
        contentA.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordA"));
        contentA.setContentType(ContentTypes.APPLICATION_JSON);
        versionA.setContent(contentA);
        createArtifactA.setFirstVersion(versionA);

        var responseA = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifactA);
        String versionAStr = responseA.getVersion().getVersion();

        // Create artifact B with reference to A (this should work - it's not circular)
        String artifactB = TestUtils.generateArtifactId();
        CreateArtifact createArtifactB = new CreateArtifact();
        createArtifactB.setArtifactId(artifactB);
        createArtifactB.setArtifactType(ArtifactType.AVRO);
        CreateVersion versionB = new CreateVersion();
        VersionContent contentB = new VersionContent();
        contentB.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordB"));
        contentB.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refsB = new ArrayList<>();
        ArtifactReference refA = new ArtifactReference();
        refA.setGroupId(groupId);
        refA.setArtifactId(artifactA);
        refA.setVersion(versionAStr);
        refA.setName("a.avsc");
        refsB.add(refA);
        contentB.setReferences(refsB);

        versionB.setContent(contentB);
        createArtifactB.setFirstVersion(versionB);

        // This should work without throwing
        var responseB = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifactB);
        Assertions.assertNotNull(responseB);
        Assertions.assertNotNull(responseB.getVersion());

        // Create artifact C with reference to B (this should also work - it's a chain, not a cycle)
        String artifactC = TestUtils.generateArtifactId();
        CreateArtifact createArtifactC = new CreateArtifact();
        createArtifactC.setArtifactId(artifactC);
        createArtifactC.setArtifactType(ArtifactType.AVRO);
        CreateVersion versionC = new CreateVersion();
        VersionContent contentC = new VersionContent();
        contentC.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordC"));
        contentC.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refsC = new ArrayList<>();
        ArtifactReference refB = new ArtifactReference();
        refB.setGroupId(groupId);
        refB.setArtifactId(artifactB);
        refB.setVersion(responseB.getVersion().getVersion());
        refB.setName("b.avsc");
        refsC.add(refB);
        contentC.setReferences(refsC);

        versionC.setContent(contentC);
        createArtifactC.setFirstVersion(versionC);

        // This should work without throwing
        var responseC = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifactC);
        Assertions.assertNotNull(responseC);
        Assertions.assertNotNull(responseC.getVersion());
    }

    @Test
    public void testCircularReferencesAllowedWithoutRule() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // No integrity rule is set, so circular references should be allowed

        // Create artifact A first (no references)
        String artifactA = TestUtils.generateArtifactId();
        CreateArtifact createArtifactA = new CreateArtifact();
        createArtifactA.setArtifactId(artifactA);
        createArtifactA.setArtifactType(ArtifactType.AVRO);
        CreateVersion versionA = new CreateVersion();
        VersionContent contentA = new VersionContent();
        contentA.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordA"));
        contentA.setContentType(ContentTypes.APPLICATION_JSON);
        versionA.setContent(contentA);
        createArtifactA.setFirstVersion(versionA);

        var responseA = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifactA);
        String versionAStr = responseA.getVersion().getVersion();

        // Create artifact B with reference to A
        String artifactB = TestUtils.generateArtifactId();
        CreateArtifact createArtifactB = new CreateArtifact();
        createArtifactB.setArtifactId(artifactB);
        createArtifactB.setArtifactType(ArtifactType.AVRO);
        CreateVersion versionB = new CreateVersion();
        VersionContent contentB = new VersionContent();
        contentB.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordB"));
        contentB.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refsB = new ArrayList<>();
        ArtifactReference refA = new ArtifactReference();
        refA.setGroupId(groupId);
        refA.setArtifactId(artifactA);
        refA.setVersion(versionAStr);
        refA.setName("a.avsc");
        refsB.add(refA);
        contentB.setReferences(refsB);

        versionB.setContent(contentB);
        createArtifactB.setFirstVersion(versionB);

        var responseB = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifactB);
        String versionBStr = responseB.getVersion().getVersion();

        // Now try to create a new version of A that references B (creating a cycle: A -> B -> A)
        // This should be allowed since no integrity rule is set
        CreateVersion newVersionA = new CreateVersion();
        VersionContent newContentA = new VersionContent();
        newContentA.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordAv2"));
        newContentA.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refsA2 = new ArrayList<>();
        ArtifactReference refB = new ArtifactReference();
        refB.setGroupId(groupId);
        refB.setArtifactId(artifactB);
        refB.setVersion(versionBStr);
        refB.setName("b.avsc");
        refsA2.add(refB);
        newContentA.setReferences(refsA2);

        newVersionA.setContent(newContentA);

        // This should succeed since no rule is blocking it
        var newResponseA = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactA).versions()
                .post(newVersionA);
        Assertions.assertNotNull(newResponseA);
        Assertions.assertEquals("2", newResponseA.getVersion());
    }

    @Test
    public void testFullIntegrityLevelBlocksCircularReferences() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create a global INTEGRITY rule with FULL (which includes all checks)
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.INTEGRITY);
        createRule.setConfig(IntegrityLevel.FULL.name());
        clientV3.admin().rules().post(createRule);

        // Create artifact A first (no references)
        String artifactA = TestUtils.generateArtifactId();
        CreateArtifact createArtifactA = new CreateArtifact();
        createArtifactA.setArtifactId(artifactA);
        createArtifactA.setArtifactType(ArtifactType.AVRO);
        CreateVersion versionA = new CreateVersion();
        VersionContent contentA = new VersionContent();
        contentA.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordA"));
        contentA.setContentType(ContentTypes.APPLICATION_JSON);
        versionA.setContent(contentA);
        createArtifactA.setFirstVersion(versionA);

        var responseA = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifactA);
        String versionAStr = responseA.getVersion().getVersion();

        // Create artifact B with reference to A
        String artifactB = TestUtils.generateArtifactId();
        CreateArtifact createArtifactB = new CreateArtifact();
        createArtifactB.setArtifactId(artifactB);
        createArtifactB.setArtifactType(ArtifactType.AVRO);
        CreateVersion versionB = new CreateVersion();
        VersionContent contentB = new VersionContent();
        contentB.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordB"));
        contentB.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refsB = new ArrayList<>();
        ArtifactReference refA = new ArtifactReference();
        refA.setGroupId(groupId);
        refA.setArtifactId(artifactA);
        refA.setVersion(versionAStr);
        refA.setName("a.avsc");
        refsB.add(refA);
        contentB.setReferences(refsB);

        versionB.setContent(contentB);
        createArtifactB.setFirstVersion(versionB);

        var responseB = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifactB);
        String versionBStr = responseB.getVersion().getVersion();

        // Now try to create a new version of A that references B (creating a cycle: A -> B -> A)
        CreateVersion newVersionA = new CreateVersion();
        VersionContent newContentA = new VersionContent();
        newContentA.setContent(String.format(AVRO_SCHEMA_TEMPLATE, "RecordAv2"));
        newContentA.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refsA2 = new ArrayList<>();
        ArtifactReference refB = new ArtifactReference();
        refB.setGroupId(groupId);
        refB.setArtifactId(artifactB);
        refB.setVersion(versionBStr);
        refB.setName("b.avsc");
        refsA2.add(refB);
        newContentA.setReferences(refsA2);

        newVersionA.setContent(newContentA);

        // This should throw a RuleViolationException because FULL includes NO_CIRCULAR_REFERENCES
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactA).versions()
                    .post(newVersionA);
        }, "Should have thrown exception due to circular reference with FULL integrity level");
    }
}
