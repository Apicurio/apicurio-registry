package io.apicurio.registry.noprofile.integrity;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.VersionMetaData;
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
import java.util.stream.Collectors;

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
    public void testCrossVersionReferenceAllowed() throws Exception {
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

        // Create a new version of A that references B
        // This forms a chain: A@2 -> B@1 -> A@1, which is NOT a cycle
        // because A@1 has no references, so there's no path back to A@2
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

        // This should succeed - it's a valid reference chain, not a cycle
        var newResponseA = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactA).versions()
                .post(newVersionA);
        Assertions.assertNotNull(newResponseA);
        Assertions.assertEquals("2", newResponseA.getVersion());
    }

    @Test
    public void testCrossVersionReferenceToSameArtifactAllowed() throws Exception {
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

        // Create a new version of A that references the previous version
        // A@2 -> A@1 is valid because A@1 has no references, so there's no cycle
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

        // This should succeed - referencing an older version of the same artifact is valid
        var newResponseA = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactA).versions()
                .post(newVersionA);
        Assertions.assertNotNull(newResponseA);
        Assertions.assertEquals("2", newResponseA.getVersion());
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
    public void testFullIntegrityLevelAllowsCrossVersionReferences() throws Exception {
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

        // Create a new version of A that references B
        // This forms: A@2 -> B@1 -> A@1, which is NOT a cycle
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

        // This should succeed - it's a valid reference chain, not a cycle
        var newResponseA = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactA).versions()
                .post(newVersionA);
        Assertions.assertNotNull(newResponseA);
        Assertions.assertEquals("2", newResponseA.getVersion());
    }

    // #11309: a cycle closed by a version whose id the storage generates (no explicit version).

    @Test
    public void testGeneratedVersionClosingDirectCycleIsRejected() throws Exception {
        String groupId = TestUtils.generateGroupId();
        enableNoCircularReferences();
        String a = TestUtils.generateArtifactId();
        String b = TestUtils.generateArtifactId();

        // b@1 has no references; a@1 references b@2, which does not exist yet.
        createVersion(groupId, b, null, "RecordB");
        createVersion(groupId, a, null, "RecordA", ref(groupId, b, "2", "b.avsc"));

        // b's next version (generated id 2) referencing a@1 closes a@1 -> b@2 -> a@1.
        RuleViolationProblemDetails error = Assertions.assertThrows(RuleViolationProblemDetails.class,
                () -> createVersion(groupId, b, null, "RecordBv2", ref(groupId, a, "1", "a.avsc")));
        assertCycleClosedBy(error, "a.avsc", groupId + "/" + a + "@1", groupId + "/" + b + "@2");
        Assertions.assertEquals(List.of("1"), versionsOf(groupId, b));
    }

    @Test
    public void testGeneratedVersionClosingLongerCycleIsRejected() throws Exception {
        String groupId = TestUtils.generateGroupId();
        enableNoCircularReferences();
        String a = TestUtils.generateArtifactId();
        String b = TestUtils.generateArtifactId();
        String c = TestUtils.generateArtifactId();

        // a@1 -> b@2 (not yet existing), c@1 -> a@1; b@1 has no references.
        createVersion(groupId, b, null, "RecordB");
        createVersion(groupId, a, null, "RecordA", ref(groupId, b, "2", "b.avsc"));
        createVersion(groupId, c, null, "RecordC", ref(groupId, a, "1", "a.avsc"));

        // b's next version (generated id 2) referencing c@1 closes b@2 -> c@1 -> a@1 -> b@2.
        RuleViolationProblemDetails error = Assertions.assertThrows(RuleViolationProblemDetails.class,
                () -> createVersion(groupId, b, null, "RecordBv2", ref(groupId, c, "1", "c.avsc")));
        assertCycleClosedBy(error, "c.avsc", groupId + "/" + c + "@1", groupId + "/" + b + "@2");
        Assertions.assertEquals(List.of("1"), versionsOf(groupId, b));
    }

    @Test
    public void testExplicitVersionClosingCycleIsStillRejected() throws Exception {
        String groupId = TestUtils.generateGroupId();
        enableNoCircularReferences();
        String a = TestUtils.generateArtifactId();
        String b = TestUtils.generateArtifactId();

        createVersion(groupId, b, null, "RecordB");
        createVersion(groupId, a, null, "RecordA", ref(groupId, b, "2", "b.avsc"));

        RuleViolationProblemDetails error = Assertions.assertThrows(RuleViolationProblemDetails.class,
                () -> createVersion(groupId, b, "2", "RecordBv2", ref(groupId, a, "1", "a.avsc")));
        assertCycleClosedBy(error, "a.avsc", groupId + "/" + a + "@1", groupId + "/" + b + "@2");
    }

    @Test
    public void testExplicitFirstVersionClosingCycleIsStillRejected() throws Exception {
        String groupId = TestUtils.generateGroupId();
        enableNoCircularReferences();
        String a = TestUtils.generateArtifactId();
        String b = TestUtils.generateArtifactId();

        // a@1 -> b@1, b does not exist yet; creating b (first version, id "1") referencing a@1 closes it.
        createVersion(groupId, a, null, "RecordA", ref(groupId, b, "1", "b.avsc"));

        RuleViolationProblemDetails error = Assertions.assertThrows(RuleViolationProblemDetails.class,
                () -> createVersion(groupId, b, null, "RecordB", ref(groupId, a, "1", "a.avsc")));
        assertCycleClosedBy(error, "a.avsc", groupId + "/" + a + "@1", groupId + "/" + b + "@1");
    }

    @Test
    public void testGeneratedVersionWithoutCycleIsAllowed() throws Exception {
        String groupId = TestUtils.generateGroupId();
        enableNoCircularReferences();
        String a = TestUtils.generateArtifactId();
        String b = TestUtils.generateArtifactId();

        // a@1 -> b@1 (an existing version); b's next version referencing a@1 is b@2 -> a@1 -> b@1: no cycle.
        createVersion(groupId, b, null, "RecordB");
        createVersion(groupId, a, null, "RecordA", ref(groupId, b, "1", "b.avsc"));

        VersionMetaData created = createVersion(groupId, b, null, "RecordBv2", ref(groupId, a, "1", "a.avsc"));
        Assertions.assertEquals("2", created.getVersion());
        Assertions.assertEquals(List.of("1", "2"), versionsOf(groupId, b));
    }

    @Test
    public void testGeneratedVersionWithDanglingReferenceToOtherFutureVersionIsRejected() throws Exception {
        String groupId = TestUtils.generateGroupId();
        enableNoCircularReferences();
        String a = TestUtils.generateArtifactId();
        String b = TestUtils.generateArtifactId();

        // a@1 -> b@99, which does not exist; b@1 has no references.
        createVersion(groupId, b, null, "RecordB");
        createVersion(groupId, a, null, "RecordA", ref(groupId, b, "99", "b.avsc"));

        // With a generated id, b@99 might be the id the new version receives, so it is rejected.
        RuleViolationProblemDetails error = Assertions.assertThrows(RuleViolationProblemDetails.class,
                () -> createVersion(groupId, b, null, "RecordBv2", ref(groupId, a, "1", "a.avsc")));
        assertCycleClosedBy(error, "a.avsc", groupId + "/" + a + "@1", groupId + "/" + b + "@99");
        Assertions.assertEquals(List.of("1"), versionsOf(groupId, b));

        // With the explicit id 2, the new version is known not to be b@99: no cycle.
        VersionMetaData created = createVersion(groupId, b, "2", "RecordBv2", ref(groupId, a, "1", "a.avsc"));
        Assertions.assertEquals("2", created.getVersion());
        Assertions.assertEquals(List.of("1", "2"), versionsOf(groupId, b));
    }

    private void enableNoCircularReferences() {
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.INTEGRITY);
        createRule.setConfig(IntegrityLevel.NO_CIRCULAR_REFERENCES.name());
        clientV3.admin().rules().post(createRule);
    }

    private static ArtifactReference ref(String groupId, String artifactId, String version, String name) {
        ArtifactReference ref = new ArtifactReference();
        ref.setGroupId(groupId);
        ref.setArtifactId(artifactId);
        ref.setVersion(version);
        ref.setName(name);
        return ref;
    }

    private VersionMetaData createVersion(String groupId, String artifactId, String version, String recordName,
            ArtifactReference... references) {
        VersionContent content = new VersionContent();
        content.setContent(String.format(AVRO_SCHEMA_TEMPLATE, recordName));
        content.setContentType(ContentTypes.APPLICATION_JSON);
        content.setReferences(List.of(references));
        CreateVersion createVersion = new CreateVersion();
        createVersion.setVersion(version);
        createVersion.setContent(content);

        boolean exists = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.artifactId = artifactId;
        }).getCount() > 0;
        if (exists) {
            return clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                    .post(createVersion);
        }
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AVRO);
        createArtifact.setFirstVersion(createVersion);
        return clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact).getVersion();
    }

    private List<String> versionsOf(String groupId, String artifactId) {
        return clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get()
                .getVersions().stream().map(v -> v.getVersion()).sorted().collect(Collectors.toList());
    }

    private static void assertCycleClosedBy(RuleViolationProblemDetails error, String referenceName,
            String reference, String versionBeingWritten) {
        Assertions.assertEquals(400, error.getStatus());
        String description = String.format(
                "Circular reference detected in reference graph: %s leads back to %s, the artifact being written (cycle)",
                reference, versionBeingWritten);
        Assertions.assertEquals("Circular artifact reference(s) detected. Causes: " + description + " at "
                + referenceName, error.getDetail());
        Assertions.assertEquals(1, error.getCauses().size());
        var cause = error.getCauses().get(0);
        Assertions.assertEquals(referenceName, cause.getContext());
        Assertions.assertEquals(description, cause.getDescription());
    }
}
