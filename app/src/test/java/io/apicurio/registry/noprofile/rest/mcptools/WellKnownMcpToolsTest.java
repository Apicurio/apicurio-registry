package io.apicurio.registry.noprofile.rest.mcptools;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integration tests for the MCP well-known endpoints:
 * - Registered MCP tool discovery (GET /.well-known/mcp-tools/{groupId}/{artifactId})
 * - Search MCP tools (GET /.well-known/mcp-tools)
 * - Compatible tools endpoint (GET /.well-known/mcp-tools/{groupId}/{artifactId}/compatible)
 * - Schema endpoint response headers (GET /.well-known/schemas/mcp-tool/v1)
 */
@QuarkusTest
@TestProfile(McpToolsEnabledProfile.class)
public class WellKnownMcpToolsTest extends AbstractResourceTestBase {

    private String serverRootUrl;

    @BeforeEach
    public void setUpWellKnown() {
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        serverRootUrl = "http://localhost:" + port;
    }

    private RequestSpecification givenAtRoot() {
        return RestAssured.given().baseUri(serverRootUrl);
    }

    private static final String MCP_TOOL_DB_SEARCH = """
            {
                "name": "db_search",
                "title": "Database Search",
                "description": "Search product database",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "query": { "type": "string" },
                        "limit": { "type": "integer" }
                    },
                    "required": ["query"]
                },
                "outputSchema": {
                    "type": "object",
                    "properties": {
                        "records": { "type": "array" },
                        "total": { "type": "integer" }
                    },
                    "required": ["records", "total"]
                }
            }
            """;

    private static final String MCP_TOOL_RECORD_PROCESSOR = """
            {
                "name": "record_processor",
                "title": "Record Processor",
                "description": "Process search result records",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "records": { "type": "array" },
                        "total": { "type": "integer" },
                        "format": { "type": "string" }
                    },
                    "required": ["records"]
                }
            }
            """;

    private static final String MCP_TOOL_INCOMPATIBLE_PROCESSOR = """
            {
                "name": "incompatible_processor",
                "title": "Incompatible Processor",
                "description": "Expects records to be string not array",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "records": { "type": "string" }
                    }
                }
            }
            """;

    private static final String MCP_TOOL_NO_OUTPUT = """
            {
                "name": "sink_tool",
                "title": "Sink Tool",
                "description": "Consumes inputs without producing structured output",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "data": { "type": "string" }
                    }
                }
            }
            """;

