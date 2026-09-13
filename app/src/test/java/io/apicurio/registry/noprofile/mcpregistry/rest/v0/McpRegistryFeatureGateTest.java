package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

/**
 * Tests that the MCP Registry endpoints are invisible when the feature is disabled, which is the default.
 *
 * There is no central filter: every endpoint calls requireEnabled() itself, so every endpoint is covered here.
 * One added without that call would be live on every deployment. The message is asserted as well as the
 * status, because with the feature on, a missing server or version answers 404 too.
 */
@QuarkusTest
public class McpRegistryFeatureGateTest extends AbstractResourceTestBase {

    private static final String BASE = "/mcp-registry/v0.1";
    private static final String SERVER = BASE + "/servers/io.github.example/weather";
    private static final String DISABLED = "MCP Registry API is disabled";

    @Test
    public void testListServersBlockedWhenDisabled() {
        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers")
                .then()
                .statusCode(404)
                .body("error", equalTo(DISABLED));
    }

    @Test
    public void testGetServerBlockedWhenDisabled() {
        given()
                .when()
                .contentType(CT_JSON)
                .get(SERVER)
                .then()
                .statusCode(404)
                .body("error", equalTo(DISABLED));
    }

    @Test
    public void testListServerVersionsBlockedWhenDisabled() {
        given()
                .when()
                .contentType(CT_JSON)
                .get(SERVER + "/versions")
                .then()
                .statusCode(404)
                .body("error", equalTo(DISABLED));
    }

    @Test
    public void testGetServerVersionBlockedWhenDisabled() {
        given()
                .when()
                .contentType(CT_JSON)
                .get(SERVER + "/versions/1.0.0")
                .then()
                .statusCode(404)
                .body("error", equalTo(DISABLED));
    }

    @Test
    public void testPublishBlockedWhenDisabled() {
        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"name\":\"io.github.example/weather\",\"version\":\"1.0.0\"}")
                .post(BASE + "/publish")
                .then()
                .statusCode(404)
                .body("error", equalTo(DISABLED));
    }

    @Test
    public void testDeleteServerVersionBlockedWhenDisabled() {
        given()
                .when()
                .delete(SERVER + "/versions/1.0.0")
                .then()
                .statusCode(404)
                .body("error", equalTo(DISABLED));
    }

    @Test
    public void testUpdateServerVersionStatusBlockedWhenDisabled() {
        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"status\":\"deprecated\"}")
                .patch(SERVER + "/versions/1.0.0/status")
                .then()
                .statusCode(404)
                .body("error", equalTo(DISABLED));
    }

    @Test
    public void testUpdateServerStatusBlockedWhenDisabled() {
        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"status\":\"deprecated\"}")
                .patch(SERVER + "/status")
                .then()
                .statusCode(404)
                .body("error", equalTo(DISABLED));
    }
}
