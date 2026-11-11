package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Import is off by default: the endpoint answers 409 and contacts nothing.
 */
@QuarkusTest
public class McpImportFeatureGateTest extends AbstractResourceTestBase {

    @Test
    void disabledByDefault() throws Exception {
        String group = TestUtils.generateGroupId();
        createArtifact(group, "srv", ArtifactType.MCP_SERVER,
                "{\"name\":\"io.github.test/srv\",\"description\":\"A server\",\"version\":\"1.0.0\"}",
                ContentTypes.APPLICATION_JSON);
        try (McpTestServer mcp = new McpTestServer()) {
            given().contentType(ContentTypes.APPLICATION_JSON).body(Map.of("url", mcp.url()))
                    .post(registryV3ApiUrl + "/groups/" + group + "/artifacts/srv/mcp-import").then()
                    .statusCode(409)
                    .body("title", equalTo("Importing tools from MCP servers is not enabled."));
            assertEquals(0, mcp.requests.get());
        }
    }

    @Test
    void uiConfigDoesNotOfferTheImport() {
        given().get(registryV3ApiUrl + "/system/uiConfig").then().statusCode(200)
                .body("features.mcpImport", equalTo(false));
    }
}
