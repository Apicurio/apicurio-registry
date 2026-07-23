package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.FormAuthTestProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Form-Based Authentication.
 *
 * Validates that the form login endpoint (/j_security_check) successfully authenticates
 * users against a configured Identity Provider, generates session cookies, and properly
 * enforces role-based access control based on the user's groups.
 */
@QuarkusTest
@TestProfile(FormAuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class FormAuthTest extends AbstractResourceTestBase {

    private static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";

    @Override
    protected void deleteGlobalRules(int expectedDefaultRulesCount) throws Exception {
        // No need for this in this test, and it wouldn't work anyway because it
        // would fail with a 401/302 (missing auth cookie)
    }

    private String login(String username, String password) {
        Response response = given()
            .formParam("j_username", username)
            .formParam("j_password", password)
            .when()
                .post("/j_security_check")
            .then()
                .statusCode(302)
                .extract().response();
        
        return response.getCookie("quarkus-credential");
    }

    @Test
    public void testUnauthenticatedAccess() {
        // Request without cookie should fail (either 401 or 302 redirect to login)
        Response response = given()
            .when()
                .get("/registry/v3/users/me")
            .then()
                .extract().response();
        
        int status = response.statusCode();
        assertTrue(status == 401 || status == 302 || status == 403, "Status should be 401, 302, or 403 but was " + status);
    }

    @Test
    public void testLoginAndAccess() {
        // 1. Login to get the cookie
        String cookie = login("developer", "developer");
        assertNotNull(cookie, "Quarkus credential cookie should not be null after successful login");

        // 2. Use the cookie to access authenticated endpoint
        given()
            .cookie("quarkus-credential", cookie)
            .when()
                .get("/registry/v3/users/me")
            .then()
                .statusCode(200)
                .body("username", equalTo("developer"))
                .body("developer", equalTo(true));
    }

    @Test
    public void testRoleBasedAuthorization() {
        String cookie = login("developer", "developer");
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Developer can create artifacts
        given()
            .cookie("quarkus-credential", cookie)
            .contentType(ContentType.JSON)
            .body(TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON, ARTIFACT_CONTENT,
                    ContentTypes.APPLICATION_JSON))
            .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
            .then()
                .statusCode(200)
                .body("version.artifactId", equalTo(artifactId))
                .body("version.owner", equalTo("developer"));
    }

    @Test
    public void testReadOnlyRole() {
        String cookie = login("readonly", "readonly");
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Read-only user cannot create artifacts
        given()
            .cookie("quarkus-credential", cookie)
            .contentType(ContentType.JSON)
            .body(TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON, ARTIFACT_CONTENT,
                    ContentTypes.APPLICATION_JSON))
            .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
            .then()
                .statusCode(403);
    }

    @Test
    public void testInvalidLogin() {
        Response response = given()
            .formParam("j_username", "admin")
            .formParam("j_password", "wrongpassword")
            .when()
                .post("/j_security_check")
            .then()
                .extract().response();
        
        // Form login usually redirects to an error page on failure or returns 401
        assertTrue(response.statusCode() == 302 || response.statusCode() == 401, "Status should be 302 or 401 but was " + response.statusCode());
        
        String cookie = response.getCookie("quarkus-credential");
        assertTrue(cookie == null || cookie.isEmpty(), "Cookie should not be set on invalid login");
    }
}
