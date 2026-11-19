package io.apicurio.registry.noprofile.integrity;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.models.WrappedVersionState;
import io.apicurio.registry.rest.client.v2.RegistryClient;
import io.apicurio.registry.rest.client.v2.models.ArtifactContent;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.MutabilityEnabledProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import com.microsoft.kiota.ApiException;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The {@code NO_CIRCULAR_REFERENCES} check on write paths where the version id is known although it is not
 * given through the v3 create-version call: v2 writes with an explicit version, and draft publication. Only
 * a reference to exactly that version closes a cycle there; a dangling reference to another future version
 * of the same artifact does not (#11309).
 */
@QuarkusTest
@TestProfile(MutabilityEnabledProfile.class)
public class CircularReferenceKnownVersionIntegrityRuleTest extends AbstractResourceTestBase {

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

    private RegistryClient clientV2;

    @BeforeEach
    void enableNoCircularReferences() {
        clientV2 = createRestClientV2(vertx);
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.INTEGRITY);
        createRule.setConfig(IntegrityLevel.NO_CIRCULAR_REFERENCES.name());
        clientV3.admin().rules().post(createRule);
    }

    @AfterEach
    void cleanup() {
        clientV3.admin().rules().byRuleType(RuleType.INTEGRITY.name()).delete();
    }

    @Test
    public void testV2ExplicitVersionWithDanglingReferenceToOtherFutureVersionIsAllowed() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String a = TestUtils.generateArtifactId();
        String b = TestUtils.generateArtifactId();
        createVersion(groupId, b, null, "RecordB", false);
        createVersion(groupId, a, null, "RecordA", false, ref(groupId, b, "99", "b.avsc"));

        // b@2 -> a@1 -> b@99 (missing): not a cycle, as the new version is known to be b@2.
        var created = clientV2.groups().byGroupId(groupId).artifacts().byArtifactId(b).versions()
                .post(v2Content("RecordBv2", groupId, a, "1", "a.avsc"),
                        config -> config.headers.put("X-Registry-Version", Set.of("2")));
        Assertions.assertEquals("2", created.getVersion());
        Assertions.assertEquals(List.of("1", "2"), versionsOf(groupId, b));
    }

    @Test
    public void testV2ExplicitVersionClosingCycleIsRejected() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String a = TestUtils.generateArtifactId();
        String b = TestUtils.generateArtifactId();
        createVersion(groupId, b, null, "RecordB", false);
        createVersion(groupId, a, null, "RecordA", false, ref(groupId, b, "2", "b.avsc"));

        // The v2 client maps only 409 to RuleViolationError; rule violations answer 400 here.
        ApiException error = Assertions.assertThrows(ApiException.class,
                () -> clientV2.groups().byGroupId(groupId).artifacts().byArtifactId(b).versions()
                        .post(v2Content("RecordBv2", groupId, a, "1", "a.avsc"),
                                config -> config.headers.put("X-Registry-Version", Set.of("2"))));
        Assertions.assertEquals(400, error.getResponseStatusCode());
        Assertions.assertEquals(List.of("1"), versionsOf(groupId, b));
    }

    @Test
    public void testDraftPublicationWithDanglingReferenceToOtherFutureVersionIsAllowed() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String a = TestUtils.generateArtifactId();
        String b = TestUtils.generateArtifactId();
        createVersion(groupId, b, null, "RecordB", false);
        createVersion(groupId, a, null, "RecordA", false, ref(groupId, b, "99", "b.avsc"));
        // A draft skips the rules; its id (2) is generated now and known when it is published.
        VersionMetaData draft = createVersion(groupId, b, null, "RecordBv2", true, ref(groupId, a, "1", "a.avsc"));
        Assertions.assertEquals("2", draft.getVersion());

        publish(groupId, b, "2");
        Assertions.assertEquals(VersionState.ENABLED, clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(b).versions().byVersionExpression("2").get().getState());
    }

    @Test
    public void testDraftPublicationClosingCycleIsRejected() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String a = TestUtils.generateArtifactId();
        String b = TestUtils.generateArtifactId();
        createVersion(groupId, b, null, "RecordB", false);
        createVersion(groupId, a, null, "RecordA", false, ref(groupId, b, "2", "b.avsc"));
        VersionMetaData draft = createVersion(groupId, b, null, "RecordBv2", true, ref(groupId, a, "1", "a.avsc"));
        Assertions.assertEquals("2", draft.getVersion());

        ProblemDetails error = Assertions.assertThrows(ProblemDetails.class, () -> publish(groupId, b, "2"));
        Assertions.assertEquals(400, error.getStatus());
        Assertions.assertEquals("RuleViolationException", error.getName());
        Assertions.assertEquals(String.format("Circular artifact reference(s) detected. Causes: "
                + "Circular reference detected in reference graph: %s/%s@1 -> %s/%s@2 -> %s/%s@1 (cycle) at a.avsc",
                groupId, a, groupId, b, groupId, a), error.getDetail());
        Assertions.assertEquals(VersionState.DRAFT, clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(b).versions().byVersionExpression("2").get().getState());
    }

    private void publish(String groupId, String artifactId, String version) {
        WrappedVersionState enabled = new WrappedVersionState();
        enabled.setState(VersionState.ENABLED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression(version).state().put(enabled);
    }

    private static ArtifactContent v2Content(String recordName, String groupId, String artifactId, String version,
            String name) {
        io.apicurio.registry.rest.client.v2.models.ArtifactReference ref =
                new io.apicurio.registry.rest.client.v2.models.ArtifactReference();
        ref.setGroupId(groupId);
        ref.setArtifactId(artifactId);
        ref.setVersion(version);
        ref.setName(name);
        ArtifactContent content = new ArtifactContent();
        content.setContent(String.format(AVRO_SCHEMA_TEMPLATE, recordName));
        content.setReferences(List.of(ref));
        return content;
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
            boolean draft, ArtifactReference... references) {
        VersionContent content = new VersionContent();
        content.setContent(String.format(AVRO_SCHEMA_TEMPLATE, recordName));
        content.setContentType(ContentTypes.APPLICATION_JSON);
        content.setReferences(List.of(references));
        CreateVersion createVersion = new CreateVersion();
        createVersion.setVersion(version);
        createVersion.setContent(content);
        createVersion.setIsDraft(draft);

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
}
