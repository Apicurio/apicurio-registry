package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.UserInterfaceConfig;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.models.WrappedVersionState;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.MutabilityEnabledProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestProfile(MutabilityEnabledProfile.class)
public class DraftContentTest extends AbstractResourceTestBase {

    private static final String AVRO_CONTENT_V1 = """
            {
               "type" : "record",
               "namespace" : "Apicurio",
               "name" : "FullName",
               "fields" : [
                  { "name" : "FirstName" , "type" : "string" },
                  { "name" : "LastName" , "type" : "string" }
               ]
            }
            """;

    private static final String AVRO_CONTENT_V2 = """
            {
               "type" : "record",
               "namespace" : "Apicurio",
               "name" : "FullName",
               "fields" : [
                  { "name" : "FirstName" , "type" : "string" },
                  { "name" : "MiddleName" , "type" : "string" },
                  { "name" : "LastName" , "type" : "string" }
               ]
            }
            """;

    private static final String INVALID_AVRO_CONTENT = """
            {
               "type" : "record",
               "namespace" : "Apicurio",
               "name" : "FullName"
            """;

    @Test
    public void testCreateDraftArtifact() throws Exception {
        String content = resourceToString("openapi-empty.json");
        // Ensure the content is unique because we will do a contentHash check later in the test.
        content = content.replace("Empty API", "Unique API: " + UUID.randomUUID().toString());
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.OPENAPI,
                content, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setIsDraft(true);
        createArtifact.getFirstVersion().setVersion("1.0.0");

        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertNotNull(car.getVersion());

        VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("1.0.0").get();
        Assertions.assertNotNull(vmd);
        Assertions.assertEquals(VersionState.DRAFT, vmd.getState());

        // Note: Should NOT be able to fetch its content by globalId (disallowed for DRAFT content)
        Long globalId = car.getVersion().getGlobalId();
        Assertions.assertNotNull(globalId);
        Assertions.assertThrows(ProblemDetails.class, () -> {
            clientV3.ids().globalIds().byGlobalId(globalId).get();
        });

        // Note: Should NOT be able to fetch its content by contentId (disallowed for DRAFT content)
        Long contentId = car.getVersion().getContentId();
        Assertions.assertNotNull(contentId);
        Assertions.assertThrows(ProblemDetails.class, () -> {
            clientV3.ids().contentIds().byContentId(contentId).get();
        });

        // Note: Should NOT be able to fetch its content by contentHash (disallowed for DRAFT content)
        ContentWrapperDto contentWrapperDto = ContentWrapperDto.builder()
                .content(ContentHandle.create(content)).contentType(ContentTypes.APPLICATION_JSON).build();
        String contentHash = RegistryContentUtils.contentHash(contentWrapperDto);
        Assertions.assertThrows(ProblemDetails.class, () -> {
            clientV3.ids().contentHashes().byContentHash(contentHash).get();
        });
    }

    @Test
    public void testCreateDraftArtifactVersion() throws Exception {
        String content = resourceToString("openapi-empty.json");
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.OPENAPI,
                content, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setIsDraft(false);
        createArtifact.getFirstVersion().setVersion("1.0.0");

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        CreateVersion createVersion = TestUtils.clientCreateVersion(content, ContentTypes.APPLICATION_JSON);
        createVersion.setVersion("1.0.1");
        createVersion.setIsDraft(true);
        VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().post(createVersion);

        Assertions.assertNotNull(vmd);
        Assertions.assertEquals(VersionState.DRAFT, vmd.getState());

        vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("1.0.0").get();
        Assertions.assertNotNull(vmd);
        Assertions.assertEquals(VersionState.ENABLED, vmd.getState());
        vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("1.0.1").get();
        Assertions.assertNotNull(vmd);
        Assertions.assertEquals(VersionState.DRAFT, vmd.getState());
    }

    @Test
    public void testUpdateDraftContent() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.ASYNCAPI,
                AVRO_CONTENT_V1, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setIsDraft(true);
        createArtifact.getFirstVersion().setVersion("1.0");

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("1.0").get();
        Assertions.assertNotNull(vmd);
        Assertions.assertEquals(VersionState.DRAFT, vmd.getState());

        try (InputStream inputStream = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression("1.0").content().get()) {
            String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            Assertions.assertEquals(TestUtils.normalizeMultiLineString(AVRO_CONTENT_V1),
                    TestUtils.normalizeMultiLineString(content));
        }

