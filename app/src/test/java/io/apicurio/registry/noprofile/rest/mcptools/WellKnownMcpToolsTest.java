package io.apicurio.registry.noprofile.rest.mcptools;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.noprofile.rest.a2a.ExperimentalFeaturesEnabledProfile;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
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
import org.junit.jupiter.api.Assertions;
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
    public void testCreateExtractsNameFromContentIntoMetadata() throws Exception {
        // Reproduces #8622: creating an MCP_TOOL artifact without an explicit "name" on the
        // create request should still populate artifact metadata name from the tool content's
        // top-level "name" field, the same way the v2 API already does.
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        String contentName = "content_derived_name_" + TestUtils.generateArtifactId().replace("-", "");
        String toolContent = SEARCH_DATABASE_TOOL.replace("search_database", contentName);

        createMcpTool(groupId, artifactId, toolContent);

        ArtifactMetaData metaData = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).get();
        Assertions.assertNotNull(metaData);
        Assertions.assertEquals(contentName, metaData.getName());
    }

    @Test
    public void testExplicitRequestNameOverridesContentName() throws Exception {
        // The create path extracts metadata from the content first, then lets explicitly
        // provided request values override it. This covers the override half of that
        // behavior: when the request supplies a name, it must win over the MCP tool
        // content's top-level "name".
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        String contentName = "content_derived_name_" + TestUtils.generateArtifactId().replace("-", "");
        String explicitName = "explicit_request_name_" + TestUtils.generateArtifactId().replace("-", "");
        String toolContent = SEARCH_DATABASE_TOOL.replace("search_database", contentName);

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.MCP_TOOL);
        createArtifact.setName(explicitName);

        CreateVersion createVersion = new CreateVersion();
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(toolContent);
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(versionContent);
        createArtifact.setFirstVersion(createVersion);

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        ArtifactMetaData metaData = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).get();
        Assertions.assertNotNull(metaData);
        Assertions.assertEquals(explicitName, metaData.getName());

        // The content-derived name must not have been used, so searching by it finds nothing.
        givenAtRoot()
                .when()
                .queryParam("name", contentName)
                .get("/.well-known/mcp-tools")
                .then()
                .statusCode(200)
                .body("count", equalTo(0));
    }

    @Test
    public void testCreateMcpToolWithNoFirstVersionDoesNotThrow() throws Exception {
        // Regression test: createArtifact supports omitting the first version entirely (an
        // "empty artifact", content added via a separate versions().post() call later). The
        // extract-then-override metadata logic must handle that null-content case rather than
        // NPE'ing when it tries to extract metadata from content that does not exist yet.
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.MCP_TOOL);
        // No firstVersion set - this must not throw.

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        ArtifactMetaData metaData = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).get();
        Assertions.assertNotNull(metaData);
        Assertions.assertNull(metaData.getName());

        // Adding content afterward should still extract the name normally.
        CreateVersion createVersion = new CreateVersion();
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(SEARCH_DATABASE_TOOL);
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(versionContent);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);
    }

    @Test
    public void testSearchMcpToolsByContentDerivedName() throws Exception {
        // Companion to testCreateExtractsNameFromContentIntoMetadata: once the content-derived
        // name lands in artifact metadata, well-known search by that name should find the tool,
        // not just search by artifactId.
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        String contentName = "content_derived_name_" + TestUtils.generateArtifactId().replace("-", "");
        String toolContent = SEARCH_DATABASE_TOOL.replace("search_database", contentName);

        createMcpTool(groupId, artifactId, toolContent);

        givenAtRoot()
                .when()
                .queryParam("name", contentName)
                .get("/.well-known/mcp-tools")
                .then()
                .statusCode(200)
                .body("count", equalTo(1))
                .body("tools", hasSize(1))
                .body("tools[0].artifactId", equalTo(artifactId));
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
