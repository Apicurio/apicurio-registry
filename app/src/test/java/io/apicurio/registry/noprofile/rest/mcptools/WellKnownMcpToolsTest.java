package io.apicurio.registry.noprofile.rest.mcptools;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.noprofile.rest.a2a.ExperimentalFeaturesEnabledProfile;
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests for the MCP tool well-known discovery endpoints.
 */
@QuarkusTest
@TestProfile(ExperimentalFeaturesEnabledProfile.class)
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

    private static final String SEARCH_DATABASE_TOOL = """
            {
                "name": "search_database",
                "title": "Database Search Tool",
                "description": "Search the product database with filters",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "query": { "type": "string", "description": "Search query" },
                        "limit": { "type": "integer", "default": 10 }
                    },
                    "required": ["query"]
                },
                "outputSchema": {
                    "type": "object",
                    "properties": {
                        "results": { "type": "array", "description": "Search results" },
                        "total": { "type": "integer", "description": "Total count" }
                    },
                    "required": ["results", "total"]
                },
                "annotations": {
                    "title": "DB Search",
                    "audience": ["user", "assistant"],
                    "priority": 0.8
                }
            }
            """;

    private static final String GET_WEATHER_TOOL = """
            {
                "name": "get_weather",
                "title": "Weather Lookup",
                "description": "Get the current weather for a location",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "location": { "type": "string", "description": "City name" },
                        "units": { "type": "string", "enum": ["celsius", "fahrenheit"] }
                    },
                    "required": ["location"]
                }
            }
            """;

    @Test
    public void testGetRegisteredMcpTool() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        createMcpTool(groupId, artifactId, SEARCH_DATABASE_TOOL);

        givenAtRoot()
                .when()
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}")
                .then()
                .statusCode(200)
                .body("name", equalTo("search_database"))
                .body("title", equalTo("Database Search Tool"))
                .body("description", equalTo("Search the product database with filters"))
                .body("inputSchema.type", equalTo("object"))
                .body("inputSchema.properties.query.type", equalTo("string"))
                .body("inputSchema.required", hasItem("query"));
    }

    @Test
    public void testGetRegisteredMcpToolNotFound() {
        givenAtRoot()
                .when()
                .pathParam("groupId", "nonexistent-group")
                .pathParam("artifactId", "nonexistent-tool")
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}")
                .then()
                .statusCode(404);
    }

    @Test
    public void testGetNonMcpToolArtifact() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AVRO);

        CreateVersion createVersion = new CreateVersion();
        VersionContent content = new VersionContent();
        content.setContent("{\"type\": \"record\", \"name\": \"Test\", \"fields\": []}");
        content.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(content);
        createArtifact.setFirstVersion(createVersion);

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        givenAtRoot()
                .when()
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}")
                .then()
                .statusCode(404);
    }

    @Test
    public void testSearchMcpTools() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String unique = TestUtils.generateArtifactId().replace("-", "");
        String tool1Id = "wcmcp-s1-" + unique;
        String tool2Id = "wcmcp-s2-" + unique;

        createMcpTool(groupId, tool1Id, SEARCH_DATABASE_TOOL);
        createMcpTool(groupId, tool2Id, GET_WEATHER_TOOL);

        givenAtRoot()
                .when()
                .queryParam("name", "*" + unique + "*")
                .get("/.well-known/mcp-tools")
                .then()
                .statusCode(200)
                .body("count", equalTo(2))
                .body("tools", hasSize(2))
                .body("tools.artifactId", hasItems(tool1Id, tool2Id));
    }

    @Test
    public void testSearchMcpToolsWithPagination() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String unique = TestUtils.generateArtifactId().replace("-", "");
        String prefix = "wcmcp-page-" + unique;

        for (int i = 0; i < 3; i++) {
            createMcpTool(groupId, prefix + "-" + i, SEARCH_DATABASE_TOOL);
        }

        String nameFilter = "*" + prefix + "*";

        String firstPage0 = givenAtRoot()
                .when()
                .queryParam("name", nameFilter)
                .queryParam("offset", 0)
                .queryParam("limit", 2)
                .get("/.well-known/mcp-tools")
                .then()
                .statusCode(200)
                .body("count", equalTo(3))
                .body("tools", hasSize(2))
                .extract().path("tools[0].artifactId");

        givenAtRoot()
                .when()
                .queryParam("name", nameFilter)
                .queryParam("offset", 1)
                .queryParam("limit", 2)
                .get("/.well-known/mcp-tools")
                .then()
                .statusCode(200)
                .body("count", equalTo(3))
                .body("tools", hasSize(2))
                .body("tools[0].artifactId", not(equalTo(firstPage0)));
    }

    @Test
    public void testSearchMcpToolsOffsetBeyondResults() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String unique = TestUtils.generateArtifactId().replace("-", "");
        String artifactId = "wcmcp-beyond-" + unique;

        createMcpTool(groupId, artifactId, SEARCH_DATABASE_TOOL);

        givenAtRoot()
                .when()
                .queryParam("name", "*wcmcp-beyond-" + unique + "*")
                .queryParam("offset", 50)
                .queryParam("limit", 10)
                .get("/.well-known/mcp-tools")
                .then()
                .statusCode(200)
                .body("count", equalTo(1))
                .body("tools", hasSize(0));
    }

    @Test
    public void testSearchMcpToolsMalformedOffset() {
        givenAtRoot()
                .when()
                .queryParam("offset", "not-a-number")
                .get("/.well-known/mcp-tools")
                .then()
                .statusCode(400);
    }

    @Test
    public void testSearchMcpToolsEndpointReturnsCorrectStructure() {
        givenAtRoot()
                .when()
                .get("/.well-known/mcp-tools")
                .then()
                .statusCode(200)
                .body("count", notNullValue())
                .body("tools", notNullValue());
    }

    @Test
    public void testSearchMcpToolsByName() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        createMcpTool(groupId, artifactId, SEARCH_DATABASE_TOOL);
        createMcpTool(groupId, TestUtils.generateArtifactId(), GET_WEATHER_TOOL);

        // name matches registry artifact name / artifactId, not the MCP tool JSON "name" field.
        givenAtRoot()
                .when()
                .queryParam("name", artifactId)
                .get("/.well-known/mcp-tools")
                .then()
                .statusCode(200)
                .body("count", equalTo(1))
                .body("tools", hasSize(1))
                .body("tools[0].artifactId", equalTo(artifactId))
                .body("tools[0].title", equalTo("Database Search Tool"));
    }

    @Test
    public void testSearchMcpToolsByNameWildcard() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String unique = TestUtils.generateArtifactId().replace("-", "");
        String artifactId = "wcmcp-" + unique;

        createMcpTool(groupId, artifactId, SEARCH_DATABASE_TOOL);

        givenAtRoot()
                .when()
                .queryParam("name", "*wcmcp-" + unique + "*")
                .get("/.well-known/mcp-tools")
                .then()
                .statusCode(200)
                .body("count", equalTo(1))
                .body("tools", hasSize(1))
                .body("tools[0].artifactId", equalTo(artifactId));
    }

    @Test
    public void testSearchMcpToolsReturnsParameters() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        createMcpTool(groupId, artifactId, SEARCH_DATABASE_TOOL);

        givenAtRoot()
                .when()
                .queryParam("name", artifactId)
                .get("/.well-known/mcp-tools")
                .then()
                .statusCode(200)
                .body("count", equalTo(1))
                .body("tools", hasSize(1))
                .body("tools[0].artifactId", equalTo(artifactId))
                .body("tools[0].title", equalTo("Database Search Tool"))
                .body("tools[0].parameters", hasItems("query", "limit"));
    }

    @Test
    public void testGetMcpToolSchemaV1() {
        givenAtRoot()
                .when()
                .get("/.well-known/schemas/mcp-tool/v1")
                .then()
                .statusCode(200)
                .body("title", equalTo("MCP Tool Definition"))
                .body("required", hasItems("name", "inputSchema"))
                .body("properties.name.type", equalTo("string"))
                .body("properties.inputSchema.type", equalTo("object"));
    }

    @Test
    public void testGetMcpToolSchemaUnknownVersion() {
        givenAtRoot()
                .when()
                .get("/.well-known/schemas/mcp-tool/v99")
                .then()
                .statusCode(404);
    }

    private static final String COMPAT_SOURCE_TOOL = """
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

    private static final String COMPAT_MATCHING_TOOL = """
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

    private static final String COMPAT_INCOMPATIBLE_TOOL = """
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

    private static final String COMPAT_NO_OUTPUT_TOOL = """
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

    private static final String LARGE_LIMIT_SOURCE_TOOL = """
            {
                "name": "large_limit_source",
                "title": "Large Limit Source Tool",
                "description": "Produces unique large-limit output property",
                "inputSchema": { "type": "object" },
                "outputSchema": {
                    "type": "object",
                    "properties": {
                        "unique_large_limit_field": { "type": "string" }
                    }
                }
            }
            """;

    private static final String LARGE_LIMIT_COMPATIBLE_TOOL = """
            {
                "name": "large_limit_compat",
                "title": "Large Limit Compatible Tool",
                "description": "Consumes unique large-limit output property",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "unique_large_limit_field": { "type": "string" }
                    }
                }
            }
            """;

    @Test
    public void testFindCompatibleToolsSuccess() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String sourceId = "source-db-search";
        String candidateId = "candidate-processor";
        String incompatibleId = "incompatible-proc";

        createMcpTool(groupId, sourceId, COMPAT_SOURCE_TOOL);
        createMcpTool(groupId, candidateId, COMPAT_MATCHING_TOOL);
        createMcpTool(groupId, incompatibleId, COMPAT_INCOMPATIBLE_TOOL);

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
        createMcpTool(groupId, sourceId, COMPAT_NO_OUTPUT_TOOL);

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
    public void testFindCompatibleToolsLargeLimit() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String sourceId = "large-limit-source";

        createMcpTool(groupId, sourceId, LARGE_LIMIT_SOURCE_TOOL);
        createMcpTool(groupId, "large-limit-compat-1", LARGE_LIMIT_COMPATIBLE_TOOL);
        createMcpTool(groupId, "large-limit-compat-2", LARGE_LIMIT_COMPATIBLE_TOOL);

        // A limit larger than the maximum page size is clamped rather than applied literally,
        // for every offset within the result set and for the offset just past its end.
        assertCompatibleToolsPage(groupId, sourceId, 0, Integer.MAX_VALUE, 2);
        assertCompatibleToolsPage(groupId, sourceId, 1, Integer.MAX_VALUE, 1);
        assertCompatibleToolsPage(groupId, sourceId, 2, Integer.MAX_VALUE, 0);
    }

    /**
     * Asserts that a compatible-tools request succeeds and returns the expected page. The total
     * count stays at the two compatible tools created by the caller, independent of the window.
     */
    private void assertCompatibleToolsPage(String groupId, String sourceId, int offset, int limit,
            int expectedToolsOnPage) {
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", sourceId)
                .queryParam("offset", offset)
                .queryParam("limit", limit)
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}/compatible")
                .then()
                .statusCode(200)
                .body("count", equalTo(2))
                .body("tools", hasSize(expectedToolsOnPage));
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
