package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.ProxyHeaderTrustAuthTestProfile;
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
 * Test for proxy header authentication with trust-proxy-authorization enabled.
 *
 * This test validates that when apicurio.authn.proxy-header.trust-proxy-authorization=true,
 * local authorization checks are skipped for requests authenticated via proxy headers,
 * meaning the proxy is trusted to have already performed authorization.
 *
 * IMPORTANT: This test demonstrates a security-sensitive configuration that should only
 * be used when the proxy is performing comprehensive authorization checks.
 */
@QuarkusTest
@TestProfile(ProxyHeaderTrustAuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class ProxyHeaderTrustAuthTest extends AbstractResourceTestBase {

    private static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";
    private static final String HEADER_USERNAME = "X-Forwarded-User";
    private static final String HEADER_EMAIL = "X-Forwarded-Email";
    private static final String HEADER_GROUPS = "X-Forwarded-Groups";

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_EMAIL = "testuser@example.com";
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
     * Test that users with read-only role can create artifacts when proxy authorization is trusted.
     * Normally read-only users cannot create artifacts, but with trust-proxy-authorization=true,
     * the proxy is assumed to have authorized this operation.
     */
    @Test
    public void testReadOnlyUserCanCreateWhenTrustingProxy() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Read-only user can create artifacts (authorization check skipped)
        String response = given()
            .header(HEADER_USERNAME, READONLY_USERNAME)
            .header(HEADER_EMAIL, READONLY_EMAIL)
            .header(HEADER_GROUPS, ROLE_READONLY)
            .contentType(ContentType.JSON)
            .body(TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON, ARTIFACT_CONTENT,
                    ContentTypes.APPLICATION_JSON))
            .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
            .then()
                .statusCode(200)  // Success! Authorization check was skipped
                .body("version.artifactId", equalTo(artifactId))
                .body("version.owner", equalTo(READONLY_USERNAME))
            .extract().asString();

        assertNotNull(response);

        // Clean up
        given()
            .header(HEADER_USERNAME, READONLY_USERNAME)
            .header(HEADER_EMAIL, READONLY_EMAIL)
            .header(HEADER_GROUPS, ROLE_READONLY)
            .when()
                .pathParam("groupId", groupId)
                .delete("/registry/v3/groups/{groupId}/artifacts/" + artifactId)
            .then()
                .statusCode(204);
    }

    /**
     * Test that users with NO roles can create artifacts when proxy authorization is trusted.
     * Normally users without roles cannot create artifacts, but with trust-proxy-authorization=true,
     * local role checks are bypassed.
     */
    @Test
    public void testUserWithNoRolesCanCreateWhenTrustingProxy() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // User with NO groups/roles can still create artifacts (authorization bypassed)
        given()
            .header(HEADER_USERNAME, TEST_USERNAME)
            .header(HEADER_EMAIL, TEST_EMAIL)
            // No HEADER_GROUPS - user has no roles
            .contentType(ContentType.JSON)
            .body(TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON, ARTIFACT_CONTENT,
                    ContentTypes.APPLICATION_JSON))
            .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
            .then()
                .statusCode(200);  // Success! Role check was skipped

        // Clean up
        given()
            .header(HEADER_USERNAME, TEST_USERNAME)
            .header(HEADER_EMAIL, TEST_EMAIL)
            .when()
                .pathParam("groupId", groupId)
                .delete("/registry/v3/groups/{groupId}/artifacts/" + artifactId)
            .then()
                .statusCode(204);
    }

    /**
     * Test that authenticated users with proxy headers can perform all operations
     * regardless of their configured roles.
     */
    @Test
    public void testAllOperationsAllowedWithProxyAuth() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create artifact (normally requires developer/admin role)
        given()
            .header(HEADER_USERNAME, READONLY_USERNAME)
            .header(HEADER_EMAIL, READONLY_EMAIL)
            .header(HEADER_GROUPS, ROLE_READONLY)
            .contentType(ContentType.JSON)
            .body(TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON, ARTIFACT_CONTENT,
                    ContentTypes.APPLICATION_JSON))
            .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
            .then()
                .statusCode(200);

        // Update artifact (normally requires developer/admin role)
        String updatedContent = "{\"name\":\"updated\"}";
        given()
            .header(HEADER_USERNAME, READONLY_USERNAME)
            .header(HEADER_EMAIL, READONLY_EMAIL)
            .header(HEADER_GROUPS, ROLE_READONLY)
            .contentType(ContentType.JSON)
            .body(TestUtils.clientCreateVersion(updatedContent, ContentTypes.APPLICATION_JSON))
            .when()
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions")
            .then()
                .statusCode(200);

        // Delete artifact (normally requires developer/admin role)
        given()
            .header(HEADER_USERNAME, READONLY_USERNAME)
            .header(HEADER_EMAIL, READONLY_EMAIL)
            .header(HEADER_GROUPS, ROLE_READONLY)
            .when()
                .pathParam("groupId", groupId)
                .delete("/registry/v3/groups/{groupId}/artifacts/" + artifactId)
            .then()
                .statusCode(204);
    }

    /**
     * Test that the user identity is correctly extracted from proxy headers
     * even though authorization is bypassed.
     */
    @Test
    public void testUserIdentityExtractedCorrectly() {
        // Verify user info is correct
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
     * Test that multiple users can perform operations with their own identities.
     */
    @Test
    public void testMultipleUserIdentities() {
        String groupId = TestUtils.generateGroupId();
        String artifactId1 = TestUtils.generateArtifactId();
        String artifactId2 = TestUtils.generateArtifactId();

        // User 1 creates an artifact
        given()
            .header(HEADER_USERNAME, "user1")
            .header(HEADER_EMAIL, "user1@example.com")
            .header(HEADER_GROUPS, ROLE_READONLY)  // Read-only, but can create due to trust
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
            .header(HEADER_GROUPS, ROLE_READONLY)  // Read-only, but can create due to trust
            .contentType(ContentType.JSON)
            .body(TestUtils.clientCreateArtifact(artifactId2, ArtifactType.JSON, ARTIFACT_CONTENT,
                    ContentTypes.APPLICATION_JSON))
            .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
            .then()
                .statusCode(200)
                .body("version.owner", equalTo("user2"));

        // Clean up
        given()
            .header(HEADER_USERNAME, "user1")
            .header(HEADER_EMAIL, "user1@example.com")
            .when()
                .pathParam("groupId", groupId)
                .delete("/registry/v3/groups/{groupId}/artifacts/" + artifactId1)
            .then()
                .statusCode(204);

        given()
            .header(HEADER_USERNAME, "user2")
            .header(HEADER_EMAIL, "user2@example.com")
            .when()
                .pathParam("groupId", groupId)
                .delete("/registry/v3/groups/{groupId}/artifacts/" + artifactId2)
            .then()
                .statusCode(204);
    }

    /**
     * Test that ownership is still tracked even though authorization is bypassed.
     */
    @Test
    public void testOwnershipTracking() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create artifact as user1
        given()
            .header(HEADER_USERNAME, "user1")
            .header(HEADER_EMAIL, "user1@example.com")
            .header(HEADER_GROUPS, ROLE_READONLY)
            .contentType(ContentType.JSON)
            .body(TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON, ARTIFACT_CONTENT,
                    ContentTypes.APPLICATION_JSON))
            .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
            .then()
                .statusCode(200);

        // Verify ownership is user1
        given()
            .header(HEADER_USERNAME, "user2")  // Different user
            .header(HEADER_EMAIL, "user2@example.com")
            .when()
                .pathParam("groupId", groupId)
                .get("/registry/v3/groups/{groupId}/artifacts/" + artifactId)
            .then()
                .statusCode(200)
                .body("owner", equalTo("user1"));  // Ownership is preserved

        // Clean up
        given()
            .header(HEADER_USERNAME, "user1")
            .header(HEADER_EMAIL, "user1@example.com")
            .when()
                .pathParam("groupId", groupId)
                .delete("/registry/v3/groups/{groupId}/artifacts/" + artifactId)
            .then()
                .statusCode(204);
    }
}