    @Test
    public void testGetRegisteredMcpToolLatest() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        createMcpTool(groupId, artifactId, MCP_TOOL_DB_SEARCH);

        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}")
                .then()
                .statusCode(200)
                .body("name", equalTo("db_search"))
                .body("title", equalTo("Database Search"))
                .body("description", equalTo("Search product database"))
                .body("inputSchema.type", equalTo("object"))
                .body("inputSchema.properties.query.type", equalTo("string"));
    }

    @Test
    public void testGetRegisteredMcpToolExplicitVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        createMcpTool(groupId, artifactId, MCP_TOOL_DB_SEARCH);

        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .queryParam("version", "1")
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}")
                .then()
                .statusCode(200)
                .body("name", equalTo("db_search"));
    }

    @Test
    public void testGetRegisteredMcpToolMissingVersionNotFound() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        createMcpTool(groupId, artifactId, MCP_TOOL_DB_SEARCH);

        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .queryParam("version", "99999")
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}")
                .then()
                .statusCode(404);
    }

    @Test
    public void testGetRegisteredMcpToolNotFound() {
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", "nonexistent-group")
                .pathParam("artifactId", "nonexistent-tool")
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}")
                .then()
                .statusCode(404);
    }

    @Test
    public void testSearchMcpTools() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createMcpTool(groupId, "search-tool-1", MCP_TOOL_DB_SEARCH);
        createMcpTool(groupId, "search-tool-2", MCP_TOOL_RECORD_PROCESSOR);

        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .get("/.well-known/mcp-tools")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(2))
                .body("tools", notNullValue());
    }

    @Test
    public void testSearchMcpToolsByParameter() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createMcpTool(groupId, "param-tool-db", MCP_TOOL_DB_SEARCH);

        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .queryParam("parameter", "query")
                .get("/.well-known/mcp-tools")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("tools.artifactId", hasItem("param-tool-db"));
    }

    @Test
    public void testFindCompatibleToolsSuccess() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String sourceId = "source-db-search";
        String candidateId = "candidate-processor";
        String incompatibleId = "incompatible-proc";

        createMcpTool(groupId, sourceId, MCP_TOOL_DB_SEARCH);
        createMcpTool(groupId, candidateId, MCP_TOOL_RECORD_PROCESSOR);
        createMcpTool(groupId, incompatibleId, MCP_TOOL_INCOMPATIBLE_PROCESSOR);

        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", sourceId)
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}/compatible")
                .then()
                .statusCode(200)
                .body("count", equalTo(1))
                .body("tools", hasSize(1))
                .body("tools[0].artifactId", equalTo(candidateId));
    }

    @Test
    public void testFindCompatibleToolsNoOutputSchema() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String sourceId = "no-output-source";
        createMcpTool(groupId, sourceId, MCP_TOOL_NO_OUTPUT);

        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", sourceId)
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}/compatible")
                .then()
                .statusCode(200)
                .body("count", equalTo(0))
                .body("tools", hasSize(0));
    }

    @Test
    public void testFindCompatibleToolsSourceNotFound() {
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", "nonexistent-group")
                .pathParam("artifactId", "nonexistent-tool")
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}/compatible")
                .then()
                .statusCode(404);
    }

    private static final String PAGINATED_SOURCE_TOOL = """
            {
                "name": "paginated_source",
                "title": "Paginated Source Tool",
                "description": "Produces unique pagination output property",
                "inputSchema": { "type": "object" },
                "outputSchema": {
                    "type": "object",
                    "properties": {
                        "unique_page_field": { "type": "string" }
                    }
                }
            }
            """;

    private static final String PAGINATED_COMPATIBLE_TOOL = """
            {
                "name": "paginated_compat",
                "title": "Paginated Compatible Tool",
                "description": "Consumes unique pagination output property",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "unique_page_field": { "type": "string" }
                    }
                }
            }
            """;

    @Test
    public void testFindCompatibleToolsPagination() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String sourceId = "paginated-source";

        // Create two compatible candidates so we can paginate
        createMcpTool(groupId, sourceId, PAGINATED_SOURCE_TOOL);
        createMcpTool(groupId, "compat-page-1", PAGINATED_COMPATIBLE_TOOL);
        createMcpTool(groupId, "compat-page-2", PAGINATED_COMPATIBLE_TOOL);

        // Full result: 2 compatible tools, count reflects total
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", sourceId)
                .queryParam("offset", 0)
                .queryParam("limit", 100)
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}/compatible")
                .then()
                .statusCode(200)
                .body("count", equalTo(2))
                .body("tools", hasSize(2));

        // Paginated result: limit=1 returns only one tool but count stays total
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", sourceId)
                .queryParam("offset", 0)
                .queryParam("limit", 1)
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}/compatible")
                .then()
                .statusCode(200)
                .body("count", equalTo(2))
                .body("tools", hasSize(1));

        // Offset beyond results: empty page, count unchanged
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", sourceId)
                .queryParam("offset", 100)
                .queryParam("limit", 10)
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}/compatible")
                .then()
                .statusCode(200)
                .body("count", equalTo(2))
                .body("tools", hasSize(0));
    }

    @Test
    public void testMcpSchemaEndpointHeaders() {
        givenAtRoot()
                .when()
                .get("/.well-known/schemas/mcp-tool/v1")
                .then()
                .statusCode(200)
                .contentType("application/schema+json")
                .header("Content-Disposition", equalTo("inline; filename=\"mcp-tool-v1.json\""))
                .header("Cache-Control", equalTo("public, max-age=86400"))
                .body("$schema", notNullValue())
                .body("title", equalTo("MCP Tool Definition"));
    }

    private void createMcpTool(String groupId, String artifactId, String content) throws Exception {
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.MCP_TOOL);

        CreateVersion createVersion = new CreateVersion();
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(versionContent);
        createArtifact.setFirstVersion(createVersion);

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
    }
}
