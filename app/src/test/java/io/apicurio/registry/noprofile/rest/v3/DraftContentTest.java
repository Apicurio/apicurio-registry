package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@QuarkusTest
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

    @Test
    public void testCreateDraftArtifact() throws Exception {
        String content = resourceToString("openapi-empty.json");
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.OPENAPI,
                content, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setIsDraft(true);
        createArtifact.getFirstVersion().setVersion("1.0.0");

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("1.0.0").get();
        Assertions.assertNotNull(vmd);
        Assertions.assertEquals(VersionState.DRAFT, vmd.getState());
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

}
