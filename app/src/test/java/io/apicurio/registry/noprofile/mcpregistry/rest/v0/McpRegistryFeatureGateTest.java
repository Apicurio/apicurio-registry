package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Tests that the MCP Registry endpoints are invisible when the feature is disabled, which is the default.
 */
@QuarkusTest
public class McpRegistryFeatureGateTest extends AbstractResourceTestBase {

    @Test
    public void testListServersBlockedWhenDisabled() {
        given()
                .when()
                .contentType(CT_JSON)
                .get("/mcp-registry/v0.1/servers")
                .then()
                .statusCode(404);
    }

    @Test
    public void testGetServerBlockedWhenDisabled() {
        given()
                .when()
                .contentType(CT_JSON)
                .get("/mcp-registry/v0.1/servers/io.github.example/weather")
                .then()
                .statusCode(404);
    }

    @Test
    public void testPublishBlockedWhenDisabled() {
        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"name\":\"io.github.example/weather\",\"version\":\"1.0.0\"}")
                .post("/mcp-registry/v0.1/publish")
                .then()
                .statusCode(404);
    }
}
