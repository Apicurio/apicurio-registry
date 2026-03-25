package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v3.beans.CreateArtifact;
import io.apicurio.registry.rest.v3.beans.CreateVersion;
import io.apicurio.registry.rest.v3.beans.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.MutabilityEnabledProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(MutabilityEnabledProfile.class)
public class Base64ContentTest extends AbstractResourceTestBase {

    private static final String GROUP = "Base64ContentTest";

    private static final String JSON_SCHEMA_CONTENT = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string"
                    }
                }
            }
            """;

    /**
     * Test creating an artifact with base64-encoded content.
     */
    @Test
    public void testCreateArtifactWithBase64Content() throws Exception {
        String artifactId = "testCreateArtifactWithBase64Content";
        String base64Content = Base64.getEncoder().encodeToString(
                JSON_SCHEMA_CONTENT.getBytes(StandardCharsets.UTF_8));

        CreateArtifact createArtifact = CreateArtifact.builder()
                .artifactId(artifactId)
                .artifactType(ArtifactType.JSON.value())
                .firstVersion(CreateVersion.builder()
                        .content(VersionContent.builder()
                                .content(base64Content)
                                .contentType(ContentTypes.APPLICATION_JSON)
                                .encoding(VersionContent.Encoding.base64)
                                .build())
                        .build())
                .build();

        // Create the artifact with base64-encoded content
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("artifact.artifactId", equalTo(artifactId))
                .body("artifact.artifactType", equalTo(ArtifactType.JSON.value()));

        // Verify the stored content is the decoded JSON, not the base64 string
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200)
                .body("type", equalTo("object"));
    }

    /**
     * Test creating an artifact version with base64-encoded content.
     */
    @Test
    public void testCreateArtifactVersionWithBase64Content() throws Exception {
        String artifactId = "testCreateVersionWithBase64Content";

        // First, create an artifact with normal content
        createArtifact(GROUP, artifactId, ArtifactType.JSON.value(), JSON_SCHEMA_CONTENT,
                ContentTypes.APPLICATION_JSON);

        // Now create a new version with base64-encoded content
        String updatedContent = """
                {
                    "type": "object",
                    "properties": {
                        "name": {
                            "type": "string"
                        },
                        "age": {
                            "type": "integer"
                        }
                    }
                }
                """;
        String base64Content = Base64.getEncoder().encodeToString(
                updatedContent.getBytes(StandardCharsets.UTF_8));

        CreateVersion createVersion = CreateVersion.builder()
                .content(VersionContent.builder()
                        .content(base64Content)
                        .contentType(ContentTypes.APPLICATION_JSON)
                        .encoding(VersionContent.Encoding.base64)
                        .build())
                .build();

        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId).body(createVersion)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions")
                .then().statusCode(200)
                .body("version", equalTo("2"));

        // Verify the stored content has the "age" property
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200)
                .body("properties.age.type", equalTo("integer"));
    }

    /**
     * Test that creating an artifact without encoding still works as before.
     */
    @Test
    public void testCreateArtifactWithNoEncoding() throws Exception {
        String artifactId = "testCreateArtifactWithNoEncoding";

        CreateArtifact createArtifact = TestUtils.serverCreateArtifact(artifactId, ArtifactType.JSON.value(),
                JSON_SCHEMA_CONTENT, ContentTypes.APPLICATION_JSON);

        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("artifact.artifactId", equalTo(artifactId))
                .body("artifact.artifactType", equalTo(ArtifactType.JSON.value()));

        // Verify the stored content is correct
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200)
                .body("type", equalTo("object"));
    }

    /**
     * Test that invalid base64 content returns a 400 error.
     */
    @Test
    public void testCreateArtifactWithInvalidBase64() throws Exception {
        String artifactId = "testCreateArtifactWithInvalidBase64";

        CreateArtifact createArtifact = CreateArtifact.builder()
                .artifactId(artifactId)
                .artifactType(ArtifactType.JSON.value())
                .firstVersion(CreateVersion.builder()
                        .content(VersionContent.builder()
                                .content("!!!not-valid-base64!!!")
                                .contentType(ContentTypes.APPLICATION_JSON)
                                .encoding(VersionContent.Encoding.base64)
                                .build())
                        .build())
                .build();

        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(400);
    }

    /**
     * Test updating artifact version content with base64-encoded content.
     */
    @Test
    public void testUpdateArtifactVersionContentWithBase64() throws Exception {
        String artifactId = "testUpdateVersionContentWithBase64";

        // Create an artifact with a draft version
        CreateArtifact createArtifact = CreateArtifact.builder()
                .artifactId(artifactId)
                .artifactType(ArtifactType.JSON.value())
                .firstVersion(CreateVersion.builder()
                        .isDraft(true)
                        .content(VersionContent.builder()
                                .content(JSON_SCHEMA_CONTENT)
                                .contentType(ContentTypes.APPLICATION_JSON)
                                .build())
                        .build())
                .build();

        var response = given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .extract().body();
        String version = response.path("version.version");

        // Update the draft version content with base64-encoded content
        String updatedContent = """
                {
                    "type": "object",
                    "properties": {
                        "email": {
                            "type": "string",
                            "format": "email"
                        }
                    }
                }
                """;
        String base64Content = Base64.getEncoder().encodeToString(
                updatedContent.getBytes(StandardCharsets.UTF_8));

        VersionContent updateBody = VersionContent.builder()
                .content(base64Content)
                .contentType(ContentTypes.APPLICATION_JSON)
                .encoding(VersionContent.Encoding.base64)
                .build();

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .pathParam("version", version)
                .body(updateBody)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/content")
                .then().statusCode(204);

        // Verify the updated content
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .pathParam("version", version)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/content")
                .then().statusCode(200)
                .body("properties.email.format", equalTo("email"));
    }
}

