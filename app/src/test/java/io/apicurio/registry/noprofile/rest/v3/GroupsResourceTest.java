package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.v3.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rest.v3.beans.Comment;
import io.apicurio.registry.rest.v3.beans.CreateRule;
import io.apicurio.registry.rest.v3.beans.EditableArtifactMetaData;
import io.apicurio.registry.rest.v3.beans.EditableVersionMetaData;
import io.apicurio.registry.rest.v3.beans.IfArtifactExists;
import io.apicurio.registry.rest.v3.beans.NewComment;
import io.apicurio.registry.rest.v3.beans.Rule;
import io.apicurio.registry.rest.v3.beans.VersionMetaData;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.ReferenceType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.tests.DeletionEnabledProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import io.restassured.response.ValidatableResponse;
import org.apache.commons.codec.digest.DigestUtils;
import org.hamcrest.Matchers;
import org.jose4j.base64url.Base64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToObject;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@TestProfile(DeletionEnabledProfile.class)
public class GroupsResourceTest extends AbstractResourceTestBase {

    private static final String GROUP = "GroupsResourceTest";

    @Test
    public void testDefaultGroup() throws Exception {
        String oaiArtifactContent = resourceToString("openapi-empty.json");
        String jsonArtifactContent = resourceToString("jsonschema-valid.json");

        String defaultGroup = GroupId.DEFAULT.getRawGroupIdWithDefaultString();
        String group = "testDefaultGroup";

        // Create artifacts in null (default) group
        createArtifact(defaultGroup, "testDefaultGroup/EmptyAPI/1", ArtifactType.OPENAPI, oaiArtifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(defaultGroup, "testDefaultGroup/EmptyAPI/2", ArtifactType.OPENAPI, oaiArtifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(defaultGroup, "testDefaultGroup/EmptyAPI/3", ArtifactType.OPENAPI, oaiArtifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(defaultGroup, "testDefaultGroup/EmptyAPI/4", ArtifactType.OPENAPI, oaiArtifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(defaultGroup, "testDefaultGroup/EmptyAPI/5", ArtifactType.OPENAPI, oaiArtifactContent,
                ContentTypes.APPLICATION_JSON);

        // Create 2 artifacts in other group
        createArtifact(group, "testDefaultGroup/EmptyAPI/1", ArtifactType.OPENAPI, jsonArtifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(group, "testDefaultGroup/EmptyAPI/2", ArtifactType.OPENAPI, jsonArtifactContent,
                ContentTypes.APPLICATION_JSON);

        // Search each group to ensure the correct # of artifacts.
        given().when().queryParam("groupId", defaultGroup).get("/registry/v3/search/artifacts").then()
                .statusCode(200).body("count", greaterThanOrEqualTo(5));
        given().when().queryParam("groupId", group).get("/registry/v3/search/artifacts").then()
                .statusCode(200).body("count", equalTo(2));

        // Get the artifact content
        given().when().pathParam("groupId", defaultGroup)
                .pathParam("artifactId", "testDefaultGroup/EmptyAPI/1")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));
    }

    @Test
    public void testCreateArtifactRule() throws Exception {
        String oaiArtifactContent = resourceToString("openapi-empty.json");
        createArtifact("testCreateArtifactRule", "testCreateArtifactRule/EmptyAPI/1", ArtifactType.OPENAPI,
                oaiArtifactContent, ContentTypes.APPLICATION_JSON);

        // Test Rule type null
        CreateRule nullType = new CreateRule();
        nullType.setRuleType(null);
        nullType.setConfig("TestConfig");
        given().when().contentType(CT_JSON).pathParam("groupId", "testCreateArtifactRule")
                .pathParam("artifactId", "testCreateArtifactRule/EmptyAPI/1").body(nullType)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(400);

        // Test Rule config null
        CreateRule nullConfig = new CreateRule();
        nullConfig.setRuleType(RuleType.VALIDITY);
        nullConfig.setConfig(null);
        given().when().contentType(CT_JSON).pathParam("groupId", "testCreateArtifactRule")
                .pathParam("artifactId", "testCreateArtifactRule/EmptyAPI/1").body(nullConfig)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(400);

        // Test Rule config empty
        CreateRule emptyConfig = new CreateRule();
        emptyConfig.setRuleType(RuleType.VALIDITY);
        emptyConfig.setConfig("");
        given().when().contentType(CT_JSON).pathParam("groupId", "testCreateArtifactRule")
                .pathParam("artifactId", "testCreateArtifactRule/EmptyAPI/1").body(emptyConfig)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(400);

    }

    @Test
    public void testUpdateArtifactOwner() throws Exception {
        String oaiArtifactContent = resourceToString("openapi-empty.json");
        createArtifact("testUpdateArtifactOwner", "testUpdateArtifactOwner/EmptyAPI/1", ArtifactType.OPENAPI,
                oaiArtifactContent, ContentTypes.APPLICATION_JSON);

        EditableArtifactMetaData body = new EditableArtifactMetaData();
        body.setOwner("newOwner");

        given().when().contentType(CT_JSON).pathParam("groupId", "testUpdateArtifactOwner")
                .pathParam("artifactId", "testUpdateArtifactOwner/EmptyAPI/1").body(body)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}").then().statusCode(204);

        // TODO verify that the owner was changed.
    }

    @Test
    public void testUpdateEmptyArtifactOwner() throws Exception {
        String oaiArtifactContent = resourceToString("openapi-empty.json");
        createArtifact("testUpdateEmptyArtifactOwner", "testUpdateEmptyArtifactOwner/EmptyAPI/1",
                ArtifactType.OPENAPI, oaiArtifactContent, ContentTypes.APPLICATION_JSON);

        EditableArtifactMetaData body = new EditableArtifactMetaData();
        body.setOwner("");

        given().when().contentType(CT_JSON).pathParam("groupId", "testUpdateEmptyArtifactOwner")
                .pathParam("artifactId", "testUpdateEmptyArtifactOwner/EmptyAPI/1").body(body)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}").then().statusCode(400);
    }

    @Test
    public void testMultipleGroups() throws Exception {
        String oaiArtifactContent = resourceToString("openapi-empty.json");
        String jsonArtifactContent = resourceToString("jsonschema-valid.json");

        String group1 = "testMultipleGroups_1";
        String group2 = "testMultipleGroups_2";

        // Create 5 artifacts in Group 1
        createArtifact(group1, "testMultipleGroups/EmptyAPI/1", ArtifactType.OPENAPI, oaiArtifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(group1, "testMultipleGroups/EmptyAPI/2", ArtifactType.OPENAPI, oaiArtifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(group1, "testMultipleGroups/EmptyAPI/3", ArtifactType.OPENAPI, oaiArtifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(group1, "testMultipleGroups/EmptyAPI/4", ArtifactType.OPENAPI, oaiArtifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(group1, "testMultipleGroups/EmptyAPI/5", ArtifactType.OPENAPI, oaiArtifactContent,
                ContentTypes.APPLICATION_JSON);

        // Create 2 artifacts in Group 2
        createArtifact(group2, "testMultipleGroups/EmptyAPI/1", ArtifactType.OPENAPI, jsonArtifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(group2, "testMultipleGroups/EmptyAPI/2", ArtifactType.OPENAPI, jsonArtifactContent,
                ContentTypes.APPLICATION_JSON);

        // Get group 1 metadata
        given().when().pathParam("groupId", group1).get("/registry/v3/groups/{groupId}").then()
                .statusCode(200).body("groupId", equalTo("testMultipleGroups_1"));

        // Get group 2 metadata
        given().when().pathParam("groupId", group2).get("/registry/v3/groups/{groupId}").then()
                .statusCode(200).body("groupId", equalTo("testMultipleGroups_2"));

        // Search each group to ensure the correct # of artifacts.
        given().when().queryParam("groupId", group1).get("/registry/v3/search/artifacts").then()
                .statusCode(200).body("count", equalTo(5));
        given().when().queryParam("groupId", group2).get("/registry/v3/search/artifacts").then()
                .statusCode(200).body("count", equalTo(2));

        // Get the artifact content
        given().when().pathParam("groupId", group1).pathParam("artifactId", "testMultipleGroups/EmptyAPI/1")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));

        // Test delete group operations
        // Delete group 1 metadata
        given().when().pathParam("groupId", group1).delete("/registry/v3/groups/{groupId}").then()
                .statusCode(204);

        // Delete group 2 metadata
        given().when().pathParam("groupId", group2).delete("/registry/v3/groups/{groupId}").then()
                .statusCode(204);

        // Get group 1 metadata again, should return 404
        given().when().pathParam("groupId", group1).get("/registry/v3/groups/{groupId}").then()
                .statusCode(404);

        // Get group 1 metadata again, should return 404
        given().when().pathParam("groupId", group2).get("/registry/v3/groups/{groupId}").then()
                .statusCode(404);
    }

    @Test
    public void testCreateArtifact() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact - indicate the type via a header param
        createArtifact(GROUP, "testCreateArtifact/EmptyAPI/1", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Create OpenAPI artifact - indicate the type via the content-type
        io.apicurio.registry.rest.v3.beans.CreateArtifact createArtifact = TestUtils.serverCreateArtifact(
                "testCreateArtifact/EmptyAPI/2", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("artifact.groupId", equalTo(GROUP)).body("version.version", equalTo("1"))
                .body("artifact.artifactId", equalTo("testCreateArtifact/EmptyAPI/2"))
                .body("artifact.artifactType", equalTo(ArtifactType.OPENAPI));

        // Try to create a duplicate artifact ID (should fail)
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(409)
                .body("status", equalTo(409)).body("title", equalTo(
                        "An artifact with ID 'testCreateArtifact/EmptyAPI/2' in group 'GroupsResourceTest' already exists."));

        // Try to create an artifact with an invalid artifact type
        createArtifact = TestUtils.serverCreateArtifact("testCreateArtifact/EmptyAPI/invalidArtifactType",
                "INVALID_ARTIFACT_TYPE", artifactContent, ContentTypes.APPLICATION_JSON);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(artifactContent)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(400);

        // Create OpenAPI artifact - don't provide the artifact type
        createArtifact = TestUtils.serverCreateArtifact("testCreateArtifact/EmptyAPI/detect", null,
                artifactContent, ContentTypes.APPLICATION_JSON);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("artifact.artifactId", equalTo("testCreateArtifact/EmptyAPI/detect"))
                .body("artifact.artifactType", equalTo(ArtifactType.OPENAPI));

        // Create artifact with empty content (should fail)
        createArtifact = TestUtils.serverCreateArtifact("testCreateArtifact/EmptyContent", null, "",
                ContentTypes.APPLICATION_JSON);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(400);

        // Create OpenAPI artifact - provide a custom version #
        createArtifact = TestUtils.serverCreateArtifact("testCreateArtifact/EmptyAPI-customVersion",
                ArtifactType.OPENAPI, artifactContent, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setVersion("1.0.2");
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("artifact.groupId", equalTo(GROUP)).body("version.version", equalTo("1.0.2"))
                .body("artifact.artifactId", equalTo("testCreateArtifact/EmptyAPI-customVersion"))
                .body("artifact.artifactType", equalTo(ArtifactType.OPENAPI));

        // Create OpenAPI artifact - provide a custom name
        String customName = "CUSTOM NAME";
        createArtifact = TestUtils.serverCreateArtifact("testCreateArtifact/EmptyAPI-customName",
                ArtifactType.OPENAPI, artifactContent, ContentTypes.APPLICATION_JSON);
        createArtifact.setName(customName);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("artifact.groupId", equalTo(GROUP)).body("artifact.name", equalTo(customName))
                .body("artifact.artifactId", equalTo("testCreateArtifact/EmptyAPI-customName"))
                .body("artifact.artifactType", equalTo(ArtifactType.OPENAPI));

        // Create OpenAPI artifact - provide a custom description
        String customDescription = "CUSTOM DESCRIPTION";
        createArtifact = TestUtils.serverCreateArtifact("testCreateArtifact/EmptyAPI-customDescription",
                ArtifactType.OPENAPI, artifactContent, ContentTypes.APPLICATION_JSON);
        createArtifact.setDescription(customDescription);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("artifact.groupId", equalTo(GROUP))
                .body("artifact.description", equalTo(customDescription))
                .body("artifact.artifactId", equalTo("testCreateArtifact/EmptyAPI-customDescription"))
                .body("artifact.artifactType", equalTo(ArtifactType.OPENAPI));
    }

    @Test
    @DisabledIfEnvironmentVariable(named = CURRENT_ENV, matches = CURRENT_ENV_MAS_REGEX)
    @DisabledOnOs(OS.WINDOWS)
    public void testCreateArtifactNoAscii() {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact - provide a custom No-ASCII name
        String customNoASCIIName = "CUSTOM NAME with NO-ASCII char č";
        io.apicurio.registry.rest.v3.beans.CreateArtifact createArtifact = TestUtils.serverCreateArtifact(
                "testCreateArtifact/EmptyAPI-customNameEncoded", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact.setName(customNoASCIIName);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("artifact.groupId", equalTo(GROUP))
                .body("artifact.artifactId", equalTo("testCreateArtifact/EmptyAPI-customNameEncoded"))
                .body("artifact.artifactType", equalTo(ArtifactType.OPENAPI))
                .body("artifact.name", equalTo(customNoASCIIName));

        // Create OpenAPI artifact - provide a custom No-ASCII description
        String customNoASCIIDescription = "CUSTOM DESCRIPTION with NO-ASCII char č";
        createArtifact = TestUtils.serverCreateArtifact(
                "testCreateArtifact/EmptyAPI-customDescriptionEncoded", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact.setDescription(customNoASCIIDescription);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("artifact.groupId", equalTo(GROUP))
                .body("artifact.artifactId", equalTo("testCreateArtifact/EmptyAPI-customDescriptionEncoded"))
                .body("artifact.artifactType", equalTo(ArtifactType.OPENAPI))
                .body("artifact.description", equalTo(customNoASCIIDescription));
    }

    @Test
    public void testGetArtifact() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testGetArtifact/EmptyAPI", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Get the artifact content
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", "testGetArtifact/EmptyAPI")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));

        // Try to get artifact content for an artifact that doesn't exist.
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", "testGetArtifact/MissingAPI")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest").then()
                .statusCode(404).body("status", equalTo(404)).body("title", equalTo(
                        "No version '<tip of the branch 'latest' that does not have disabled status>' found for artifact with ID 'testGetArtifact/MissingAPI' in group 'GroupsResourceTest'."));
    }

    @Test
    public void testUpdateArtifact() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testUpdateArtifact/EmptyAPI", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Update OpenAPI artifact (new version)
        io.apicurio.registry.rest.v3.beans.CreateVersion createVersion = TestUtils
                .serverCreateVersion(updatedArtifactContent, ContentTypes.APPLICATION_JSON);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI").body(createVersion)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(200)
                .body("artifactId", equalTo("testUpdateArtifact/EmptyAPI"))
                .body("artifactType", equalTo(ArtifactType.OPENAPI));

        // Get the artifact content (should be the updated content)
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", "testUpdateArtifact/EmptyAPI")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API (Updated)"));

        // Try to update an artifact that doesn't exist.
        createVersion = TestUtils.serverCreateVersion(updatedArtifactContent, ArtifactType.OPENAPI);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testUpdateArtifact/MissingAPI").body(createVersion)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(404);

        // Try to update an artifact with empty content
        createVersion = TestUtils.serverCreateVersion("", ArtifactType.OPENAPI);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI").body(createVersion)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(400);

        // Update OpenAPI artifact with a custom version
        createVersion = TestUtils.serverCreateVersion(updatedArtifactContent, ArtifactType.OPENAPI);
        createVersion.setVersion("3.0.0.Final");
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI").body(createVersion)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(200)
                .body("version", equalTo("3.0.0.Final"))
                .body("artifactId", equalTo("testUpdateArtifact/EmptyAPI"))
                .body("artifactType", equalTo(ArtifactType.OPENAPI));

        // Update OpenAPI artifact with a custom name
        String customName = "CUSTOM NAME";
        createVersion = TestUtils.serverCreateVersion(updatedArtifactContent, ArtifactType.OPENAPI);
        createVersion.setName(customName);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI").body(createVersion)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(200)
                .body("name", equalTo(customName)).body("artifactId", equalTo("testUpdateArtifact/EmptyAPI"))
                .body("artifactType", equalTo(ArtifactType.OPENAPI));

        // Update OpenAPI artifact with a custom description
        String customDescription = "CUSTOM DESCRIPTION";
        createVersion = TestUtils.serverCreateVersion(updatedArtifactContent, ArtifactType.OPENAPI);
        createVersion.setDescription(customDescription);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI").body(createVersion)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(200)
                .body("description", equalTo(customDescription))
                .body("artifactId", equalTo("testUpdateArtifact/EmptyAPI"))
                .body("artifactType", equalTo(ArtifactType.OPENAPI));

    }

    @Test
    public void testUpdateVersionState() throws Exception {
        String oaiArtifactContent = resourceToString("openapi-empty.json");
        createArtifact("testUpdateVersionState", "testUpdateVersionState/EmptyAPI/1", ArtifactType.OPENAPI,
                oaiArtifactContent, ContentTypes.APPLICATION_JSON);

        EditableVersionMetaData body = new EditableVersionMetaData();
        body.setState(VersionState.DEPRECATED);

        // Update the artifact state to DEPRECATED.
        given().when().contentType(CT_JSON).pathParam("groupId", "testUpdateVersionState")
                .pathParam("artifactId", "testUpdateVersionState/EmptyAPI/1").body(body)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest").then()
                .statusCode(204);

        // Update the artifact state to DEPRECATED again.
        given().when().contentType(CT_JSON).pathParam("groupId", "testUpdateVersionState")
                .pathParam("artifactId", "testUpdateVersionState/EmptyAPI/1").body(body)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest").then()
                .statusCode(204);

        // Send a GET request to check if the artifact state is DEPRECATED.
        given().when().contentType(CT_JSON).pathParam("groupId", "testUpdateVersionState")
                .pathParam("artifactId", "testUpdateVersionState/EmptyAPI/1")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).header("X-Registry-Deprecated", "true");
    }

    @Test
    public void testUpdateArtifactVersionState() throws Exception {
        String oaiArtifactContent = resourceToString("openapi-empty.json");
        createArtifact("testUpdateArtifactVersionState", "testUpdateArtifactVersionState/EmptyAPI",
                ArtifactType.OPENAPI, oaiArtifactContent, ContentTypes.APPLICATION_JSON);

        EditableVersionMetaData body = new EditableVersionMetaData();
        body.setState(VersionState.DEPRECATED);

        // Update the artifact state to DEPRECATED.
        given().when().contentType(CT_JSON).pathParam("groupId", "testUpdateArtifactVersionState")
                .pathParam("artifactId", "testUpdateArtifactVersionState/EmptyAPI")
                .pathParam("versionId", "1").body(body)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionId}").then()
                .statusCode(204);

        // Update the artifact state to DEPRECATED again.
        given().when().contentType(CT_JSON).pathParam("groupId", "testUpdateArtifactVersionState")
                .pathParam("artifactId", "testUpdateArtifactVersionState/EmptyAPI")
                .pathParam("versionId", "1").body(body)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionId}").then()
                .statusCode(204);

        // Send a GET request to check if the artifact state is DEPRECATED.
        given().when().contentType(CT_JSON).pathParam("groupId", "testUpdateArtifactVersionState")
                .pathParam("artifactId", "testUpdateArtifactVersionState/EmptyAPI")
                .pathParam("versionId", "1")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionId}/content")
                .then().statusCode(200).header("X-Registry-Deprecated", "true");
    }

    @Test
    @DisabledIfEnvironmentVariable(named = CURRENT_ENV, matches = CURRENT_ENV_MAS_REGEX)
    @DisabledOnOs(OS.WINDOWS)
    public void testUpdateArtifactNoAscii() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testUpdateArtifactNoAscii/EmptyAPI", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Update OpenAPI artifact with a custom no-ascii name
        String customNoASCIIName = "CUSTOM NAME with NO-ASCII char ě";
        io.apicurio.registry.rest.v3.beans.CreateVersion createVersion = TestUtils
                .serverCreateVersion(updatedArtifactContent, ContentTypes.APPLICATION_JSON);
        createVersion.setName(customNoASCIIName);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testUpdateArtifactNoAscii/EmptyAPI").body(createVersion)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(200)
                .body("name", equalTo(customNoASCIIName))
                .body("artifactId", equalTo("testUpdateArtifactNoAscii/EmptyAPI"))
                .body("artifactType", equalTo(ArtifactType.OPENAPI));

        // Update OpenAPI artifact with a custom no-ascii description
        String customNoASCIIDescription = "CUSTOM DESCRIPTION with NO-ASCII char ě";
        createVersion = TestUtils.serverCreateVersion(updatedArtifactContent, ContentTypes.APPLICATION_JSON);
        createVersion.setDescription(customNoASCIIDescription);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .header("X-Registry-Description-Encoded",
                        Base64.encode(customNoASCIIDescription.getBytes(StandardCharsets.UTF_8)))
                .pathParam("artifactId", "testUpdateArtifactNoAscii/EmptyAPI").body(createVersion)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(200)
                .body("description", equalTo(customNoASCIIDescription))
                .body("artifactId", equalTo("testUpdateArtifactNoAscii/EmptyAPI"))
                .body("artifactType", equalTo(ArtifactType.OPENAPI));
    }

    @Test
    public void testDeleteArtifact() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testDeleteArtifact/EmptyAPI", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Make sure we can get the artifact content
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", "testDeleteArtifact/EmptyAPI")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));

        // Delete the artifact
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", "testDeleteArtifact/EmptyAPI")
                .delete("/registry/v3/groups/{groupId}/artifacts/{artifactId}").then().statusCode(204);

        // Try to get artifact for an artifact that doesn't exist.
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", "testDeleteArtifact/EmptyAPI")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}").then().statusCode(404)
                .body("status", equalTo(404)).body("title", equalTo(
                        "No artifact with ID 'testDeleteArtifact/EmptyAPI' in group 'GroupsResourceTest' was found."));

        // Try to delete an artifact that doesn't exist.
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", "testDeleteArtifact/MissingAPI")
                .delete("/registry/v3/groups/{groupId}/artifacts/{artifactId}").then().statusCode(404);
    }

    @Test
    public void testDeleteArtifactVersion() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testDeleteArtifactVersion/EmptyAPI", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Make sure we can get the artifact content
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));

        // Create a new version of the artifact
        io.apicurio.registry.rest.v3.beans.CreateVersion createVersion = TestUtils
                .serverCreateVersion(updatedArtifactContent, ContentTypes.APPLICATION_JSON);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI").body(createVersion)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(200)
                .body("version", equalTo("2")).body("artifactType", equalTo(ArtifactType.OPENAPI));

        // Get the artifact version 1
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI").pathParam("version", "1")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/content").then()
                .statusCode(200).body("openapi", equalTo("3.0.2")).body("info.title", equalTo("Empty API"));

        // Delete the artifact version 1
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI").pathParam("version", "1")
                .delete("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}").then()
                .statusCode(204);

        // Try to get artifact version 1 that doesn't exist.
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI").pathParam("version", "1")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}").then()
                .statusCode(404).body("status", equalTo(404)).body("title", equalTo(
                        "No version '1' found for artifact with ID 'testDeleteArtifactVersion/EmptyAPI' in group 'GroupsResourceTest'."));

        // Get the artifact version 2
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI").pathParam("version", "2")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/content").then()
                .statusCode(200).body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API (Updated)"));

        // Delete the artifact version 2
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI").pathParam("version", "2")
                .delete("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}").then()
                .statusCode(204);

        // Try to get artifact version 2 that doesn't exist.
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI").pathParam("version", "2")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}").then()
                .statusCode(404).body("status", equalTo(404)).body("title", equalTo(
                        "No version '2' found for artifact with ID 'testDeleteArtifactVersion/EmptyAPI' in group 'GroupsResourceTest'."));

        // Try to delete an artifact version 2 that doesn't exist.
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI").pathParam("version", "2")
                .delete("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}").then()
                .statusCode(404);
    }

    @Test
    public void testDeleteArtifactsInGroup() throws Exception {
        String group = "testDeleteArtifactsInGroup";
        String artifactContent = resourceToString("openapi-empty.json");

        // Create several artifacts in the group.
        createArtifact(group, "EmptyAPI-1", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(group, "EmptyAPI-2", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(group, "EmptyAPI-3", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Make sure we can search for all three artifacts in the group.
        given().when().queryParam("groupId", group).get("/registry/v3/search/artifacts").then()
                .statusCode(200).body("count", equalTo(3));

        // Delete the artifacts in the group
        given().when().pathParam("groupId", group).delete("/registry/v3/groups/{groupId}/artifacts").then()
                .statusCode(204);

        // Verify that all 3 artifacts were deleted
        given().when().queryParam("groupId", group).get("/registry/v3/search/artifacts").then()
                .statusCode(200).body("count", equalTo(0));
    }

    @Test
    public void testDeleteGroupWithArtifacts() throws Exception {
        String group = "testDeleteGroupWithArtifacts";
        String artifactContent = resourceToString("openapi-empty.json");

        // Create several artifacts in the group.
        createArtifact(group, "EmptyAPI-1", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(group, "EmptyAPI-2", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(group, "EmptyAPI-3", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Make sure we can search for all three artifacts in the group.
        given().when().queryParam("groupId", group).get("/registry/v3/search/artifacts").then()
                .statusCode(200).body("count", equalTo(3));

        // Delete the *group* (should delete all artifacts)
        given().when().pathParam("groupId", group).delete("/registry/v3/groups/{groupId}").then()
                .statusCode(204);

        // Verify that all 3 artifacts were deleted
        given().when().queryParam("groupId", group).get("/registry/v3/search/artifacts").then()
                .statusCode(200).body("count", equalTo(0));
    }

    @Test
    public void testListArtifactsInGroup() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String group = "testListArtifactsInGroup";

        // Create several artifacts in a group.
        createArtifact(group, "EmptyAPI-1", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(group, "EmptyAPI-2", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(group, "EmptyAPI-3", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // List the artifacts in the group
        given().when().pathParam("groupId", group).get("/registry/v3/groups/{groupId}/artifacts").then()
                .statusCode(200).body("count", equalTo(3));

        // Add two more artifacts to the group.
        createArtifact(group, "EmptyAPI-4", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(group, "EmptyAPI-5", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // List the artifacts in the group again
        given().when().pathParam("groupId", group).get("/registry/v3/groups/{groupId}/artifacts").then()
                .statusCode(200).body("count", equalTo(5));

        // Try to list artifacts for a group that doesn't exist

        // List the artifacts in the group
        given().when().pathParam("groupId", group + "-doesnotexist")
                .get("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("count", equalTo(0));

    }

    @Test
    public void testListArtifactVersions() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String artifactId = "testListArtifactVersions/EmptyAPI";

        // Create an artifact
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Update the artifact 5 times
        for (int idx = 0; idx < 5; idx++) {
            String versionContent = artifactContent.replace("Empty API", "Empty API (Update " + idx + ")");
            io.apicurio.registry.rest.v3.beans.CreateVersion createVersion = TestUtils
                    .serverCreateVersion(versionContent, ContentTypes.APPLICATION_JSON);
            given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                    .pathParam("artifactId", artifactId).body(createVersion)
                    .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then()
                    .statusCode(200).body("artifactId", equalTo(artifactId))
                    .body("artifactType", equalTo(ArtifactType.OPENAPI));
        }

        // List the artifact versions
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then()
                // .log().all()
                .statusCode(200).body("count", equalTo(6)).body("versions[0].version", notNullValue());

        // Try to list artifact versions for an artifact that doesn't exist.
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testListArtifactVersions/MissingAPI")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(404);

    }

    @Test
    public void testCreateArtifactVersion() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testCreateArtifactVersion/EmptyAPI", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Create a new version of the artifact
        io.apicurio.registry.rest.v3.beans.CreateVersion createVersion = TestUtils
                .serverCreateVersion(updatedArtifactContent, ContentTypes.APPLICATION_JSON);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI").body(createVersion)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(200)
                .body("version", equalTo("2")).body("artifactType", equalTo(ArtifactType.OPENAPI));

        // Get the artifact content (should be the updated content)
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API (Updated)"));

        // Try to create a new version of an artifact that doesn't exist.
        createVersion = TestUtils.serverCreateVersion(updatedArtifactContent, ContentTypes.APPLICATION_JSON);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testCreateArtifactVersion/MissingAPI").body(createVersion)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(404);

        // Try to create a new version of the artifact with empty content
        createVersion = TestUtils.serverCreateVersion("", ContentTypes.APPLICATION_JSON);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI").body(createVersion)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(400);

        // Create another new version of the artifact with a custom version #
        createVersion = TestUtils.serverCreateVersion(updatedArtifactContent, ContentTypes.APPLICATION_JSON);
        createVersion.setVersion("3.0.0.Final");
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI").body(createVersion)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(200)
                .body("version", equalTo("3.0.0.Final")).body("artifactType", equalTo(ArtifactType.OPENAPI));

        // Create another new version of the artifact with a custom name
        String customName = "CUSTOM NAME";
        createVersion = TestUtils.serverCreateVersion(updatedArtifactContent, ContentTypes.APPLICATION_JSON);
        createVersion.setName(customName);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).header("X-Registry-Name", customName)
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI").body(createVersion)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(200)
                .body("name", equalTo(customName));

        // Create another new version of the artifact with a custom description
        String customDescription = "CUSTOM DESCRIPTION";
        createVersion = TestUtils.serverCreateVersion(updatedArtifactContent, ContentTypes.APPLICATION_JSON);
        createVersion.setDescription(customDescription);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .header("X-Registry-Description", customDescription)
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI").body(createVersion)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(200)
                .body("description", equalTo(customDescription));

    }

    @Test
    @DisabledIfEnvironmentVariable(named = CURRENT_ENV, matches = CURRENT_ENV_MAS_REGEX)
    @DisabledOnOs(OS.WINDOWS)
    public void testCreateArtifactVersionNoAscii() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testCreateArtifactVersionNoAscii/EmptyAPI", ArtifactType.OPENAPI,
                artifactContent, ContentTypes.APPLICATION_JSON);

        // Create another new version of the artifact with a custom No-ASCII name and description
        String customNameNoASCII = "CUSTOM NAME WITH NO-ASCII CHAR ě";
        String customDescriptionNoASCII = "CUSTOM DESCRIPTION WITH NO-ASCII CHAR ě";

        io.apicurio.registry.rest.v3.beans.CreateVersion createVersion = TestUtils
                .serverCreateVersion(updatedArtifactContent, ContentTypes.APPLICATION_JSON);
        createVersion.setName(customNameNoASCII);
        createVersion.setDescription(customDescriptionNoASCII);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testCreateArtifactVersionNoAscii/EmptyAPI").body(createVersion)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(200)
                .body("name", equalTo(customNameNoASCII))
                .body("description", equalTo(customDescriptionNoASCII));

        // Get artifact metadata (should have the custom name and description)
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testCreateArtifactVersionNoAscii/EmptyAPI")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest").then()
                .statusCode(200).body("name", equalTo(customNameNoASCII))
                .body("description", equalTo(customDescriptionNoASCII));
    }

    @Test
    public void testCreateArtifactVersionValidityRuleViolation() throws Exception {
        String artifactContent = resourceToString("jsonschema-valid.json");
        String artifactContentInvalidSyntax = resourceToString("jsonschema-invalid.json");
        String artifactId = "testCreateArtifact/ValidityRuleViolation";
        createArtifact(GROUP, artifactId, ArtifactType.JSON, artifactContent, ContentTypes.APPLICATION_JSON);

        // Add a rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("FULL");
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createRule)
                .pathParam("artifactId", artifactId)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(204)
                .body(anything());

        // Verify the rule was added
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/VALIDITY").then()
                .statusCode(200).contentType(ContentType.JSON).body("ruleType", equalTo("VALIDITY"))
                .body("config", equalTo("FULL"));

        // Create a new version of the artifact with invalid syntax
        io.apicurio.registry.rest.v3.beans.CreateVersion createVersion = TestUtils
                .serverCreateVersion(artifactContentInvalidSyntax, ContentTypes.APPLICATION_JSON);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(createVersion).post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions")
                .then().statusCode(409).body("status", equalTo(409))
                .body("title", startsWith("Syntax or semantic violation for JSON Schema artifact."));
    }

    @Test
    public void testCreateArtifactVersionCompatibilityRuleViolation() throws Exception {
        String artifactContent = resourceToString("jsonschema-valid.json");
        String artifactContentInvalidSyntax = resourceToString("jsonschema-valid-incompatible.json");
        String artifactId = "testCreateArtifact/ValidJson";
        createArtifact(GROUP, artifactId, ArtifactType.JSON, artifactContent, ContentTypes.APPLICATION_JSON);

        // Add a rule
        CreateRule rule = new CreateRule();
        rule.setRuleType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(rule).post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then()
                .statusCode(204).body(anything());

        // Verify the rule was added
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/COMPATIBILITY").then()
                .statusCode(200).contentType(ContentType.JSON).body("ruleType", equalTo("COMPATIBILITY"))
                .body("config", equalTo("BACKWARD"));

        // Create a new version of the artifact with invalid syntax
        io.apicurio.registry.rest.v3.beans.CreateVersion createVersion = TestUtils
                .serverCreateVersion(artifactContentInvalidSyntax, ContentTypes.APPLICATION_JSON);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(createVersion).post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions")
                .then().statusCode(409).body("status", equalTo(409))
                .body("title", startsWith("Incompatible artifact: testCreateArtifact/ValidJson [JSON], num"
                        + " of incompatible diffs: {1}, list of diff types: [SUBSCHEMA_TYPE_CHANGED at /properties/age]"))
                .body("causes[0].description", equalTo(DiffType.SUBSCHEMA_TYPE_CHANGED.getDescription()))
                .body("causes[0].context", equalTo("/properties/age"));

    }

    @Test
    public void testGetArtifactVersion() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create an artifact
        createArtifact(GROUP, "testGetArtifactVersion/EmptyAPI", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Update the artifact 5 times
        List<String> versions = new ArrayList<>();
        for (int idx = 0; idx < 5; idx++) {
            io.apicurio.registry.rest.v3.beans.CreateVersion createVersion = TestUtils.serverCreateVersion(
                    artifactContent.replace("Empty API", "Empty API (Update " + idx + ")"),
                    ContentTypes.APPLICATION_JSON);
            String version = given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                    .pathParam("artifactId", "testGetArtifactVersion/EmptyAPI").body(createVersion)
                    .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then()
                    .statusCode(200).body("artifactId", equalTo("testGetArtifactVersion/EmptyAPI"))
                    .body("artifactType", equalTo(ArtifactType.OPENAPI)).extract().body().path("version");
            versions.add(version);
        }

        // Now get each version of the artifact
        for (int idx = 0; idx < 5; idx++) {
            String version = versions.get(idx);
            String expected = "Empty API (Update " + idx + ")";
            given().when().pathParam("groupId", GROUP)
                    .pathParam("artifactId", "testGetArtifactVersion/EmptyAPI").pathParam("version", version)
                    .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/content")
                    .then().statusCode(200).body("info.title", equalTo(expected));
        }

        // Now get a version that doesn't exist.
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", "testGetArtifactVersion/EmptyAPI")
                .pathParam("version", 12345)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}").then()
                .statusCode(404);

        // Now get a version of an artifact that doesn't exist.
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactVersion/MissingAPI").pathParam("version", "1")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}").then()
                .statusCode(404);
    }

    @Test
    public void testArtifactRules() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String artifactId = "testArtifactRules/EmptyAPI";

        // Create an artifact
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Add a rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("FULL");
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createRule)
                .pathParam("artifactId", artifactId)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(204)
                .body(anything());

        // Verify the rule was added
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/VALIDITY").then()
                .statusCode(200).contentType(ContentType.JSON).body("ruleType", equalTo("VALIDITY"))
                .body("config", equalTo("FULL"));

        // Try to add the rule again - should get a 409
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(createRule).post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then()
                .statusCode(409).body("status", equalTo(409))
                .body("title", equalTo("A rule named 'VALIDITY' already exists."));

        // Add another rule
        createRule.setRuleType(RuleType.COMPATIBILITY);
        createRule.setConfig("BACKWARD");
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(createRule).post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then()
                .statusCode(204).body(anything());

        // Verify the rule was added
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/COMPATIBILITY").then()
                .statusCode(200).contentType(ContentType.JSON).body("ruleType", equalTo("COMPATIBILITY"))
                .body("config", equalTo("BACKWARD"));

        // Get the list of rules (should be 2 of them)
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(200)
                .contentType(ContentType.JSON)
                .body("[0]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY")))
                .body("[1]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY"))).body("[2]", nullValue());

        // Update a rule's config
        Rule updateRule = new Rule();
        updateRule.setRuleType(RuleType.COMPATIBILITY);
        updateRule.setConfig("FULL");
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(updateRule)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/COMPATIBILITY").then()
                .statusCode(200).contentType(ContentType.JSON).body("ruleType", equalTo("COMPATIBILITY"))
                .body("config", equalTo("FULL"));

        // Get a single (updated) rule by name
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/COMPATIBILITY").then()
                .statusCode(200).contentType(ContentType.JSON).body("ruleType", equalTo("COMPATIBILITY"))
                .body("config", equalTo("FULL"));

        // Delete a rule
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .delete("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/COMPATIBILITY").then()
                .statusCode(204).body(anything());

        // Get a single (deleted) rule by name (should fail with a 404)
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/COMPATIBILITY").then()
                .statusCode(404).contentType(ContentType.JSON).body("status", equalTo(404))
                .body("title", equalTo("No rule named 'COMPATIBILITY' was found."));

        // Get the list of rules (should be 1 of them)
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then()
                // .log().all()
                .statusCode(200).contentType(ContentType.JSON)
                .body("[0]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY"))).body("[1]", nullValue());

        // Delete all rules
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .delete("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(204);

        // Get the list of rules (no rules now)
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(200)
                .contentType(ContentType.JSON).body("[0]", nullValue());

        // Add a rule to an artifact that doesn't exist.
        createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("FULL");
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "MissingArtifact").body(createRule)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(404)
                .body(anything());
    }

    @Test
    public void testDeleteAllArtifactRules() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String artifactId = "testDeleteAllArtifactRules/EmptyAPI";

        // Create an artifact
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Add the Validity rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("FULL");
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createRule)
                .pathParam("artifactId", artifactId)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(204)
                .body(anything());

        // Add the Integrity rule
        createRule.setRuleType(RuleType.INTEGRITY);
        createRule.setConfig(IntegrityLevel.NO_DUPLICATES.name());
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createRule)
                .pathParam("artifactId", artifactId)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(204)
                .body(anything());

        // Verify the rules were added
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/VALIDITY").then()
                .statusCode(200).contentType(ContentType.JSON).body("ruleType", equalTo("VALIDITY"))
                .body("config", equalTo("FULL"));
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/INTEGRITY").then()
                .statusCode(200).contentType(ContentType.JSON).body("ruleType", equalTo("INTEGRITY"))
                .body("config", equalTo("NO_DUPLICATES"));

        // Get the list of rules (should be 2 of them)
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(200)
                .contentType(ContentType.JSON).body("[0]", anyOf(equalTo("VALIDITY"), equalTo("INTEGRITY")))
                .body("[1]", anyOf(equalTo("VALIDITY"), equalTo("INTEGRITY"))).body("[2]", nullValue());

        // Delete all rules
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .delete("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(204);

        // Make sure the rules were deleted
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/VALIDITY").then()
                .statusCode(404).contentType(ContentType.JSON).body("status", equalTo(404))
                .body("title", equalTo("No rule named 'VALIDITY' was found."));
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/INTEGRITY").then()
                .statusCode(404).contentType(ContentType.JSON).body("status", equalTo(404))
                .body("title", equalTo("No rule named 'INTEGRITY' was found."));

        // Get the list of rules (no rules now)
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(200)
                .contentType(ContentType.JSON).body("[0]", nullValue());
    }

    @Test
    public void testArtifactMetaData() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testGetArtifactMetaData/EmptyAPI", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON, (ca) -> {
                    ca.setName("Empty API");
                    ca.setDescription("An example API design using OpenAPI.");
                });

        // Get the artifact meta-data
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}").then().statusCode(200)
                .body("artifactId", equalTo("testGetArtifactMetaData/EmptyAPI")).body("version", anything())
                .body("artifactType", equalTo(ArtifactType.OPENAPI)).body("createdOn", anything())
                .body("name", equalTo("Empty API"))
                .body("description", equalTo("An example API design using OpenAPI.")).extract()
                .as(ArtifactMetaData.class);

        // Try to get artifact meta-data for an artifact that doesn't exist.
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactMetaData/MissingAPI")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}").then().statusCode(404)
                .body("status", equalTo(404)).body("title", equalTo(
                        "No artifact with ID 'testGetArtifactMetaData/MissingAPI' in group 'GroupsResourceTest' was found."));

        // Update the artifact meta-data
        EditableArtifactMetaData amd = EditableArtifactMetaData.builder().name("Empty API Name")
                .description("Empty API description.")
                .labels(Map.of("additionalProp1", "Empty API additional property")).build();
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI").body(amd)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}").then().statusCode(204);

        // Get the (updated) artifact meta-data
        Map<String, String> expectedLabels = new HashMap<>();
        expectedLabels.put("additionalProp1", "Empty API additional property");

        given().when().pathParam("groupId", GROUP).pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}").then().statusCode(200)
                .body("artifactId", equalTo("testGetArtifactMetaData/EmptyAPI"))
                .body("name", equalTo("Empty API Name"))
                .body("description", equalTo("Empty API description."))
                .body("labels", equalToObject(expectedLabels));

        // Update the artifact content (new version) and then make sure the name/description meta-data is
        // still available
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");
        io.apicurio.registry.rest.v3.beans.CreateVersion createVersion = TestUtils
                .serverCreateVersion(updatedArtifactContent, ContentTypes.APPLICATION_JSON);
        given().when().contentType(CT_JSON).header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("groupId", GROUP).pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .body(createVersion).post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions")
                .then().statusCode(200).body("artifactId", equalTo("testGetArtifactMetaData/EmptyAPI"))
                .body("artifactType", equalTo(ArtifactType.OPENAPI));

        // Verify the artifact meta-data name and description are still set.
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}").then().statusCode(200)
                .body("artifactId", equalTo("testGetArtifactMetaData/EmptyAPI")).body("version", anything())
                .body("name", equalTo("Empty API Name"))
                .body("description", equalTo("Empty API description."));
    }

    @Test
    public void testLabelWithNullValue() throws Exception {
        String group = UUID.randomUUID().toString();
        String artifactContent = resourceToString("openapi-empty.json");

        int idx = 0;
        String title = "Empty API " + idx;
        String artifactId = "Empty-" + idx;
        this.createArtifact(group, artifactId, ArtifactType.OPENAPI,
                artifactContent.replaceAll("Empty API", title), ContentTypes.APPLICATION_JSON);

        Map<String, String> labels = new HashMap<>();
        labels.put("test-key", null);

        // Update the artifact meta-data
        EditableArtifactMetaData metaData = new EditableArtifactMetaData();
        metaData.setName(title);
        metaData.setDescription("Some description of an API");
        metaData.setLabels(labels);
        given().when().contentType(CT_JSON).pathParam("groupId", group).pathParam("artifactId", artifactId)
                .body(metaData).put("/registry/v3/groups/{groupId}/artifacts/{artifactId}").then()
                .statusCode(204);

        // Get the (updated) artifact meta-data
        Map<String, String> expectedLabels = new HashMap<>();
        expectedLabels.put("test-key", null);
        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}").then().statusCode(200)
                .body("artifactId", equalTo(artifactId)).body("version", anything())
                .body("labels", equalToObject(expectedLabels));

    }

    @Test
    public void testArtifactVersionMetaData() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent_v2 = artifactContent.replace("Empty API", "Empty API (VERSION 2)");
        String updatedArtifactContent_v3 = artifactContent.replace("Empty API", "Empty API (VERSION 3)");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testArtifactVersionMetaData/EmptyAPI", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Create a new version of the artifact
        io.apicurio.registry.rest.v3.beans.CreateVersion createVersion_v2 = TestUtils
                .serverCreateVersion(updatedArtifactContent_v2, ContentTypes.APPLICATION_JSON);
        createVersion_v2.setName("Empty API (VERSION 2)");
        createVersion_v2.setDescription("An example API design using OpenAPI.");
        String version2 = given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI").body(createVersion_v2)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(200)
                .body("version", notNullValue()).body("artifactType", equalTo(ArtifactType.OPENAPI)).extract()
                .body().path("version");

        // Create another new version of the artifact
        io.apicurio.registry.rest.v3.beans.CreateVersion createVersion_v3 = TestUtils
                .serverCreateVersion(updatedArtifactContent_v3, ContentTypes.APPLICATION_JSON);
        createVersion_v3.setName("Empty API (VERSION 3)");
        createVersion_v3.setDescription("An example API design using OpenAPI.");
        String version3 = given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI").body(createVersion_v3)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(200)
                .body("version", notNullValue()).body("artifactType", equalTo(ArtifactType.OPENAPI)).extract()
                .body().path("version");

        // Get meta-data for v2
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .pathParam("version", version2)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}").then()
                .statusCode(200).body("version", equalTo(version2))
                .body("artifactType", equalTo(ArtifactType.OPENAPI)).body("createdOn", anything())
                .body("name", equalTo("Empty API (VERSION 2)"))
                .body("description", equalTo("An example API design using OpenAPI.")).extract()
                .as(VersionMetaData.class);

        // Update the version meta-data
        String metaData = "{\"name\": \"Updated Name\", \"description\": \"Updated description.\"}";
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .pathParam("version", version2).body(metaData)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}").then()
                .statusCode(204);

        // Get the (updated) artifact meta-data
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .pathParam("version", version2)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}").then()
                .statusCode(200).body("version", equalTo(version2))
                .body("artifactType", equalTo(ArtifactType.OPENAPI)).body("createdOn", anything())
                .body("name", equalTo("Updated Name")).body("description", equalTo("Updated description."));

        // Get the version meta-data for the version we **didn't** update
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .pathParam("version", version3)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}").then()
                .statusCode(200).body("version", equalTo(version3))
                .body("artifactType", equalTo(ArtifactType.OPENAPI)).body("createdOn", anything())
                .body("name", equalTo("Empty API (VERSION 3)"))
                .body("description", equalTo("An example API design using OpenAPI."));

        // Get the version meta-data for a non-existent version
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI").pathParam("version", 12345)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}").then()
                .statusCode(404);

    }

    @Test
    public void testYamlContentType() throws Exception {
        String artifactId = "testYamlContentType";
        String artifactType = ArtifactType.OPENAPI;
        String artifactContent = resourceToString("openapi-empty.yaml");

        // Create OpenAPI artifact (from YAML)
        io.apicurio.registry.rest.v3.beans.CreateArtifact createArtifact = TestUtils.serverCreateArtifact(
                artifactId, ArtifactType.OPENAPI, artifactContent, ContentTypes.APPLICATION_YAML);
        createArtifact.setName("Empty API");
        createArtifact.setDescription("An example API design using OpenAPI.");
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("artifact.artifactId", equalTo(artifactId)).body("artifact.name", equalTo("Empty API"))
                .body("artifact.description", equalTo("An example API design using OpenAPI."))
                .body("artifact.artifactType", equalTo(artifactType));

        // Get the artifact content (should still be YAML)
        RestAssured.registerParser("application/x-yaml", Parser.JSON);
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", "testYamlContentType")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).header("Content-Type", Matchers.containsString(CT_YAML));
    }

    @Test
    public void testWsdlArtifact() throws Exception {
        String artifactId = "testWsdlArtifact";
        String artifactType = ArtifactType.WSDL;
        String artifactContent = resourceToString("sample.wsdl");

        // Create WSDL artifact
        io.apicurio.registry.rest.v3.beans.CreateArtifact createArtifact = TestUtils.serverCreateArtifact(
                artifactId, ArtifactType.WSDL, artifactContent, ContentTypes.APPLICATION_XML);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("artifact.artifactId", equalTo(artifactId))
                .body("artifact.artifactType", equalTo(artifactType));

        // Get the artifact content (should be XML)
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).header("Content-Type", Matchers.containsString(CT_XML));
    }

    @Test
    public void testCreateAlreadyExistingArtifact() throws Exception {
        final String artifactId = UUID.randomUUID().toString();
        final String artifactContent = resourceToString("openapi-empty.json");
        final String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");
        final String v3ArtifactContent = artifactContent.replace("Empty API", "Empty API (Version 3)");
        final String artifactName = "ArtifactNameFromHeader";
        final String artifactDescription = "ArtifactDescriptionFromHeader";

        // Create OpenAPI artifact - indicate the type via a header param
        Long globalId1 = createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON).getVersion().getGlobalId();

        // Try to create the same artifact ID (should fail)
        io.apicurio.registry.rest.v3.beans.CreateArtifact createArtifact = TestUtils.serverCreateArtifact(
                artifactId, ArtifactType.OPENAPI, artifactContent, ContentTypes.APPLICATION_JSON);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(409)
                .body("status", equalTo(409)).body("title", equalTo("An artifact with ID '" + artifactId
                        + "' in group 'GroupsResourceTest' already exists."));

        // Try to create the same artifact ID with FIND_OR_CREATE_VERSION for if exists (should return same
        // artifact)
        createArtifact = TestUtils.serverCreateArtifact(artifactId, ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .queryParam("ifExists", IfArtifactExists.FIND_OR_CREATE_VERSION).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("artifact.artifactType", equalTo(ArtifactType.OPENAPI))
                .body("version.version", equalTo("1")).body("artifact.createdOn", anything());

        // Try to create the same artifact ID with CREATE_VERSION for if exists (should create a new version)
        createArtifact = TestUtils.serverCreateArtifact(artifactId, ArtifactType.OPENAPI,
                updatedArtifactContent, ContentTypes.APPLICATION_JSON);
        ValidatableResponse resp = given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .queryParam("ifExists", IfArtifactExists.CREATE_VERSION).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("artifact.artifactType", equalTo(ArtifactType.OPENAPI))
                .body("artifact.createdOn", anything()).body("version.version", equalTo("2"));
        /* Integer globalId2 = */
        resp.extract().body().path("globalId");

        // Try to create the same artifact ID with FIND_OR_CREATE_VERSION - should return v1 (matching
        // content)
        createArtifact = TestUtils.serverCreateArtifact(artifactId, ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);
        resp = given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .queryParam("ifExists", IfArtifactExists.FIND_OR_CREATE_VERSION).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("artifact.artifactType", equalTo(ArtifactType.OPENAPI));

        Integer globalId3 = resp.extract().body().path("version.globalId");

        assertEquals(globalId1, globalId3.longValue());

        // Try to create the same artifact ID with FIND_OR_CREATE_VERSION and updated content - should create
        // a new version
        // and use name and description from headers
        createArtifact = TestUtils.serverCreateArtifact(artifactId, ArtifactType.OPENAPI, v3ArtifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setName(artifactName);
        createArtifact.getFirstVersion().setDescription(artifactDescription);
        resp = given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .queryParam("ifExists", IfArtifactExists.FIND_OR_CREATE_VERSION).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("version.version", equalTo("3")).body("version.name", equalTo(artifactName))
                .body("version.description", equalTo(artifactDescription))
                .body("artifact.artifactType", equalTo(ArtifactType.OPENAPI));
    }

    @Test
    public void testDeleteArtifactWithRule() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String artifactId = "testDeleteArtifactWithRule/EmptyAPI";

        // Create an artifact
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Add a rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("FULL");
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(createRule).post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then()
                .statusCode(204).body(anything());

        // Get a single rule by name
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/VALIDITY").then()
                .statusCode(200).contentType(ContentType.JSON).body("ruleType", equalTo("VALIDITY"))
                .body("config", equalTo("FULL"));

        // Delete the artifact
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .delete("/registry/v3/groups/{groupId}/artifacts/{artifactId}").then().statusCode(204);

        // Get a single rule by name (should be 404 because the artifact is gone)
        // Also try to get the artifact itself (should be 404)
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/VALIDITY").then()
                .statusCode(404);
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}").then().statusCode(404);

        // Re-create the artifact
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Get a single rule by name (should be 404 because the artifact is gone)
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/VALIDITY").then()
                .statusCode(404);

        // Add the same rule - should work because the old rule was deleted when the artifact was deleted.
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("FULL");
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(createRule).post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then()
                .statusCode(204).body(anything());
    }

    @Test
    public void testCustomArtifactVersion() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        String groupId = "testCustomArtifactVersion";
        String artifactId = "MyVersionedAPI";

        // Create OpenAPI artifact version 1.0.0
        io.apicurio.registry.rest.v3.beans.CreateArtifact createArtifact = TestUtils.serverCreateArtifact(
                artifactId, ArtifactType.OPENAPI, artifactContent, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setVersion("1.0.0");
        given().when().contentType(CT_JSON).pathParam("groupId", groupId).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("artifact.artifactId", equalTo(artifactId)).body("artifact.groupId", equalTo(groupId))
                .body("version.version", equalTo("1.0.0"));

        // Make sure we can get the artifact content by version
        given().when().pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                .pathParam("version", "1.0.0")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/content").then()
                .statusCode(200).body("openapi", equalTo("3.0.2")).body("info.title", equalTo("Empty API"));

        // Make sure we can get the artifact meta-data by version
        given().when().pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                .pathParam("version", "1.0.0")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}").then()
                .statusCode(200).body("artifactId", equalTo(artifactId)).body("groupId", equalTo(groupId))
                .body("version", equalTo("1.0.0"));

        // Add version 1.0.1
        String updatedContent = artifactContent.replace("Empty API", "Empty API (Version 1.0.1)");
        io.apicurio.registry.rest.v3.beans.CreateVersion createVersion = TestUtils
                .serverCreateVersion(updatedContent, ContentTypes.APPLICATION_JSON);
        createVersion.setVersion("1.0.1");
        given().when().contentType(CT_JSON).pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                .body(createVersion).post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions")
                .then().statusCode(200).body("artifactId", equalTo(artifactId))
                .body("version", equalTo("1.0.1")).body("artifactType", equalTo(ArtifactType.OPENAPI));

        // List the artifact versions
        given().when().pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(200)
                .body("count", equalTo(2)).body("versions[0].version", equalTo("1.0.0"))
                .body("versions[1].version", equalTo("1.0.1"));

        // Add version 1.0.2
        updatedContent = artifactContent.replace("Empty API", "Empty API (Version 1.0.2)");
        createVersion = TestUtils.serverCreateVersion(updatedContent, ContentTypes.APPLICATION_JSON);
        createVersion.setVersion("1.0.2");
        given().when().contentType(CT_JSON).pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                .body(createVersion).post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions")
                .then().statusCode(200).body("artifactId", equalTo(artifactId))
                .body("version", equalTo("1.0.2")).body("artifactType", equalTo(ArtifactType.OPENAPI));

        // List the artifact versions
        given().when().pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then().statusCode(200)
                .body("count", equalTo(3)).body("versions[0].version", equalTo("1.0.0"))
                .body("versions[1].version", equalTo("1.0.1")).body("versions[2].version", equalTo("1.0.2"));

    }

    @Test
    public void testCreateArtifactAfterDelete() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact - indicate the type via a header param
        createArtifact(GROUP, "testCreateArtifactAfterDelete/EmptyAPI", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Delete the artifact
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testCreateArtifactAfterDelete/EmptyAPI")
                .delete("/registry/v3/groups/{groupId}/artifacts/{artifactId}").then().statusCode(204);

        // Create the same artifact
        createArtifact(GROUP, "testCreateArtifactAfterDelete/EmptyAPI", ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);
    }

    @Test
    void testArtifactWithReferences() throws Exception {
        String artifactContent = getRandomValidJsonSchemaContent();

        // Create #1 without references
        var metadata = createArtifactExtendedRaw(GroupId.DEFAULT.getRawGroupIdWithDefaultString(), null,
                ArtifactType.JSON, artifactContent, ContentTypes.APPLICATION_JSON, List.of()).getVersion();
        // Save the metadata for artifact #1 for later use
        var referencedMD = metadata;

        // Create #2 referencing the #1, using different content
        List<ArtifactReference> references = List.of(ArtifactReference.builder()
                .groupId(metadata.getGroupId()).artifactId(metadata.getArtifactId())
                .version(metadata.getVersion()).name("foo").build());
        artifactContent = getRandomValidJsonSchemaContent();

        metadata = createArtifactExtendedRaw(GroupId.DEFAULT.getRawGroupIdWithDefaultString(), null,
                ArtifactType.JSON, artifactContent, ContentTypes.APPLICATION_JSON, references).getVersion();

        // Save the referencing artifact metadata for later use
        var referencingMD = metadata;

        var actualReferences = clientV3.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString())
                .artifacts().byArtifactId(metadata.getArtifactId()).versions()
                .byVersionExpression(metadata.getVersion()).references().get();
        assertEquals(references.size(), actualReferences.size());
        assertEquals(references.get(0).getName(), actualReferences.get(0).getName());
        assertEquals(references.get(0).getVersion(), actualReferences.get(0).getVersion());
        assertEquals(references.get(0).getArtifactId(), actualReferences.get(0).getArtifactId());
        assertEquals(references.get(0).getGroupId(), actualReferences.get(0).getGroupId());

        // Trying to use different references with the same content is ok, but the contentId and contentHash
        // is different.
        List<ArtifactReference> references2 = List.of(ArtifactReference.builder()
                .groupId(metadata.getGroupId()).artifactId(metadata.getArtifactId())
                .version(metadata.getVersion()).name("foo2").build());

        var secondMetadata = createArtifactExtendedRaw(GroupId.DEFAULT.getRawGroupIdWithDefaultString(), null,
                ArtifactType.JSON, artifactContent, ContentTypes.APPLICATION_JSON, references2).getVersion();

        assertNotEquals(secondMetadata.getContentId(), metadata.getContentId());

        // Same references are not an issue
        metadata = createArtifactExtendedRaw("default2", null, ArtifactType.JSON, artifactContent,
                ContentTypes.APPLICATION_JSON, references).getVersion();

        // Get references via globalId
        var referenceResponse = given().when().pathParam("globalId", metadata.getGlobalId())
                .get("/registry/v3/ids/globalIds/{globalId}/references").then().statusCode(HTTP_OK).extract()
                .as(new TypeRef<List<ArtifactReference>>() {
                });

        assertEquals(references, referenceResponse);

        // Get references via contentId
        referenceResponse = given().when().pathParam("contentId", metadata.getContentId())
                .get("/registry/v3/ids/contentIds/{contentId}/references").then().statusCode(HTTP_OK)
                .extract().as(new TypeRef<List<ArtifactReference>>() {
                });

        final String referencesSerialized = RegistryContentUtils
                .serializeReferences(toReferenceDtos(references));

        // We calculate the hash using the content itself and the references
        String contentHash = DigestUtils
                .sha256Hex(concatContentAndReferences(artifactContent.getBytes(StandardCharsets.UTF_8),
                        referencesSerialized.getBytes(StandardCharsets.UTF_8)));

        assertEquals(references, referenceResponse);

        // Get references via contentHash
        referenceResponse = given().when().pathParam("contentHash", contentHash)
                .get("/registry/v3/ids/contentHashes/{contentHash}/references").then().statusCode(HTTP_OK)
                .extract().as(new TypeRef<List<ArtifactReference>>() {
                });

        assertEquals(references, referenceResponse);

        // Get references via GAV
        referenceResponse = given().when().pathParam("groupId", metadata.getGroupId())
                .pathParam("artifactId", metadata.getArtifactId()).pathParam("version", metadata.getVersion())
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/references")
                .then().statusCode(HTTP_OK).extract().as(new TypeRef<List<ArtifactReference>>() {
                });

        assertEquals(references, referenceResponse);

        // Get INBOUND references via GAV
        referenceResponse = given().when()
                .pathParam("groupId", new GroupId(referencedMD.getGroupId()).getRawGroupIdWithDefaultString())
                .pathParam("artifactId", referencedMD.getArtifactId())
                .pathParam("version", referencedMD.getVersion()).queryParam("refType", ReferenceType.INBOUND)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/references")
                .then().statusCode(HTTP_OK).extract().as(new TypeRef<List<ArtifactReference>>() {
                });
        assertFalse(referenceResponse.isEmpty());
        assertEquals(2, referenceResponse.size());
        assertEquals(referencingMD.getGroupId(), referenceResponse.get(0).getGroupId());
        assertEquals(referencingMD.getArtifactId(), referenceResponse.get(0).getArtifactId());
        assertEquals(referencingMD.getVersion(), referenceResponse.get(0).getVersion());

        // Get INBOUND references via globalId
        referenceResponse = given().when().pathParam("globalId", referencedMD.getGlobalId())
                .queryParam("refType", ReferenceType.INBOUND)
                .get("/registry/v3/ids/globalIds/{globalId}/references").then().statusCode(HTTP_OK).extract()
                .as(new TypeRef<List<ArtifactReference>>() {
                });
        assertFalse(referenceResponse.isEmpty());
        assertEquals(2, referenceResponse.size());
        assertEquals(referencingMD.getGroupId(), referenceResponse.get(0).getGroupId());
        assertEquals(referencingMD.getArtifactId(), referenceResponse.get(0).getArtifactId());
        assertEquals(referencingMD.getVersion(), referenceResponse.get(0).getVersion());
    }

    private byte[] concatContentAndReferences(byte[] contentBytes, byte[] referencesBytes)
            throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(
                contentBytes.length + referencesBytes.length);
        outputStream.write(contentBytes);
        outputStream.write(referencesBytes);
        return outputStream.toByteArray();
    }

    @Test
    public void testArtifactComments() throws Exception {
        String artifactId = "testArtifactComments/EmptyAPI";
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Get comments for the artifact (should be none)
        List<Comment> comments = given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/latest/comments").then()
                .statusCode(HTTP_OK).extract().as(new TypeRef<List<Comment>>() {
                });
        assertEquals(0, comments.size());

        // Create a new comment
        NewComment nc = NewComment.builder().value("COMMENT_1").build();
        Comment comment1 = given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId).body(nc)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/comments")
                .then().statusCode(HTTP_OK).extract().as(Comment.class);
        assertNotNull(comment1);
        assertNotNull(comment1.getCommentId());
        assertNotNull(comment1.getValue());
        assertNotNull(comment1.getCreatedOn());
        assertEquals("COMMENT_1", comment1.getValue());

        // Create another new comment
        nc = NewComment.builder().value("COMMENT_2").build();
        Comment comment2 = given().when().contentType(CT_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId).body(nc)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/comments")
                .then().statusCode(HTTP_OK).extract().as(Comment.class);
        assertNotNull(comment2);
        assertNotNull(comment2.getCommentId());
        assertNotNull(comment2.getValue());
        assertNotNull(comment2.getCreatedOn());
        assertEquals("COMMENT_2", comment2.getValue());

        // Get the list of comments (should have 2)
        comments = given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/comments")
                .then().statusCode(HTTP_OK).extract().as(new TypeRef<List<Comment>>() {
                });
        assertEquals(2, comments.size());
        assertEquals("COMMENT_2", comments.get(0).getValue());
        assertEquals("COMMENT_1", comments.get(1).getValue());

        // Update a comment
        nc = NewComment.builder().value("COMMENT_2_UPDATED").build();
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .pathParam("commentId", comment2.getCommentId()).body(nc)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/comments/{commentId}")
                .then().statusCode(HTTP_NO_CONTENT);

        // Get the list of comments (should have 2)
        comments = given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/comments")
                .then().statusCode(HTTP_OK).extract().as(new TypeRef<List<Comment>>() {
                });
        assertEquals(2, comments.size());
        assertEquals("COMMENT_2_UPDATED", comments.get(0).getValue());
        assertEquals("COMMENT_1", comments.get(1).getValue());

        // Delete a comment
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .pathParam("commentId", comment2.getCommentId())
                .delete("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/comments/{commentId}")
                .then().statusCode(HTTP_NO_CONTENT);

        // Get the list of comments (should have only 1)
        comments = given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/comments")
                .then().statusCode(HTTP_OK).extract().as(new TypeRef<List<Comment>>() {
                });
        assertEquals(1, comments.size());
        assertEquals("COMMENT_1", comments.get(0).getValue());
    }

    @Test
    public void testCreateArtifactIntegrityRuleViolation() throws Exception {
        String artifactContent = resourceToString("jsonschema-valid.json");
        String artifactId = "testCreateArtifact/IntegrityRuleViolation";
        createArtifact(GROUP, artifactId, ArtifactType.JSON, artifactContent, ContentTypes.APPLICATION_JSON);

        // Enable the Integrity rule for the artifact
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.INTEGRITY);
        createRule.setConfig("FULL");
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createRule)
                .pathParam("artifactId", artifactId)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(204)
                .body(anything());

        // Verify the rule was added
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/INTEGRITY").then()
                .statusCode(200).contentType(ContentType.JSON).body("ruleType", equalTo("INTEGRITY"))
                .body("config", equalTo("FULL"));

        // Now try registering an artifact with a valid reference
        var reference = new io.apicurio.registry.rest.client.models.ArtifactReference();
        reference.setVersion("1");
        reference.setGroupId(GROUP);
        reference.setArtifactId(artifactId);
        reference.setName("other.json#/defs/Foo");

        CreateVersion createVersion = TestUtils.clientCreateVersion(artifactContent,
                ContentTypes.APPLICATION_JSON);
        createVersion.setVersion("2");
        createVersion.getContent().setReferences(List.of(reference));

        clientV3.groups().byGroupId(GROUP).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);

        // Now try registering an artifact with an INVALID reference
        reference = new io.apicurio.registry.rest.client.models.ArtifactReference();
        reference.setGroupId(GROUP);
        reference.setArtifactId("ArtifactThatDoesNotExist");
        reference.setVersion("1");
        reference.setName("other.json#/defs/Foo");

        createVersion = TestUtils.clientCreateVersion(artifactContent, ContentTypes.APPLICATION_JSON);
        createVersion.setVersion("2");
        createVersion.getContent().setReferences(List.of(reference));
        CreateVersion f_createVersion = createVersion;

        var exception_1 = assertThrows(
                io.apicurio.registry.rest.client.models.RuleViolationProblemDetails.class, () -> {
                    clientV3.groups().byGroupId(GROUP).artifacts().byArtifactId(artifactId).versions()
                            .post(f_createVersion);
                });
        Assertions.assertEquals(409, exception_1.getStatus());
        Assertions.assertEquals("RuleViolationException", exception_1.getName());

        // Now try registering an artifact with both a valid and invalid ref
        // valid ref
        var validRef = new io.apicurio.registry.rest.client.models.ArtifactReference();
        validRef.setGroupId(GROUP);
        validRef.setArtifactId(artifactId);
        validRef.setVersion("1");
        validRef.setName("other.json#/defs/Foo");
        // invalid ref
        var invalidRef = new io.apicurio.registry.rest.client.models.ArtifactReference();
        invalidRef.setGroupId(GROUP);
        invalidRef.setArtifactId("ArtifactThatDoesNotExist");
        invalidRef.setVersion("1");
        invalidRef.setName("other.json#/defs/Foo");

        createVersion = TestUtils.clientCreateVersion(artifactContent, ContentTypes.APPLICATION_JSON);
        createVersion.setVersion("2");
        createVersion.getContent().setReferences(List.of(validRef, invalidRef));
        CreateVersion f_createVersion2 = createVersion;

        var exception_2 = assertThrows(
                io.apicurio.registry.rest.client.models.RuleViolationProblemDetails.class, () -> {
                    clientV3.groups().byGroupId(GROUP).artifacts().byArtifactId(artifactId).versions()
                            .post(f_createVersion2);
                });
        Assertions.assertEquals(409, exception_2.getStatus());
        Assertions.assertEquals("RuleViolationException", exception_2.getName());

        // Now try registering an artifact with a duplicate ref
        createVersion = TestUtils.clientCreateVersion(artifactContent, ContentTypes.APPLICATION_JSON);
        createVersion.setVersion("2");
        createVersion.getContent().setReferences(List.of(validRef, validRef));
        CreateVersion f_createVersion3 = createVersion;

        var exception_3 = assertThrows(
                io.apicurio.registry.rest.client.models.RuleViolationProblemDetails.class, () -> {
                    clientV3.groups().byGroupId(GROUP).artifacts().byArtifactId(artifactId).versions()
                            .post(f_createVersion3);
                });
        Assertions.assertEquals(409, exception_3.getStatus());
        Assertions.assertEquals("RuleViolationException", exception_3.getName());
    }

    @Test
    public void testGetArtifactVersionWithReferences() throws Exception {
        String referencedTypesContent = resourceToString("referenced-types.json");
        String withExternalRefContent = resourceToString("openapi-with-external-ref.json");

        // Create the artifact containing a type to be referenced
        createArtifact(GROUP, "testGetArtifactVersionWithReferences/ReferencedTypes", ArtifactType.OPENAPI,
                referencedTypesContent, ContentTypes.APPLICATION_JSON);

        // Create the artifact that references the type
        List<ArtifactReference> refs = Collections.singletonList(ArtifactReference.builder()
                .name("./referenced-types.json#/components/schemas/Widget").groupId(GROUP)
                .artifactId("testGetArtifactVersionWithReferences/ReferencedTypes").version("1").build());
        createArtifactWithReferences(GROUP, "testGetArtifactVersionWithReferences/WithExternalRef",
                ArtifactType.OPENAPI, withExternalRefContent, ContentTypes.APPLICATION_JSON, refs);

        // Get the content of the artifact preserving external references
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactVersionWithReferences/WithExternalRef")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                .body("paths.widgets.get.responses.200.content.json.schema.items.$ref",
                        equalTo("./referenced-types.json#/components/schemas/Widget"));

        // Get the content of the artifact rewriting external references
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactVersionWithReferences/WithExternalRef")
                .queryParam("references", "REWRITE")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                .body("paths.widgets.get.responses.200.content.json.schema.items.$ref", endsWith(
                        "/apis/registry/v3/groups/GroupsResourceTest/artifacts/testGetArtifactVersionWithReferences%2FReferencedTypes/versions/1?references=REWRITE#/components/schemas/Widget"));

        // Get the content of the artifact inlining/dereferencing external references
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactVersionWithReferences/WithExternalRef")
                .queryParam("references", "DEREFERENCE")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                .body("paths.widgets.get.responses.200.content.json.schema.items.$ref", equalTo(
                        "GroupsResourceTest:testGetArtifactVersionWithReferences/ReferencedTypes:1:./referenced-types.json#/components/schemas/Widget"));
    }

}
