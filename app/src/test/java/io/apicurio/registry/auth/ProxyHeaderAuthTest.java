package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.ProxyHeaderAuthTestProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test for proxy header authentication mechanism.
 *
 * This test validates that the ProxyHeaderAuthenticationMechanism correctly extracts
 * user identity from HTTP headers injected by a reverse proxy and creates appropriate
 * SecurityIdentity objects with roles.
 */
@QuarkusTest
@TestProfile(ProxyHeaderAuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class ProxyHeaderAuthTest extends AbstractResourceTestBase {

    private static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";
    private static final String HEADER_USERNAME = "X-Forwarded-User";
    private static final String HEADER_EMAIL = "X-Forwarded-Email";
    private static final String HEADER_GROUPS = "X-Forwarded-Groups";

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_EMAIL = "testuser@example.com";
    private static final String ADMIN_USERNAME = "adminuser";
    private static final String ADMIN_EMAIL = "adminuser@example.com";
    private static final String READONLY_USERNAME = "readonlyuser";
    private static final String READONLY_EMAIL = "readonlyuser@example.com";

    private static final String ROLE_ADMIN = "sr-admin";
    private static final String ROLE_DEVELOPER = "sr-developer";
    private static final String ROLE_READONLY = "sr-readonly";

    @Override
    protected void deleteGlobalRules(int expectedDefaultRulesCount) throws Exception {
        // No need for this in this test, and it wouldn't work anyway because it
        // would fail with a 401 (missing X-Forward headers)
    }

    /**
     * Test that requests without the required X-Forwarded-User header are rejected
     * in strict mode with a 401 Unauthorized response.
     */
    @Test
    public void testMissingHeadersStrictMode() {
        // Make request without any proxy headers - should fail with 401
        given()
            .when()
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(401);
    }

    /**
     * Test that authentication succeeds when valid proxy headers are present.
     */
    @Test
    public void testValidProxyHeaders() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create an artifact with valid proxy headers
        String response = given()
            .header(HEADER_USERNAME, TEST_USERNAME)
            .header(HEADER_EMAIL, TEST_EMAIL)
            .header(HEADER_GROUPS, ROLE_DEVELOPER)
            .contentType(ContentType.JSON)
            .body(TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON, ARTIFACT_CONTENT,
                    ContentTypes.APPLICATION_JSON))
            .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
            .then()
                .statusCode(200)
                .body("version.artifactId", equalTo(artifactId))
                .body("version.owner", equalTo(TEST_USERNAME))
            .extract().asString();

        assertNotNull(response);

        // Verify we can retrieve the artifact
        given()
            .header(HEADER_USERNAME, TEST_USERNAME)
            .header(HEADER_EMAIL, TEST_EMAIL)
            .header(HEADER_GROUPS, ROLE_DEVELOPER)
            .when()
                .pathParam("groupId", groupId)
                .get("/registry/v3/groups/{groupId}/artifacts/" + artifactId)
            .then()
                .statusCode(200)
                .body("artifactId", equalTo(artifactId))
                .body("owner", equalTo(TEST_USERNAME));

        // Clean up
        given()
            .header(HEADER_USERNAME, TEST_USERNAME)
            .header(HEADER_EMAIL, TEST_EMAIL)
            .header(HEADER_GROUPS, ROLE_DEVELOPER)
            .when()
                .pathParam("groupId", groupId)
                .delete("/registry/v3/groups/{groupId}/artifacts/" + artifactId)
            .then()
                .statusCode(204);
    }

    /**
     * Test that the X-Forwarded-Email header is properly stored in the credential.
     */
    @Test
    public void testEmailHeaderExtraction() {
        // Get current user info to verify email is extracted
        given()
            .header(HEADER_USERNAME, TEST_USERNAME)
            .header(HEADER_EMAIL, TEST_EMAIL)
            .header(HEADER_GROUPS, ROLE_READONLY)
            .when()
                .get("/registry/v3/users/me")
            .then()
                .statusCode(200)
                .body("username", equalTo(TEST_USERNAME));
    }

    /**
     * Test that groups from the X-Forwarded-Groups header are properly mapped to roles.
     */
    @Test
    public void testGroupsToRolesMapping() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // User with developer role can create artifacts
        given()
            .header(HEADER_USERNAME, TEST_USERNAME)
            .header(HEADER_EMAIL, TEST_EMAIL)
            .header(HEADER_GROUPS, ROLE_DEVELOPER)
            .contentType(ContentType.JSON)
            .body(TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON, ARTIFACT_CONTENT,
                    ContentTypes.APPLICATION_JSON))
            .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
            .then()
                .statusCode(200);

        // Clean up
        given()
            .header(HEADER_USERNAME, TEST_USERNAME)
            .header(HEADER_EMAIL, TEST_EMAIL)
            .header(HEADER_GROUPS, ROLE_DEVELOPER)
            .when()
                .pathParam("groupId", groupId)
                .delete("/registry/v3/groups/{groupId}/artifacts/" + artifactId)
            .then()
                .statusCode(204);
    }

    /**
     * Test that multiple groups can be specified as comma-separated values.
     */
    @Test
    public void testMultipleGroups() {
        // User can have multiple roles/groups
        given()
            .header(HEADER_USERNAME, TEST_USERNAME)
            .header(HEADER_EMAIL, TEST_EMAIL)
            .header(HEADER_GROUPS, ROLE_DEVELOPER + "," + ROLE_READONLY + ",custom-group")
            .when()
                .get("/registry/v3/users/me")
            .then()
                .statusCode(200)
                .body("username", equalTo(TEST_USERNAME))
                .body("developer", equalTo(true))
                .body("viewer", equalTo(true));
    }

    /**
     * Test that read-only users (via groups header) cannot create artifacts.
     */
    @Test
    public void testReadOnlyRole() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // First, create an artifact as a developer
        given()
            .header(HEADER_USERNAME, TEST_USERNAME)
            .header(HEADER_EMAIL, TEST_EMAIL)
            .header(HEADER_GROUPS, ROLE_DEVELOPER)
            .contentType(ContentType.JSON)
            .body(TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON, ARTIFACT_CONTENT,
                    ContentTypes.APPLICATION_JSON))
            .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
            .then()
                .statusCode(200);

        // Read-only user can view the artifact
        given()
            .header(HEADER_USERNAME, READONLY_USERNAME)
            .header(HEADER_EMAIL, READONLY_EMAIL)
            .header(HEADER_GROUPS, ROLE_READONLY)
            .when()
                .pathParam("groupId", groupId)
                .get("/registry/v3/groups/{groupId}/artifacts/" + artifactId)
            .then()
                .statusCode(200)
                .body("artifactId", equalTo(artifactId));

        // But cannot create a new artifact
        String newArtifactId = TestUtils.generateArtifactId();
        given()
            .header(HEADER_USERNAME, READONLY_USERNAME)
            .header(HEADER_EMAIL, READONLY_EMAIL)
            .header(HEADER_GROUPS, ROLE_READONLY)
            .contentType(ContentType.JSON)
            .body(TestUtils.clientCreateArtifact(newArtifactId, ArtifactType.JSON, ARTIFACT_CONTENT,
                    ContentTypes.APPLICATION_JSON))
            .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
            .then()
                .statusCode(403); // Forbidden

        // Clean up
        given()
            .header(HEADER_USERNAME, TEST_USERNAME)
            .header(HEADER_EMAIL, TEST_EMAIL)
            .header(HEADER_GROUPS, ROLE_DEVELOPER)
            .when()
                .pathParam("groupId", groupId)
                .delete("/registry/v3/groups/{groupId}/artifacts/" + artifactId)
            .then()
                .statusCode(204);
    }

    /**
     * Test that admin users (via groups header) have full access.
     */
    @Test
    public void testAdminRole() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Admin user can create artifacts
        given()
            .header(HEADER_USERNAME, ADMIN_USERNAME)
            .header(HEADER_EMAIL, ADMIN_EMAIL)
            .header(HEADER_GROUPS, ROLE_ADMIN)
            .contentType(ContentType.JSON)
            .body(TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON, ARTIFACT_CONTENT,
                    ContentTypes.APPLICATION_JSON))
            .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
            .then()
                .statusCode(200);

        // Verify user info shows admin role
        given()
            .header(HEADER_USERNAME, ADMIN_USERNAME)
            .header(HEADER_EMAIL, ADMIN_EMAIL)
            .header(HEADER_GROUPS, ROLE_ADMIN)
            .when()
                .get("/registry/v3/users/me")
            .then()
                .statusCode(200)
                .body("username", equalTo(ADMIN_USERNAME))
                .body("admin", equalTo(true));

        // Clean up
        given()
            .header(HEADER_USERNAME, ADMIN_USERNAME)
            .header(HEADER_EMAIL, ADMIN_EMAIL)
            .header(HEADER_GROUPS, ROLE_ADMIN)
            .when()
                .pathParam("groupId", groupId)
                .delete("/registry/v3/groups/{groupId}/artifacts/" + artifactId)
            .then()
                .statusCode(204);
    }

    /**
     * Test that different users (different usernames) are treated as separate principals.
     */
    @Test
    public void testDifferentUsers() {
        String groupId = TestUtils.generateGroupId();
        String artifactId1 = TestUtils.generateArtifactId();
        String artifactId2 = TestUtils.generateArtifactId();

        // User 1 creates an artifact
        given()
            .header(HEADER_USERNAME, "user1")
            .header(HEADER_EMAIL, "user1@example.com")
            .header(HEADER_GROUPS, ROLE_DEVELOPER)
            .contentType(ContentType.JSON)
            .body(TestUtils.clientCreateArtifact(artifactId1, ArtifactType.JSON, ARTIFACT_CONTENT,
                    ContentTypes.APPLICATION_JSON))
            .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
            .then()
                .statusCode(200)
                .body("version.owner", equalTo("user1"));

        // User 2 creates a different artifact
        given()
            .header(HEADER_USERNAME, "user2")
            .header(HEADER_EMAIL, "user2@example.com")
            .header(HEADER_GROUPS, ROLE_DEVELOPER)
            .contentType(ContentType.JSON)
            .body(TestUtils.clientCreateArtifact(artifactId2, ArtifactType.JSON, ARTIFACT_CONTENT,
                    ContentTypes.APPLICATION_JSON))
            .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
            .then()
                .statusCode(200)
                .body("version.owner", equalTo("user2"));

        // Both users can see both artifacts (if they have read permissions)
        given()
            .header(HEADER_USERNAME, "user1")
            .header(HEADER_EMAIL, "user1@example.com")
            .header(HEADER_GROUPS, ROLE_DEVELOPER)
            .when()
                .pathParam("groupId", groupId)
                .get("/registry/v3/groups/{groupId}/artifacts/" + artifactId2)
            .then()
                .statusCode(200);

        // Clean up
        given()
            .header(HEADER_USERNAME, "user1")
            .header(HEADER_EMAIL, "user1@example.com")
            .header(HEADER_GROUPS, ROLE_DEVELOPER)
            .when()
                .pathParam("groupId", groupId)
                .delete("/registry/v3/groups/{groupId}/artifacts/" + artifactId1)
            .then()
                .statusCode(204);

        given()
            .header(HEADER_USERNAME, "user2")
            .header(HEADER_EMAIL, "user2@example.com")
            .header(HEADER_GROUPS, ROLE_DEVELOPER)
            .when()
                .pathParam("groupId", groupId)
                .delete("/registry/v3/groups/{groupId}/artifacts/" + artifactId2)
            .then()
                .statusCode(204);
    }

    /**
     * Test that authentication works even when the groups header is missing
     * (user will just have no roles).
     */
    @Test
    public void testMissingGroupsHeader() {
        // Request with username and email but no groups should still authenticate
        // but user will have no roles (viewer/developer/admin all false)
        given()
            .header(HEADER_USERNAME, TEST_USERNAME)
            .header(HEADER_EMAIL, TEST_EMAIL)
            .when()
                .get("/registry/v3/users/me")
            .then()
                .statusCode(200)
                .body("username", equalTo(TEST_USERNAME))
                .body("admin", equalTo(false))
                .body("developer", equalTo(false))
                .body("viewer", equalTo(false));
    }

    /**
     * Test that authentication works even when the email header is missing.
     */
    @Test
    public void testMissingEmailHeader() {
        // Request with username and groups but no email should still authenticate
        given()
            .header(HEADER_USERNAME, TEST_USERNAME)
            .header(HEADER_GROUPS, ROLE_READONLY)
            .when()
                .get("/registry/v3/users/me")
            .then()
                .statusCode(200)
                .body("username", equalTo(TEST_USERNAME))
                .body("viewer", equalTo(true));
    }

    /**
     * Test that whitespace in username header is trimmed properly.
     */
    @Test
    public void testWhitespaceHandling() {
        // Username with leading/trailing whitespace should still work
        given()
            .header(HEADER_USERNAME, "  " + TEST_USERNAME + "  ")
            .header(HEADER_EMAIL, TEST_EMAIL)
            .header(HEADER_GROUPS, ROLE_READONLY)
            .when()
                .get("/registry/v3/users/me")
            .then()
                .statusCode(200);
    }

    /**
     * Test that empty groups header is handled gracefully.
     */
    @Test
    public void testEmptyGroupsHeader() {
        // Empty groups header should result in no roles
        given()
            .header(HEADER_USERNAME, TEST_USERNAME)
            .header(HEADER_EMAIL, TEST_EMAIL)
            .header(HEADER_GROUPS, "")
            .when()
                .get("/registry/v3/users/me")
            .then()
                .statusCode(200)
                .body("username", equalTo(TEST_USERNAME))
                .body("admin", equalTo(false))
                .body("developer", equalTo(false))
                .body("viewer", equalTo(false));
    }

    /**
     * Test that groups with whitespace are parsed correctly.
     */
    @Test
    public void testGroupsWithWhitespace() {
        // Groups with spaces around commas should be trimmed
        given()
            .header(HEADER_USERNAME, TEST_USERNAME)
            .header(HEADER_EMAIL, TEST_EMAIL)
            .header(HEADER_GROUPS, " " + ROLE_DEVELOPER + " , " + ROLE_READONLY + " ")
            .when()
                .get("/registry/v3/users/me")
            .then()
                .statusCode(200)
                .body("username", equalTo(TEST_USERNAME))
                .body("developer", equalTo(true))
                .body("viewer", equalTo(true));
    }

    /**
     * Test that users without any roles cannot create artifacts.
     */
    @Test
    public void testUserWithoutRoles() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // User with no groups header (no roles) cannot create artifacts
        given()
            .header(HEADER_USERNAME, TEST_USERNAME)
            .header(HEADER_EMAIL, TEST_EMAIL)
            .contentType(ContentType.JSON)
            .body(TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON, ARTIFACT_CONTENT,
                    ContentTypes.APPLICATION_JSON))
            .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
            .then()
                .statusCode(403); // Forbidden - no roles
    }

    /**
     * Test that case sensitivity is preserved for usernames.
     */
    @Test
    public void testUsernameCaseSensitivity() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create artifact with lowercase username
        given()
            .header(HEADER_USERNAME, "testuser")
            .header(HEADER_EMAIL, TEST_EMAIL)
            .header(HEADER_GROUPS, ROLE_DEVELOPER)
            .contentType(ContentType.JSON)
            .body(TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON, ARTIFACT_CONTENT,
                    ContentTypes.APPLICATION_JSON))
            .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
            .then()
                .statusCode(200)
                .body("version.owner", equalTo("testuser"));

        // Different case should be treated as different user
        given()
            .header(HEADER_USERNAME, "TESTUSER")
            .header(HEADER_EMAIL, "TESTUSER@example.com")
            .header(HEADER_GROUPS, ROLE_DEVELOPER)
            .when()
                .get("/registry/v3/users/me")
            .then()
                .statusCode(200)
                .body("username", equalTo("TESTUSER"));

        // Clean up
        given()
            .header(HEADER_USERNAME, "testuser")
            .header(HEADER_EMAIL, TEST_EMAIL)
            .header(HEADER_GROUPS, ROLE_DEVELOPER)
            .when()
                .pathParam("groupId", groupId)
                .delete("/registry/v3/groups/{groupId}/artifacts/" + artifactId)
            .then()
                .statusCode(204);
    }

    /**
     * Test that special characters in username are handled correctly.
     */
    @Test
    public void testUsernameWithSpecialCharacters() {
        String specialUsername = "user@domain.com";

        given()
            .header(HEADER_USERNAME, specialUsername)
            .header(HEADER_EMAIL, "user@domain.com")
            .header(HEADER_GROUPS, ROLE_READONLY)
            .when()
                .get("/registry/v3/users/me")
            .then()
                .statusCode(200)
                .body("username", equalTo(specialUsername));
    }
}