        VersionContent versionContent = new VersionContent();
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
        versionContent.setContent(AVRO_CONTENT_V2);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("1.0").content().put(versionContent);

        try (InputStream inputStream = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression("1.0").content().get()) {
            String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            Assertions.assertEquals(TestUtils.normalizeMultiLineString(AVRO_CONTENT_V2),
                    TestUtils.normalizeMultiLineString(content));
        }
    }

    @Test
    public void testCannotUpdateNonDraftContent() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.ASYNCAPI,
                AVRO_CONTENT_V1, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setVersion("1.0");

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("1.0").get();
        Assertions.assertNotNull(vmd);
        Assertions.assertEquals(VersionState.ENABLED, vmd.getState());

        try (InputStream inputStream = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression("1.0").content().get()) {
            String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            Assertions.assertEquals(TestUtils.normalizeMultiLineString(AVRO_CONTENT_V1),
                    TestUtils.normalizeMultiLineString(content));
        }

        VersionContent versionContent = new VersionContent();
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
        versionContent.setContent(AVRO_CONTENT_V2);
        ProblemDetails error = Assertions.assertThrows(ProblemDetails.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                    .byVersionExpression("1.0").content().put(versionContent);
        });
        Assertions.assertEquals("ConflictException", error.getName());
        Assertions.assertEquals("Requested artifact version is not in DRAFT state.  Update disallowed.",
                error.getTitle());

        try (InputStream inputStream = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression("1.0").content().get()) {
            String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            Assertions.assertEquals(TestUtils.normalizeMultiLineString(AVRO_CONTENT_V1),
                    TestUtils.normalizeMultiLineString(content));
        }
    }

    @Test
    public void testSearchForDraftContent() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId1 = TestUtils.generateArtifactId();
        String artifactId2 = TestUtils.generateArtifactId();
        String artifactId3 = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId1, ArtifactType.ASYNCAPI,
                AVRO_CONTENT_V1, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setVersion("1.0");
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        CreateVersion createVersion = TestUtils.clientCreateVersion(AVRO_CONTENT_V2,
                ContentTypes.APPLICATION_JSON);
        createVersion.setVersion("1.1");
        createVersion.setIsDraft(true);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).versions()
                .post(createVersion);

        createArtifact = TestUtils.clientCreateArtifact(artifactId2, ArtifactType.ASYNCAPI, AVRO_CONTENT_V1,
                ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setVersion("1.0");
        createArtifact.getFirstVersion().setIsDraft(true);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        createArtifact = TestUtils.clientCreateArtifact(artifactId3, ArtifactType.ASYNCAPI, AVRO_CONTENT_V1,
                ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setVersion("1.0");
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        for (int i = 1; i <= 5; i++) {
            createVersion = TestUtils.clientCreateVersion(AVRO_CONTENT_V2, ContentTypes.APPLICATION_JSON);
            createVersion.setVersion("1." + i);
            createVersion.setIsDraft(true);
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId3).versions()
                    .post(createVersion);
        }

        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.state = VersionState.DRAFT;
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(7, results.getVersions().size());
    }

    @Test
    public void testCreateInvalidDraftArtifact() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Enable validity group rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).rules().post(createRule);

        // Create artifact with first version that has invalid content
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.OPENAPI,
                INVALID_AVRO_CONTENT, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setIsDraft(true);
        createArtifact.getFirstVersion().setVersion("1.0.0");
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Now try to transition from DRAFT to ENABLED - should fail
        WrappedVersionState enabled = new WrappedVersionState();
        enabled.setState(VersionState.ENABLED);
        ProblemDetails error = Assertions.assertThrows(ProblemDetails.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                    .byVersionExpression("1.0.0").state().put(enabled);
        });
        Assertions.assertEquals("RuleViolationException", error.getName());
        Assertions.assertEquals("Syntax violation for OpenAPI artifact.", error.getTitle());
    }

    @Test
    public void testCreateInvalidDraftVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create empty artifact
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AVRO);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Enable the validity rule for the new artifact.
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(createRule);

        // Try to create a new version with invalid content (should work if state is DRAFT).
        CreateVersion createVersion = TestUtils.clientCreateVersion(INVALID_AVRO_CONTENT,
                ContentTypes.APPLICATION_JSON);
        createVersion.setVersion("1.0.0");
        createVersion.setIsDraft(true);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);

        // Now try to transition from DRAFT to ENABLED - should fail
        WrappedVersionState enabled = new WrappedVersionState();
        enabled.setState(VersionState.ENABLED);
        ProblemDetails error = Assertions.assertThrows(ProblemDetails.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                    .byVersionExpression("1.0.0").state().put(enabled);
        });
        Assertions.assertEquals("RuleViolationException", error.getName());
        Assertions.assertEquals("Syntax violation for Avro artifact.", error.getTitle());
    }

    @Test
    public void testDraftVersionsWithBranches() throws Exception {
        String content = resourceToString("openapi-empty.json");
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // First version is ENABLED
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.OPENAPI,
                content, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setIsDraft(false);
        createArtifact.getFirstVersion().setVersion("1.0.0");

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        VersionSearchResults latestBranch = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).branches().byBranchId("latest").versions().get();
        Assertions.assertEquals(1, latestBranch.getVersions().size());
        ProblemDetails problemDetails = Assertions.assertThrows(ProblemDetails.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                    .byBranchId("drafts").versions().get();
        });
        Assertions.assertEquals("BranchNotFoundException", problemDetails.getName());

        // Second version is DRAFT
        CreateVersion createVersion = TestUtils.clientCreateVersion(content, ContentTypes.APPLICATION_JSON);
        createVersion.setVersion("1.0.1");
        createVersion.setIsDraft(true);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);

        latestBranch = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .byBranchId("latest").versions().get();
        Assertions.assertEquals(1, latestBranch.getVersions().size());
        VersionSearchResults draftsBranch = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).branches().byBranchId("drafts").versions().get();
        Assertions.assertEquals(1, draftsBranch.getVersions().size());

        // Transition draft content to enabled
        WrappedVersionState enabled = new WrappedVersionState();
        enabled.setState(VersionState.ENABLED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("1.0.1").state().put(enabled);

        latestBranch = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .byBranchId("latest").versions().get();
        Assertions.assertEquals(2, latestBranch.getVersions().size());
        draftsBranch = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .byBranchId("drafts").versions().get();
        Assertions.assertEquals(0, draftsBranch.getVersions().size());
    }

    @Test
    public void testDraftVersionsInCcompat() throws Exception {
        String content = AVRO_CONTENT_V1;
        String groupId = GroupId.DEFAULT.getRawGroupIdWithDefaultString();
        String draftArtifactId = TestUtils.generateArtifactId();
        String enabledArtifactId = TestUtils.generateArtifactId();

        // Create artifact with version as DRAFT
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(draftArtifactId, ArtifactType.AVRO,
                content, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setIsDraft(true);
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertNotNull(car.getVersion());

        // Create artifact with version as ENABLED
        createArtifact = TestUtils.clientCreateArtifact(enabledArtifactId, ArtifactType.AVRO, content,
                ContentTypes.APPLICATION_JSON);
        car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertNotNull(car.getVersion());

        // Should be able to fetch the subject in ccompat
        List<String> allSubjects = confluentClient.getAllSubjects();
        Assertions.assertTrue(!allSubjects.isEmpty());

        // Should not be able to list versions - no versions are visible
        Assertions.assertThrows(RestClientException.class, () -> {
            confluentClient.getAllVersions(draftArtifactId);
        });

        List<Integer> allVersions = confluentClient.getAllVersions(enabledArtifactId);
        Assertions.assertEquals(1, allVersions.size());
    }

    @Test
    public void testDraftVersionsInCoreV2() throws Exception {
        String content = AVRO_CONTENT_V1;
        String groupId = GroupId.DEFAULT.getRawGroupIdWithDefaultString();
        String artifactId = TestUtils.generateArtifactId();

        // Create artifact with version as DRAFT
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO, content,
                ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setIsDraft(true);
        createArtifact.getFirstVersion().setVersion("1.0");
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertNotNull(car.getVersion());

        // The version of the artifact is DRAFT so v2 will report the artifact as 404 not found
        given().when().contentType(CT_JSON).pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/latest").then()
                .statusCode(404);

        // Transition draft content to enabled
        WrappedVersionState enabled = new WrappedVersionState();
        enabled.setState(VersionState.ENABLED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("1.0").state().put(enabled);

        // Now we can get the artifact
        given().when().contentType(CT_JSON).pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/latest").then()
                .statusCode(200);
    }

    @Test
    public void testUiConfig() throws Exception {
        UserInterfaceConfig config = clientV3.system().uiConfig().get();
        Assertions.assertNotNull(config);
        Assertions.assertTrue(config.getFeatures().getDraftMutability());
    }

}
