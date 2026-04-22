package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KeycloakTestContainerManager;
import io.apicurio.registry.utils.tests.ProxyAndOidcAuthTestProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Tests for dual proxy-header + OIDC authentication mode. When both mechanisms are enabled,
 * proxy headers are tried first. If absent, the request falls back to OIDC authentication.
 */
@QuarkusTest
@TestProfile(ProxyAndOidcAuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class ProxyAndOidcDualAuthTest extends AbstractResourceTestBase {

    private static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";
    private static final String HEADER_USERNAME = "X-Forwarded-User";
    private static final String HEADER_EMAIL = "X-Forwarded-Email";
    private static final String HEADER_GROUPS = "X-Forwarded-Groups";

    private static final String PROXY_USERNAME = "proxy-testuser";
    private static final String PROXY_EMAIL = "proxy-testuser@example.com";
    private static final String ROLE_ADMIN = "sr-admin";
    private static final String ROLE_DEVELOPER = "sr-developer";

    @ConfigProperty(name = "quarkus.oidc.token-path")
    String tokenEndpoint;

    @Override
    protected void deleteGlobalRules(int expectedDefaultRulesCount) throws Exception {
        // Skip — would fail without auth headers
    }

    /**
     * Obtains a Bearer token from Keycloak using client credentials grant.
     */
    private String obtainOidcToken(String clientId, String clientSecret) {
        return given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", clientId)
                .formParam("client_secret", clientSecret)
                .post(tokenEndpoint)
                .then()
                .statusCode(200)
                .extract().path("access_token");
    }

    /**
     * When proxy headers are present, proxy authentication is used regardless of OIDC being
     * enabled. The artifact owner should match the proxy username.
     */
    @Test
    public void testProxyHeadersPresent_ProxyAuthUsed() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        given()
                .header(HEADER_USERNAME, PROXY_USERNAME)
                .header(HEADER_EMAIL, PROXY_EMAIL)
                .header(HEADER_GROUPS, ROLE_DEVELOPER)
                .contentType(ContentType.JSON)
                .body(TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON,
                        ARTIFACT_CONTENT, ContentTypes.APPLICATION_JSON))
                .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("version.owner", equalTo(PROXY_USERNAME));

        // Clean up
        given()
                .header(HEADER_USERNAME, PROXY_USERNAME)
                .header(HEADER_EMAIL, PROXY_EMAIL)
                .header(HEADER_GROUPS, ROLE_DEVELOPER)
                .when()
                .pathParam("groupId", groupId)
                .delete("/registry/v3/groups/{groupId}/artifacts/" + artifactId)
                .then()
                .statusCode(204);
    }

    /**
     * When proxy headers are absent but a valid OIDC Bearer token is present, the request falls
     * back to OIDC authentication. The artifact owner should match the OIDC client identity.
     */
    @Test
    public void testNoProxyHeaders_OidcFallback() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        String token = obtainOidcToken(KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON,
                        ARTIFACT_CONTENT, ContentTypes.APPLICATION_JSON))
                .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("version.owner",
                        equalTo(KeycloakTestContainerManager.ADMIN_CLIENT_ID));

        // Clean up
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .pathParam("groupId", groupId)
                .delete("/registry/v3/groups/{groupId}/artifacts/" + artifactId)
                .then()
                .statusCode(204);
    }

    /**
     * When neither proxy headers nor an OIDC token are present, the request should be rejected
     * with 401.
     */
    @Test
    public void testNeitherPresent_Rejected() {
        given()
                .when()
                .get("/registry/v3/search/artifacts")
                .then()
                .statusCode(401);
    }

    /**
     * When both proxy headers and an OIDC Bearer token are present, proxy authentication wins
     * because it is first in the chain. The identity should come from the proxy headers.
     */
    @Test
    public void testBothPresent_ProxyWins() {
        String token = obtainOidcToken(KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1");

        given()
                .header(HEADER_USERNAME, PROXY_USERNAME)
                .header(HEADER_EMAIL, PROXY_EMAIL)
                .header(HEADER_GROUPS, ROLE_ADMIN)
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/registry/v3/users/me")
                .then()
                .statusCode(200)
                .body("username", equalTo(PROXY_USERNAME));
    }

    /**
     * Verify that OIDC client credentials authentication still works as a fallback when proxy
     * headers are absent — confirming that the full OIDC client credentials flow (not just Bearer
     * token pass-through) functions correctly in dual mode.
     */
    @Test
    public void testOidcClientCredentials_WithBasicAuth() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        given()
                .auth().preemptive().basic(
                        KeycloakTestContainerManager.DEVELOPER_CLIENT_ID, "test1")
                .contentType(ContentType.JSON)
                .body(TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON,
                        ARTIFACT_CONTENT, ContentTypes.APPLICATION_JSON))
                .when()
                .pathParam("groupId", groupId)
                .post("/registry/v3/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("version.owner",
                        equalTo(KeycloakTestContainerManager.DEVELOPER_CLIENT_ID));

        // Clean up
        given()
                .auth().preemptive().basic(
                        KeycloakTestContainerManager.DEVELOPER_CLIENT_ID, "test1")
                .when()
                .pathParam("groupId", groupId)
                .delete("/registry/v3/groups/{groupId}/artifacts/" + artifactId)
                .then()
                .statusCode(204);
    }
}
