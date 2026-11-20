package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.models.WrappedVersionState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.MutabilityEnabledProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Verifies the {@link io.apicurio.registry.extensions.ArtifactVersionWriteHook} contract as driven by the
 * v3 REST API: when each stage runs, that content rewrites are stored, that a rejection prevents
 * persistence, and that post-publish actions skip dry runs and drafts.
 */
@QuarkusTest
@TestProfile(MutabilityEnabledProfile.class)
public class ArtifactVersionWriteHookTest extends AbstractResourceTestBase {

    private static final String SCHEMA_TEMPLATE = """
            {"type": "object", "description": "%s"}
            """;

    @Inject
    RecordingWriteHook hook;

    @Test
    public void testCreateArtifactRunsAllStagesAndStoresRewrittenContent() throws Exception {
        String groupId = newGroupId();
        String artifactId = TestUtils.generateArtifactId();

        clientV3.groups().byGroupId(groupId).artifacts().post(newArtifact(artifactId, RecordingWriteHook.REWRITE_MARKER));

        Assertions.assertEquals(List.of(
                "prepare:CREATE_ARTIFACT:" + artifactId,
                "before:CREATE_ARTIFACT:" + artifactId,
                "after:CREATE_ARTIFACT:" + artifactId), hook.eventsFor(artifactId));
        Assertions.assertEquals(SCHEMA_TEMPLATE.formatted(RecordingWriteHook.REWRITTEN_MARKER),
                getContent(groupId, artifactId, "1"));
    }

    @Test
    public void testDryRunSkipsAfterPublishAction() {
        String groupId = newGroupId();
        String artifactId = TestUtils.generateArtifactId();

        clientV3.groups().byGroupId(groupId).artifacts().post(newArtifact(artifactId, "plain"),
                config -> config.queryParameters.dryRun = true);

        Assertions.assertEquals(List.of(
                "prepare:CREATE_ARTIFACT:" + artifactId,
                "before:CREATE_ARTIFACT:" + artifactId), hook.eventsFor(artifactId));
    }

    @Test
    public void testRejectionPreventsPersistence() {
        String groupId = newGroupId();
        String artifactId = TestUtils.generateArtifactId();

        RuleViolationProblemDetails error = Assertions.assertThrows(RuleViolationProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts()
                        .post(newArtifact(artifactId, RecordingWriteHook.REJECT_MARKER)));
        Assertions.assertEquals("Rejected by test write hook", error.getTitle());
        Assertions.assertEquals("/description", error.getCauses().get(0).getContext());

        ProblemDetails notFound = Assertions.assertThrows(ProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());
        Assertions.assertEquals(404, notFound.getStatus());
        Assertions.assertEquals(List.of("prepare:CREATE_ARTIFACT:" + artifactId), hook.eventsFor(artifactId));
    }

    @Test
    public void testCreateVersionRunsAllStagesAndStoresRewrittenContent() throws Exception {
        String groupId = newGroupId();
        String artifactId = TestUtils.generateArtifactId();
        clientV3.groups().byGroupId(groupId).artifacts().post(newArtifact(artifactId, "plain"));

        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(newVersion("2", RecordingWriteHook.REWRITE_MARKER));

        Assertions.assertEquals(List.of(
                "prepare:CREATE_ARTIFACT:" + artifactId,
                "before:CREATE_ARTIFACT:" + artifactId,
                "after:CREATE_ARTIFACT:" + artifactId,
                "prepare:CREATE_VERSION:" + artifactId,
                "before:CREATE_VERSION:" + artifactId,
                "after:CREATE_VERSION:" + artifactId), hook.eventsFor(artifactId));
        Assertions.assertEquals(SCHEMA_TEMPLATE.formatted(RecordingWriteHook.REWRITTEN_MARKER),
                getContent(groupId, artifactId, "2"));
    }

    @Test
    public void testDraftSkipsPublishStagesUntilPublished() {
        String groupId = newGroupId();
        String artifactId = TestUtils.generateArtifactId();
        CreateArtifact draft = newArtifact(artifactId, "plain");
        draft.getFirstVersion().setIsDraft(true);
        clientV3.groups().byGroupId(groupId).artifacts().post(draft);

        Assertions.assertEquals(List.of("prepare:CREATE_ARTIFACT:" + artifactId), hook.eventsFor(artifactId));

        WrappedVersionState enabled = new WrappedVersionState();
        enabled.setState(VersionState.ENABLED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("1").state().put(enabled);

        Assertions.assertEquals(List.of(
                "prepare:CREATE_ARTIFACT:" + artifactId,
                "before:PUBLISH_DRAFT:" + artifactId,
                "after:PUBLISH_DRAFT:" + artifactId), hook.eventsFor(artifactId));
    }

    @Test
    public void testHookIgnoresOtherGroups() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        clientV3.groups().byGroupId(groupId).artifacts().post(newArtifact(artifactId, RecordingWriteHook.REJECT_MARKER));

        Assertions.assertEquals(List.of(), hook.eventsFor(artifactId));
    }

    private static String newGroupId() {
        return RecordingWriteHook.GROUP_PREFIX + UUID.randomUUID();
    }

    private static CreateArtifact newArtifact(String artifactId, String description) {
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON,
                SCHEMA_TEMPLATE.formatted(description), ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setVersion("1");
        return createArtifact;
    }

    private static CreateVersion newVersion(String version, String description) {
        CreateVersion createVersion = new CreateVersion();
        createVersion.setVersion(version);
        VersionContent content = new VersionContent();
        content.setContent(SCHEMA_TEMPLATE.formatted(description));
        content.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(content);
        return createVersion;
    }

    private String getContent(String groupId, String artifactId, String version) throws Exception {
        try (InputStream stream = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(version).content().get()) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }
}
