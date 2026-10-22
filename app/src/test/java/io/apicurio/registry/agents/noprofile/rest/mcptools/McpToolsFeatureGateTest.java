package io.apicurio.registry.agents.noprofile.rest.mcptools;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests MCP well-known endpoints when experimental/A2A are on but MCP tools are off.
 */
@QuarkusTest
@TestProfile(McpToolsDisabledProfile.class)
public class McpToolsFeatureGateTest extends AbstractResourceTestBase {

    private String serverRootUrl;

    @BeforeEach
    public void setUp() {
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        serverRootUrl = "http://localhost:" + port;
    }

    private RequestSpecification givenAtRoot() {
        return RestAssured.given().baseUri(serverRootUrl);
    }

    @Test
    public void testMcpToolsSearchBlockedWhenMcpDisabled() {
        givenAtRoot()
                .when()
                .get("/.well-known/mcp-tools")
                .then()
                .statusCode(404);
    }

    @Test
    public void testMcpToolGetBlockedWhenMcpDisabled() {
        givenAtRoot()
                .when()
                .pathParam("groupId", "some-group")
                .pathParam("artifactId", "some-tool")
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}")
                .then()
                .statusCode(404);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/.well-known/mcp-tools/some-group/some-tool/compatible",
            "/.well-known/mcp-tools/some-group/some-tool/compatibility",
            "/apis/registry/v3/well-known/mcp-tools/some-group/some-tool/compatible",
            "/apis/registry/v3/well-known/mcp-tools/some-group/some-tool/compatibility",
    })
    public void testMcpToolCompatibilityBlockedWhenMcpDisabled(String path) {
        givenAtRoot()
                .when()
                .get(path)
                .then()
                .statusCode(404)
                .body("status", equalTo(404))
                .body("title", notNullValue());
    }

    @Test
    public void testMcpToolSchemaAvailableWhenA2AEnabledAndMcpDisabled() {
        // Schema gate is a2a || mcp, so A2A alone still serves the schema.
        givenAtRoot()
                .when()
                .get("/.well-known/schemas/mcp-tool/v1")
                .then()
                .statusCode(200);
    }
}
