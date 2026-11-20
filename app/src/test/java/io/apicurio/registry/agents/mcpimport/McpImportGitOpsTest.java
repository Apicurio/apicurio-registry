package io.apicurio.registry.agents.mcpimport;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Read-only storage refuses an import, dry run or not, before the MCP server is contacted.
 */
@QuarkusTest
@TestProfile(McpImportGitOpsTestProfile.class)
class McpImportGitOpsTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refusedBeforeContactingTheServer(boolean dryRun) throws Exception {
        try (McpTestServer mcp = new McpTestServer()) {
            mcp.tools(McpTestServer.tool("search", "string"));
            given().contentType("application/json").body(Map.of("url", mcp.url(), "dryRun", dryRun))
                    .post("/apis/registry/v3/groups/tools/artifacts/srv/mcp-import").then()
                    .statusCode(409)
                    .body("title", equalTo("Unsupported write operation. Storage is in read-only mode."));
            assertEquals(0, mcp.requests.get());
        }
    }

    /** Import is enabled in this profile, but read-only storage could not write the result. */
    @Test
    void uiConfigDoesNotOfferTheImportOnReadOnlyStorage() {
        given().get("/apis/registry/v3/system/uiConfig").then().statusCode(200)
                .body("features.mcpImport", equalTo(false));
    }
}
