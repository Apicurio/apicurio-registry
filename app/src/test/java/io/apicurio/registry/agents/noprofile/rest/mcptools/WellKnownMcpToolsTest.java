package io.apicurio.registry.agents.noprofile.rest.mcptools;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.agents.noprofile.rest.a2a.ExperimentalFeaturesEnabledProfile;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.McpCompatibleToolsResults;
import io.apicurio.registry.rest.client.models.McpToolCompatibilityLimitation;
import io.apicurio.registry.rest.client.models.McpToolCompatibilityReason;
import io.apicurio.registry.rest.client.models.McpToolCompatibilityResult;
import io.apicurio.registry.rest.client.models.McpToolCompatibilityResults;
import io.apicurio.registry.rest.client.models.McpToolCompatibilitySide;
import io.apicurio.registry.rest.client.models.McpToolCompatibilityVerdict;
import io.apicurio.registry.rest.client.models.McpToolSearchResult;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.models.WrappedVersionState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static final String COMPAT_RECORDS_CONSUMER_TOOL = """
            {
                "name": "records_consumer",
                "title": "Records Consumer",
                "description": "Consumes the records and total that the source produces",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "records": { "type": "array" },
                        "total": { "type": "integer" }
                    },
                    "required": ["records"]
                }
            }
            """;

    private static final String COMPAT_OPTIONAL_INPUT_TOOL = """
            {
                "name": "record_processor",
                "title": "Record Processor",
                "description": "Also accepts an optional format that the source does not declare",
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

    private static final String COMPAT_UNTYPED_OUTPUT_TOOL = """
            {
                "name": "untyped_output",
                "title": "Untyped Output Tool",
                "description": "Declares object keywords without a type, so it may emit any value",
                "inputSchema": { "type": "object" },
                "outputSchema": {
                    "properties": {
                        "untyped_page_field": { "type": "string" }
                    },
                    "additionalProperties": false
                }
            }
            """;

    private static final String COMPAT_UNTYPED_CONSUMER_TOOL = """
            {
                "name": "untyped_consumer",
                "title": "Untyped Output Consumer",
                "description": "Accepts objects only, so it cannot accept every value the source may emit",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "untyped_page_field": { "type": "string" }
                    },
                    "additionalProperties": false
                }
            }
            """;

    private static final String COMPAT_NESTED_OUTPUT_TOOL = """
            {
                "name": "nested_output",
                "title": "Nested Output Tool",
                "description": "Emits a record nested inside the output object",
                "inputSchema": { "type": "object" },
                "outputSchema": {
                    "type": "object",
                    "properties": {
                        "nested_user": {
                            "type": "object",
                            "properties": { "nested_id": { "type": "string" } },
                            "required": ["nested_id"],
                            "additionalProperties": false
                        }
                    },
                    "required": ["nested_user"],
                    "additionalProperties": false
                }
            }
            """;

    private static final String COMPAT_NESTED_CONSUMER_TOOL = """
            {
                "name": "nested_consumer",
                "title": "Nested Record Consumer",
                "description": "Accepts the nested record that the source emits, or null",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "nested_user": {
                            "type": ["object", "null"],
                            "properties": { "nested_id": { "type": "string" } }
                        }
                    },
                    "required": ["nested_user"]
                }
            }
            """;

    private static final String COMPAT_NESTED_MISMATCH_TOOL = """
            {
                "name": "nested_mismatch",
                "title": "Nested Record Mismatch",
                "description": "Requires another type for the nested property",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "nested_user": {
                            "type": ["object", "null"],
                            "properties": { "nested_id": { "type": "integer" } }
                        }
                    },
                    "required": ["nested_user"]
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

    @Test
    public void testFindCompatibleToolsSuccess() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String sourceId = "source-db-search";
        String candidateId = "candidate-records-consumer";
        String optionalInputId = "candidate-processor";
        String incompatibleId = "incompatible-proc";

        createMcpTool(groupId, sourceId, COMPAT_SOURCE_TOOL);
        createMcpTool(groupId, candidateId, COMPAT_RECORDS_CONSUMER_TOOL);
        createMcpTool(groupId, optionalInputId, COMPAT_OPTIONAL_INPUT_TOOL);
        createMcpTool(groupId, incompatibleId, COMPAT_INCOMPATIBLE_TOOL);

        // The source does not set additionalProperties, so a candidate with an optional typed
        // input the source does not declare cannot be proven compatible and is not returned.
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", sourceId)
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}/compatible")
                .then()
                .statusCode(200)
                .body(artifactIdsInGroup(groupId), hasItem(candidateId))
                .body(artifactIdsInGroup(groupId), not(hasItem(optionalInputId)))
                .body(artifactIdsInGroup(groupId), not(hasItem(incompatibleId)))
                .body(artifactIdsInGroup(groupId), not(hasItem(sourceId)));
    }

    @Test
    public void testFindCompatibleToolsExcludesCandidatesTypedAgainstAnUntypedOutput() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String sourceId = "untyped-output-source";
        String candidateId = "untyped-output-candidate";

        createMcpTool(groupId, sourceId, COMPAT_UNTYPED_OUTPUT_TOOL);
        createMcpTool(groupId, candidateId, COMPAT_UNTYPED_CONSUMER_TOOL);

        // The source declares object keywords but no type, so it may emit a value the candidate rejects.
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", sourceId)
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}/compatible")
                .then()
                .statusCode(200)
                .body(artifactIdsInGroup(groupId), not(hasItem(candidateId)));
    }

    @Test
    public void testFindCompatibleToolsEvaluatesNestedObjects() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String sourceId = "nested-output-source";
        String acceptedId = "nested-output-accepted";
        String rejectedId = "nested-output-rejected";

        createMcpTool(groupId, sourceId, COMPAT_NESTED_OUTPUT_TOOL);
        createMcpTool(groupId, acceptedId, COMPAT_NESTED_CONSUMER_TOOL);
        createMcpTool(groupId, rejectedId, COMPAT_NESTED_MISMATCH_TOOL);

        // The type of the nested property decides both candidates, which were undecided before.
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", sourceId)
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}/compatible")
                .then()
                .statusCode(200)
                .body(artifactIdsInGroup(groupId), hasItem(acceptedId))
                .body(artifactIdsInGroup(groupId), not(hasItem(rejectedId)));
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
                .statusCode(404)
                .body("status", equalTo(404))
                .body("title", equalTo("MCP tool not found: nonexistent-group/nonexistent-tool"));
    }

    @Test
    public void testFindCompatibleToolsPagination() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String sourceId = "paginated-source";

        // Create two compatible candidates so we can paginate
        createMcpTool(groupId, sourceId, PAGINATED_SOURCE_TOOL);
        createMcpTool(groupId, "compat-page-1", PAGINATED_COMPATIBLE_TOOL);
        createMcpTool(groupId, "compat-page-2", PAGINATED_COMPATIBLE_TOOL);

        // The scan covers every MCP tool in the registry, and tools created by other tests stay
        // registered, so pages are checked against the unpaginated count rather than a constant.
        // That count is itself checked here, where the 500 candidate scan cap bounds the page.
        Response unpaginated = givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", sourceId)
                .queryParam("offset", 0)
                .queryParam("limit", 500)
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}/compatible")
                .then()
                .statusCode(200)
                .body(artifactIdsInGroup(groupId), hasItems("compat-page-1", "compat-page-2"))
                .extract()
                .response();
        int total = unpaginated.path("count");
        assertEquals(total, unpaginated.jsonPath().getList("tools").size());

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
                .body("count", equalTo(total))
                .body("tools", hasSize(1));

        // Offset at the end of the results: empty page, count unchanged
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", sourceId)
                .queryParam("offset", total)
                .queryParam("limit", 10)
                .get("/.well-known/mcp-tools/{groupId}/{artifactId}/compatible")
                .then()
                .statusCode(200)
                .body("count", equalTo(total))
                .body("tools", hasSize(0));
    }

    private static final String ROOT_ROUTES = "/.well-known/mcp-tools/{groupId}/{artifactId}";
    private static final String V3_ROUTES = "/apis/registry/v3/well-known/mcp-tools/{groupId}/{artifactId}";
    private static final String COMPATIBLE = "/compatible";
    private static final String COMPATIBILITY = "/compatibility";

    private static final String CATALOG_SOURCE = "catalog-source";
    private static final String CATALOG_COMPATIBLE = "catalog-compatible";
    private static final String CATALOG_INCOMPATIBLE = "catalog-incompatible";
    private static final String CATALOG_INDETERMINATE = "catalog-indeterminate";

    @Test
    public void testToolCompatibilityExplainsEveryVerdict() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createCompatibilityCatalog(groupId);

        Response response = givenSource(groupId, CATALOG_SOURCE)
                .queryParam("limit", 500)
                .get(ROOT_ROUTES + COMPATIBILITY)
                .then()
                .statusCode(200)
                .body(resultsInGroup(groupId) + ".tool.artifactId",
                        containsInAnyOrder(CATALOG_COMPATIBLE, CATALOG_INCOMPATIBLE, CATALOG_INDETERMINATE))
                .body(result(groupId, CATALOG_COMPATIBLE) + ".verdict", equalTo("COMPATIBLE"))
                .body(result(groupId, CATALOG_COMPATIBLE) + ".reasons", hasSize(0))
                .body(result(groupId, CATALOG_COMPATIBLE) + ".limitations", hasSize(0))
                .body(result(groupId, CATALOG_INCOMPATIBLE) + ".verdict", equalTo("INCOMPATIBLE"))
                .body(result(groupId, CATALOG_INCOMPATIBLE) + ".reasons", hasSize(1))
                .body(result(groupId, CATALOG_INCOMPATIBLE) + ".reasons[0].code", equalTo("TYPE_NOT_ACCEPTED"))
                .body(result(groupId, CATALOG_INCOMPATIBLE) + ".reasons[0].producerPointer",
                        equalTo("/outputSchema/properties/records/type"))
                .body(result(groupId, CATALOG_INCOMPATIBLE) + ".reasons[0].consumerPointer",
                        equalTo("/inputSchema/properties/records/type"))
                .body(result(groupId, CATALOG_INCOMPATIBLE) + ".limitations", hasSize(0))
                .body(result(groupId, CATALOG_INDETERMINATE) + ".verdict", equalTo("INDETERMINATE"))
                .body(result(groupId, CATALOG_INDETERMINATE) + ".reasons", hasSize(0))
                .body(result(groupId, CATALOG_INDETERMINATE) + ".limitations", hasSize(1))
                .body(result(groupId, CATALOG_INDETERMINATE) + ".limitations[0].code",
                        equalTo("PRODUCER_OBJECT_OPEN"))
                .body(result(groupId, CATALOG_INDETERMINATE) + ".limitations[0].side", equalTo("PRODUCER"))
                .body(result(groupId, CATALOG_INDETERMINATE) + ".limitations[0].pointer",
                        equalTo("/outputSchema"))
                .body("limitations", hasSize(0))
                .body("scanLimit", equalTo(500))
                .extract()
                .response();

        // Other tests register tools too, so the registry-wide fields are checked against each other.
        int evaluated = response.path("evaluated");
        assertEquals(evaluated, (int) response.path("totals.compatible") + (int) response.path("totals.incompatible")
                + (int) response.path("totals.indeterminate"));
        assertEquals(evaluated, (int) response.path("count"));
        assertEquals(evaluated, response.jsonPath().getList("results").size());
    }

    @Test
    public void testCompatibleReturnsTheToolsThatCompatibilityFindsCompatible() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createCompatibilityCatalog(groupId);

        Response compatible = givenSource(groupId, CATALOG_SOURCE)
                .queryParam("limit", 500)
                .get(ROOT_ROUTES + COMPATIBLE);
        Response compatibility = givenSource(groupId, CATALOG_SOURCE)
                .queryParam("limit", 500)
                .queryParam("verdict", "COMPATIBLE")
                .get(ROOT_ROUTES + COMPATIBILITY);

        assertEquals(200, compatible.statusCode());
        assertEquals(Set.of("count", "tools"), compatible.jsonPath().getMap("$").keySet());
        assertEquals(compatibility.jsonPath().getList("results.tool"), compatible.jsonPath().getList("tools"));
        assertEquals((int) compatibility.path("count"), (int) compatible.path("count"));
        assertEquals(List.of(CATALOG_COMPATIBLE), compatible.jsonPath().getList(artifactIdsInGroup(groupId)));
    }

    @Test
    public void testV3RoutesAnswerLikeTheRootRoutes() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createCompatibilityCatalog(groupId);

        for (String operation : List.of(COMPATIBLE, COMPATIBILITY)) {
            String atRoot = givenSource(groupId, CATALOG_SOURCE).get(ROOT_ROUTES + operation).then()
                    .statusCode(200).extract().asString();
            String atV3 = givenSource(groupId, CATALOG_SOURCE).get(V3_ROUTES + operation).then()
                    .statusCode(200).extract().asString();
            assertEquals(atRoot, atV3, operation);
        }
    }

    @Test
    public void testToolCompatibilityOfSourceWithoutOutputSchema() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String sourceId = "no-output-source";
        createMcpTool(groupId, sourceId, COMPAT_NO_OUTPUT_TOOL);

        givenSource(groupId, sourceId)
                .get(ROOT_ROUTES + COMPATIBILITY)
                .then()
                .statusCode(200)
                .body("candidateTotal", equalTo(0))
                .body("evaluated", equalTo(0))
                .body("scanLimit", equalTo(500))
                .body("complete", equalTo(true))
                .body("totals.compatible", equalTo(0))
                .body("totals.incompatible", equalTo(0))
                .body("totals.indeterminate", equalTo(0))
                .body("count", equalTo(0))
                .body("results", hasSize(0))
                .body("limitations", hasSize(1))
                .body("limitations[0].code", equalTo("SOURCE_HAS_NO_OUTPUT_SCHEMA"))
                .body("limitations[0].side", equalTo("PRODUCER"))
                .body("limitations[0].pointer", equalTo("/outputSchema"))
                .body("limitations[0].message", equalTo("The tool declares no outputSchema"));
    }

    @Test
    public void testToolCompatibilityFiltersByVerdict() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createCompatibilityCatalog(groupId);

        Map<String, Object> totals = givenSource(groupId, CATALOG_SOURCE)
                .queryParam("limit", 500)
                .get(ROOT_ROUTES + COMPATIBILITY)
                .then()
                .statusCode(200)
                .body(resultsInGroup(groupId) + ".tool.artifactId",
                        containsInAnyOrder(CATALOG_COMPATIBLE, CATALOG_INCOMPATIBLE, CATALOG_INDETERMINATE))
                .extract()
                .path("totals");

        givenSource(groupId, CATALOG_SOURCE)
                .queryParam("limit", 500)
                .queryParam("verdict", "INCOMPATIBLE")
                .get(ROOT_ROUTES + COMPATIBILITY)
                .then()
                .statusCode(200)
                .body("results.verdict", everyItem(equalTo("INCOMPATIBLE")))
                .body(resultsInGroup(groupId) + ".tool.artifactId", contains(CATALOG_INCOMPATIBLE))
                .body("count", equalTo(totals.get("incompatible")))
                .body("totals", equalTo(totals));

        givenSource(groupId, CATALOG_SOURCE)
                .queryParam("limit", 500)
                .queryParam("verdict", "COMPATIBLE", "INDETERMINATE")
                .get(ROOT_ROUTES + COMPATIBILITY)
                .then()
                .statusCode(200)
                .body("results.verdict", everyItem(anyOf(equalTo("COMPATIBLE"), equalTo("INDETERMINATE"))))
                .body(resultsInGroup(groupId) + ".tool.artifactId",
                        containsInAnyOrder(CATALOG_COMPATIBLE, CATALOG_INDETERMINATE))
                .body("count", equalTo((int) totals.get("compatible") + (int) totals.get("indeterminate")))
                .body("totals", equalTo(totals));
    }

    @ParameterizedTest
    @ValueSource(strings = {ROOT_ROUTES + COMPATIBILITY, V3_ROUTES + COMPATIBILITY})
    public void testToolCompatibilityRejectsUnknownVerdict(String route) throws Exception {
        String groupId = TestUtils.generateGroupId();
        createMcpTool(groupId, CATALOG_SOURCE, COMPAT_SOURCE_TOOL);

        givenSource(groupId, CATALOG_SOURCE)
                .queryParam("verdict", "compatible")
                .get(route)
                .then()
                .statusCode(400)
                .body("status", equalTo(400))
                .body("name", equalTo("InvalidParameterValueException"))
                .body("title", containsString("'verdict'"));
    }

    @ParameterizedTest
    @MethodSource("compatibilityRoutes")
    public void testMalformedPaginationFailsParameterConversion(String route) throws Exception {
        String groupId = TestUtils.generateGroupId();
        createMcpTool(groupId, CATALOG_SOURCE, COMPAT_SOURCE_TOOL);

        // The source exists, so the 404 can only come from converting the parameter.
        for (String parameter : List.of("offset", "limit")) {
            givenSource(groupId, CATALOG_SOURCE)
                    .queryParam(parameter, "not-a-number")
                    .get(route)
                    .then()
                    .statusCode(404)
                    .body("status", equalTo(404))
                    .body("title", containsString(parameter));
        }
    }

    @ParameterizedTest
    @MethodSource("compatibilityRoutes")
    public void testPaginationBeyondTheIntegerRangeIsClamped(String route) throws Exception {
        String groupId = TestUtils.generateGroupId();
        createCompatibilityCatalog(groupId);
        String items = route.endsWith(COMPATIBLE) ? "tools" : "results";
        int count = givenSource(groupId, CATALOG_SOURCE).get(route).then().statusCode(200)
                .extract().path("count");

        givenSource(groupId, CATALOG_SOURCE)
                .queryParam("offset", "99999999999999")
                .get(route)
                .then()
                .statusCode(200)
                .body("count", equalTo(count))
                .body(items, hasSize(0));

        givenSource(groupId, CATALOG_SOURCE)
                .queryParam("limit", "99999999999999")
                .get(route)
                .then()
                .statusCode(200)
                .body("count", equalTo(count))
                .body(items, hasSize(count));

        givenSource(groupId, CATALOG_SOURCE)
                .queryParam("offset", 1)
                .queryParam("limit", Integer.MAX_VALUE)
                .get(route)
                .then()
                .statusCode(200)
                .body("count", equalTo(count))
                .body(items, hasSize(count - 1));
    }

    @ParameterizedTest
    @MethodSource("compatibilityRoutes")
    public void testNegativePaginationIsNormalized(String route) throws Exception {
        String groupId = TestUtils.generateGroupId();
        createCompatibilityCatalog(groupId);
        String items = route.endsWith(COMPATIBLE) ? "tools" : "results";

        List<Object> firstPage = givenSource(groupId, CATALOG_SOURCE)
                .queryParam("offset", 0)
                .queryParam("limit", 1)
                .get(route)
                .then()
                .statusCode(200)
                .body(items, hasSize(1))
                .extract()
                .jsonPath()
                .getList(items);

        List<Object> normalized = givenSource(groupId, CATALOG_SOURCE)
                .queryParam("offset", -1)
                .queryParam("limit", -1)
                .get(route)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList(items);

        assertEquals(firstPage, normalized);
    }

    @ParameterizedTest
    @MethodSource("compatibilityRoutes")
    public void testPaginationWithoutValueTakesTheDefaults(String route) throws Exception {
        String groupId = TestUtils.generateGroupId();
        createCompatibilityCatalog(groupId);
        String items = route.endsWith(COMPATIBLE) ? "tools" : "results";

        List<Object> defaults = givenSource(groupId, CATALOG_SOURCE)
                .get(route)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList(items);

        List<Object> withoutValue = givenSource(groupId, CATALOG_SOURCE)
                .queryParam("offset", "")
                .queryParam("limit", "")
                .get(route)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList(items);

        assertEquals(defaults, withoutValue);
    }

    @ParameterizedTest
    @MethodSource("compatibilityRoutes")
    public void testCompatibilityOfMissingSourceOrNonMcpArtifactIsNotFound(String route) throws Exception {
        String groupId = TestUtils.generateGroupId();
        String avroId = TestUtils.generateArtifactId();
        createArtifact(groupId, avroId, ArtifactType.AVRO, "{\"type\": \"record\", \"name\": \"Test\", \"fields\": []}",
                ContentTypes.APPLICATION_JSON);

        givenSource(groupId, "nonexistent-tool")
                .get(route)
                .then()
                .statusCode(404)
                .body("status", equalTo(404))
                .body("title", equalTo("MCP tool not found: " + groupId + "/nonexistent-tool"));

        givenSource(groupId, avroId)
                .get(route)
                .then()
                .statusCode(404)
                .body("status", equalTo(404))
                .body("title", equalTo("Artifact is not an MCP tool definition"));
    }

    @Test
    public void testToolCompatibilityComparesTheRequestedSourceVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createArtifact(groupId, CATALOG_SOURCE, ArtifactType.MCP_TOOL, COMPAT_NO_OUTPUT_TOOL,
                ContentTypes.APPLICATION_JSON, artifact -> artifact.getFirstVersion().setVersion("1.0.0"));
        createArtifactVersion(groupId, CATALOG_SOURCE, COMPAT_SOURCE_TOOL, ContentTypes.APPLICATION_JSON);
        createMcpTool(groupId, CATALOG_COMPATIBLE, COMPAT_RECORDS_CONSUMER_TOOL);

        givenSource(groupId, CATALOG_SOURCE)
                .queryParam("limit", 500)
                .get(ROOT_ROUTES + COMPATIBILITY)
                .then()
                .statusCode(200)
                .body("limitations", hasSize(0))
                .body(result(groupId, CATALOG_COMPATIBLE) + ".verdict", equalTo("COMPATIBLE"));

        givenSource(groupId, CATALOG_SOURCE)
                .queryParam("version", "1.0.0")
                .get(ROOT_ROUTES + COMPATIBILITY)
                .then()
                .statusCode(200)
                .body("evaluated", equalTo(0))
                .body("results", hasSize(0))
                .body("limitations.code", contains("SOURCE_HAS_NO_OUTPUT_SCHEMA"));
    }

    @Test
    public void testSourceInTheDefaultGroupIsNotItsOwnCandidate() throws Exception {
        String sourceId = TestUtils.generateArtifactId();
        String candidateId = TestUtils.generateArtifactId();
        createMcpTool("default", sourceId, COMPAT_SOURCE_TOOL);
        createMcpTool("default", candidateId, COMPAT_RECORDS_CONSUMER_TOOL);

        // Tools in the default group have no groupId in the response, and the IDs are unique.
        givenSource("default", sourceId)
                .queryParam("limit", 500)
                .get(ROOT_ROUTES + COMPATIBILITY)
                .then()
                .statusCode(200)
                .body("results.tool.artifactId", hasItem(candidateId))
                .body("results.tool.artifactId", not(hasItem(sourceId)))
                .body("results.find { it.tool.artifactId == '" + candidateId + "' }.verdict",
                        equalTo("COMPATIBLE"));

        givenSource("default", sourceId)
                .queryParam("limit", 500)
                .get(ROOT_ROUTES + COMPATIBLE)
                .then()
                .statusCode(200)
                .body("tools.artifactId", hasItem(candidateId))
                .body("tools.artifactId", not(hasItem(sourceId)));
    }

    private static final String REPORT_SOURCE = "report-source";
    private static final String ACCEPTS_EVERY_OUTPUT = "accepts-every-output";

    private static final String REPORT_SOURCE_TOOL = """
            {
                "name": "report_source",
                "title": "Report Source",
                "description": "Emits a closed object, so every verdict depends only on what it declares",
                "inputSchema": { "type": "object" },
                "outputSchema": {
                    "type": "object",
                    "properties": {
                        "id": { "type": "integer" },
                        "note": { "type": ["string", "null"] },
                        "tags": { "type": "array", "items": { "type": "string" } },
                        "owner": {
                            "type": "object",
                            "properties": { "name": { "type": "string" } },
                            "required": ["name"],
                            "additionalProperties": false
                        },
                        "cursor": { "type": "string" }
                    },
                    "required": ["id", "note", "tags", "owner"],
                    "additionalProperties": false
                }
            }
            """;

    private static final String ACCEPTS_EVERY_OUTPUT_TOOL = """
            {
                "name": "accepts_every_output",
                "title": "Accepts Every Output",
                "description": "Accepts every value the report source emits, its integer id as a nullable number",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "id": { "type": ["number", "null"] },
                        "note": { "type": ["string", "null"] },
                        "tags": { "type": "array", "items": { "type": "string" } },
                        "owner": { "type": "object", "properties": { "name": { "type": "string" } } }
                    },
                    "required": ["id"]
                }
            }
            """;

    private static final String REQUIRES_CURSOR_TOOL = """
            {
                "name": "requires_cursor",
                "title": "Requires Cursor",
                "description": "Requires the cursor, which the report source may leave out",
                "inputSchema": {
                    "type": "object",
                    "properties": { "cursor": { "type": "string" } },
                    "required": ["cursor"]
                }
            }
            """;

    private static final String REJECTS_CURSOR_TOOL = """
            {
                "name": "rejects_cursor",
                "title": "Rejects Cursor",
                "description": "Accepts every report field except the cursor",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "id": { "type": "integer" },
                        "note": { "type": ["string", "null"] },
                        "tags": { "type": "array" },
                        "owner": { "type": "object" }
                    },
                    "additionalProperties": false
                }
            }
            """;

    private static final String REJECTS_NULL_NOTE_TOOL = """
            {
                "name": "rejects_null_note",
                "title": "Rejects Null Note",
                "description": "Rejects the null note that the report source may emit",
                "inputSchema": {
                    "type": "object",
                    "properties": { "note": { "type": "string" } }
                }
            }
            """;

    private static final String NESTED_MISMATCH_TOOL = """
            {
                "name": "nested_mismatch",
                "title": "Nested Mismatch",
                "description": "Expects integer tags and an integer owner name",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "tags": { "type": "array", "items": { "type": "integer" } },
                        "owner": { "type": "object", "properties": { "name": { "type": "integer" } } }
                    }
                }
            }
            """;

    private static final String ENUM_INPUT_TOOL = """
            {
                "name": "enum_input",
                "title": "Enum Input",
                "description": "Restricts the id with enum, which is not evaluated yet",
                "inputSchema": {
                    "type": "object",
                    "properties": { "id": { "type": "integer", "enum": [1, 2] } }
                }
            }
            """;

    private static final String CUSTOM_DIALECT_TOOL = """
            {
                "name": "custom_dialect",
                "title": "Custom Dialect",
                "description": "Declares a JSON Schema dialect that is not supported",
                "inputSchema": { "$schema": "https://example.com/custom-dialect", "type": "object" }
            }
            """;

    private static final String DEEP_INPUT_TOOL = """
            {
                "name": "deep_input",
                "title": "Deep Input",
                "description": "Nests an array deeper than the comparison evaluates",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "l1": { "type": "object", "properties": {
                            "l2": { "type": "object", "properties": {
                                "l3": { "type": "object", "properties": {
                                    "l4": { "type": "object", "properties": {
                                        "l5": { "type": "array", "items": { "type": "string" } }
                                    } }
                                } }
                            } }
                        } }
                    }
                }
            }
            """;

    private static final String NO_INPUT_SCHEMA_TOOL = """
            {
                "name": "no_input_schema",
                "title": "No Input Schema",
                "description": "Declares no inputSchema"
            }
            """;

    private static final String TRUNCATED_TOOL = "{\"name\": \"truncated_tool\", \"inputSchema\": ";

    @ParameterizedTest
    @ValueSource(strings = {ROOT_ROUTES, V3_ROUTES})
    public void testToolCompatibilityExplainsEveryReason(String routes) throws Exception {
        String groupId = TestUtils.generateGroupId();
        createMcpTool(groupId, REPORT_SOURCE, REPORT_SOURCE_TOOL);
        createMcpTool(groupId, ACCEPTS_EVERY_OUTPUT, ACCEPTS_EVERY_OUTPUT_TOOL);
        createMcpTool(groupId, "requires-cursor", REQUIRES_CURSOR_TOOL);
        createMcpTool(groupId, "rejects-cursor", REJECTS_CURSOR_TOOL);
        createMcpTool(groupId, "rejects-null-note", REJECTS_NULL_NOTE_TOOL);
        createMcpTool(groupId, "nested-mismatch", NESTED_MISMATCH_TOOL);

        Response response = givenSource(groupId, REPORT_SOURCE).queryParam("limit", 500)
                .get(routes + COMPATIBILITY);

        assertEquals(200, response.statusCode());
        assertEquals(Map.of(
                ACCEPTS_EVERY_OUTPUT, outcome("COMPATIBLE", List.of(), List.of()),
                "requires-cursor", outcome("INCOMPATIBLE", List.of(reason("REQUIRED_NOT_GUARANTEED",
                        "/outputSchema/required", "/inputSchema/required/0",
                        "Required input 'cursor' is declared by the producer but not required")), List.of()),
                "rejects-cursor", outcome("INCOMPATIBLE", List.of(reason("ADDITIONAL_PROPERTY_NOT_ACCEPTED",
                        "/outputSchema/properties/cursor", "/inputSchema/additionalProperties",
                        "The producer may emit 'cursor', which the consumer does not accept")), List.of()),
                "rejects-null-note", outcome("INCOMPATIBLE", List.of(reason("TYPE_NOT_ACCEPTED",
                        "/outputSchema/properties/note/type", "/inputSchema/properties/note/type",
                        "The producer's value for 'note' is not accepted by the consumer")), List.of()),
                "nested-mismatch", outcome("INCOMPATIBLE", List.of(
                        reason("TYPE_NOT_ACCEPTED", "/outputSchema/properties/owner/properties/name/type",
                                "/inputSchema/properties/owner/properties/name/type",
                                "The producer's value for 'name' is not accepted by the consumer"),
                        reason("TYPE_NOT_ACCEPTED", "/outputSchema/properties/tags/items/type",
                                "/inputSchema/properties/tags/items/type",
                                "The producer may emit array items that the consumer does not accept")), List.of())),
                outcomesInGroup(response, groupId));

        givenSource(groupId, REPORT_SOURCE)
                .queryParam("limit", 500)
                .get(routes + COMPATIBLE)
                .then()
                .statusCode(200)
                .body(artifactIdsInGroup(groupId), contains(ACCEPTS_EVERY_OUTPUT));
    }

    @ParameterizedTest
    @ValueSource(strings = {ROOT_ROUTES, V3_ROUTES})
    public void testToolCompatibilityReportsWhatItCannotCompare(String routes) throws Exception {
        String groupId = TestUtils.generateGroupId();
        createMcpTool(groupId, REPORT_SOURCE, REPORT_SOURCE_TOOL);
        createMcpTool(groupId, ACCEPTS_EVERY_OUTPUT, ACCEPTS_EVERY_OUTPUT_TOOL);
        createMcpTool(groupId, "enum-input", ENUM_INPUT_TOOL);
        createMcpTool(groupId, "custom-dialect", CUSTOM_DIALECT_TOOL);
        createMcpTool(groupId, "deep-input", DEEP_INPUT_TOOL);
        createMcpTool(groupId, "no-input-schema", NO_INPUT_SCHEMA_TOOL);
        createMcpTool(groupId, "truncated", TRUNCATED_TOOL);

        Response response = givenSource(groupId, REPORT_SOURCE).queryParam("limit", 500)
                .get(routes + COMPATIBILITY);

        assertEquals(200, response.statusCode());
        assertEquals(Map.of(
                ACCEPTS_EVERY_OUTPUT, outcome("COMPATIBLE", List.of(), List.of()),
                "enum-input", outcome("INDETERMINATE", List.of(), List.of(consumerLimitation("UNSUPPORTED_KEYWORD",
                        "/inputSchema/properties/id/enum", "'enum' is not evaluated yet"))),
                "custom-dialect", outcome("INDETERMINATE", List.of(), List.of(consumerLimitation(
                        "UNSUPPORTED_DIALECT", "/inputSchema/$schema",
                        "JSON Schema dialect 'https://example.com/custom-dialect' is not supported"))),
                "deep-input", outcome("INDETERMINATE", List.of(), List.of(consumerLimitation("DEPTH_LIMIT_REACHED",
                        "/inputSchema/properties/l1/properties/l2/properties/l3/properties/l4/properties/l5/items",
                        "'items' nests deeper than the comparison evaluates"))),
                "no-input-schema", outcome("INDETERMINATE", List.of(), List.of(consumerLimitation(
                        "COMPARISON_FAILED", "/inputSchema", "The tool declares no inputSchema object"))),
                "truncated", outcome("INDETERMINATE", List.of(), List.of(consumerLimitation("COMPARISON_FAILED",
                        "", "The tool definition is not valid JSON")))),
                outcomesInGroup(response, groupId));

        givenSource(groupId, REPORT_SOURCE)
                .queryParam("limit", 500)
                .get(routes + COMPATIBLE)
                .then()
                .statusCode(200)
                .body(artifactIdsInGroup(groupId), contains(ACCEPTS_EVERY_OUTPUT));
    }

    @ParameterizedTest
    @ValueSource(strings = {ROOT_ROUTES, V3_ROUTES})
    public void testCandidatesWithoutAnEnabledVersionAreSkipped(String routes) throws Exception {
        String groupId = TestUtils.generateGroupId();
        createMcpTool(groupId, REPORT_SOURCE, REPORT_SOURCE_TOOL);
        createMcpTool(groupId, ACCEPTS_EVERY_OUTPUT, ACCEPTS_EVERY_OUTPUT_TOOL);
        createMcpTool(groupId, "disabled-only", ACCEPTS_EVERY_OUTPUT_TOOL);
        disableVersion(groupId, "disabled-only", "1");
        createArtifact(groupId, "draft-only", ArtifactType.MCP_TOOL, ACCEPTS_EVERY_OUTPUT_TOOL,
                ContentTypes.APPLICATION_JSON, artifact -> artifact.getFirstVersion().setIsDraft(true));
        createMcpTool(groupId, "disabled-latest", ACCEPTS_EVERY_OUTPUT_TOOL);
        createArtifactVersion(groupId, "disabled-latest", REJECTS_NULL_NOTE_TOOL, ContentTypes.APPLICATION_JSON);
        disableVersion(groupId, "disabled-latest", "2");

        Response response = givenSource(groupId, REPORT_SOURCE).queryParam("limit", 500)
                .get(routes + COMPATIBILITY);

        // Every candidate is compatible through its first version, so only the version states
        // explain which ones are compared: a disabled latest version falls back to the enabled one.
        assertEquals(200, response.statusCode());
        assertEquals(Map.of(ACCEPTS_EVERY_OUTPUT, outcome("COMPATIBLE", List.of(), List.of()),
                "disabled-latest", outcome("COMPATIBLE", List.of(), List.of())),
                outcomesInGroup(response, groupId));

        givenSource(groupId, REPORT_SOURCE)
                .queryParam("limit", 500)
                .get(routes + COMPATIBLE)
                .then()
                .statusCode(200)
                .body(artifactIdsInGroup(groupId), containsInAnyOrder(ACCEPTS_EVERY_OUTPUT, "disabled-latest"));
    }

    @Test
    public void testCompatibilityOperationsThroughTheGeneratedClient() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createCompatibilityCatalog(groupId);
        var source = clientV3.wellKnown().mcpTools().byGroupId(groupId).byArtifactId(CATALOG_SOURCE);

        McpToolCompatibilityResults compatibility = source.compatibility()
                .get(config -> config.queryParameters.limit = 500);
        Map<String, McpToolCompatibilityResult> results = compatibility.getResults().stream()
                .filter(result -> groupId.equals(result.getTool().getGroupId()))
                .collect(Collectors.toMap(result -> result.getTool().getArtifactId(), result -> result));

        assertEquals(Set.of(CATALOG_COMPATIBLE, CATALOG_INCOMPATIBLE, CATALOG_INDETERMINATE), results.keySet());
        assertEquals(McpToolCompatibilityVerdict.COMPATIBLE, results.get(CATALOG_COMPATIBLE).getVerdict());
        assertEquals(McpToolCompatibilityVerdict.INCOMPATIBLE, results.get(CATALOG_INCOMPATIBLE).getVerdict());
        McpToolCompatibilityReason reason = results.get(CATALOG_INCOMPATIBLE).getReasons().get(0);
        assertEquals("TYPE_NOT_ACCEPTED", reason.getCode());
        assertEquals("/outputSchema/properties/records/type", reason.getProducerPointer());
        assertEquals("/inputSchema/properties/records/type", reason.getConsumerPointer());
        assertEquals(McpToolCompatibilityVerdict.INDETERMINATE, results.get(CATALOG_INDETERMINATE).getVerdict());
        McpToolCompatibilityLimitation limitation = results.get(CATALOG_INDETERMINATE).getLimitations().get(0);
        assertEquals("PRODUCER_OBJECT_OPEN", limitation.getCode());
        assertEquals(McpToolCompatibilitySide.PRODUCER, limitation.getSide());
        assertEquals("/outputSchema", limitation.getPointer());
        assertEquals(compatibility.getEvaluated(), compatibility.getTotals().getCompatible()
                + compatibility.getTotals().getIncompatible() + compatibility.getTotals().getIndeterminate());
        assertEquals(500, compatibility.getScanLimit());
        assertEquals(List.of(), compatibility.getLimitations());

        McpCompatibleToolsResults compatible = source.compatible().get(config -> config.queryParameters.limit = 500);
        assertEquals(List.of(CATALOG_COMPATIBLE), compatible.getTools().stream()
                .filter(tool -> groupId.equals(tool.getGroupId()))
                .map(McpToolSearchResult::getArtifactId)
                .toList());
    }

    @Test
    public void testGeneratedClientReadsEveryCompatibilityField() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createCompatibilityCatalog(groupId);

        // The same request on the wire, which every field the client returns is compared with.
        JsonPath wire = givenSource(groupId, CATALOG_SOURCE)
                .queryParam("limit", 500)
                .get(V3_ROUTES + COMPATIBILITY)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();
        McpToolCompatibilityResults compatibility = clientV3.wellKnown().mcpTools().byGroupId(groupId)
                .byArtifactId(CATALOG_SOURCE).compatibility().get(config -> config.queryParameters.limit = 500);

        assertEquals(wire.getInt("candidateTotal"), compatibility.getCandidateTotal());
        assertEquals(wire.getInt("evaluated"), compatibility.getEvaluated());
        assertEquals(wire.getInt("scanLimit"), compatibility.getScanLimit());
        assertEquals(wire.getBoolean("complete"), compatibility.getComplete());
        assertEquals(wire.getInt("totals.compatible"), compatibility.getTotals().getCompatible());
        assertEquals(wire.getInt("totals.incompatible"), compatibility.getTotals().getIncompatible());
        assertEquals(wire.getInt("totals.indeterminate"), compatibility.getTotals().getIndeterminate());
        assertEquals(wire.getInt("count"), compatibility.getCount());
        assertEquals(List.of(), wire.getList("limitations"));
        assertEquals(List.of(), compatibility.getLimitations());
        // Read once as maps: a path evaluated per field takes minutes over a registry-wide scan.
        List<Map<String, Object>> wireResults = wire.getList("results");
        assertEquals(wireResults.size(), compatibility.getResults().size());
        for (int i = 0; i < wireResults.size(); i++) {
            assertSameResult(wireResults.get(i), compatibility.getResults().get(i));
        }

        // Explicit values for this test's tools, so that a field lost on both sides cannot pass.
        Map<String, McpToolCompatibilityResult> results = compatibility.getResults().stream()
                .filter(result -> groupId.equals(result.getTool().getGroupId()))
                .collect(Collectors.toMap(result -> result.getTool().getArtifactId(), result -> result));
        assertEquals(Set.of(CATALOG_COMPATIBLE, CATALOG_INCOMPATIBLE, CATALOG_INDETERMINATE), results.keySet());
        McpToolSearchResult compatibleTool = results.get(CATALOG_COMPATIBLE).getTool();
        assertEquals("Records Consumer", compatibleTool.getTitle());
        assertEquals(List.of("records", "total"), compatibleTool.getParameters());
        assertFalse(results.get(CATALOG_INCOMPATIBLE).getReasons().get(0).getMessage().isBlank());
        assertFalse(results.get(CATALOG_INDETERMINATE).getLimitations().get(0).getMessage().isBlank());
    }

    @Test
    public void testGeneratedClientCompatibleReturnsTheCompatibleVerdicts() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createCompatibilityCatalog(groupId);
        var source = clientV3.wellKnown().mcpTools().byGroupId(groupId).byArtifactId(CATALOG_SOURCE);

        McpCompatibleToolsResults compatible = source.compatible().get(config -> config.queryParameters.limit = 500);
        McpToolCompatibilityResults compatibility = source.compatibility().get(config -> {
            config.queryParameters.verdict = new String[] {"COMPATIBLE"};
            config.queryParameters.limit = 500;
        });

        List<String> compatibleTools = compatible.getTools().stream().map(WellKnownMcpToolsTest::identity).toList();
        assertEquals(compatibility.getResults().stream().map(result -> identity(result.getTool())).toList(),
                compatibleTools);
        assertEquals(compatibility.getTotals().getCompatible(), compatible.getCount());
        assertEquals(compatibility.getCount(), compatible.getCount());
        assertTrue(compatibleTools.contains(groupId + "/" + CATALOG_COMPATIBLE));
        assertFalse(compatibleTools.contains(groupId + "/" + CATALOG_INCOMPATIBLE));
        assertFalse(compatibleTools.contains(groupId + "/" + CATALOG_INDETERMINATE));
    }

    @Test
    public void testGeneratedClientSendsVersionVerdictAndOffsetTogether() throws Exception {
        String groupId = TestUtils.generateGroupId();
        // Only version 1.0.0 can be compared, since the latest one has no outputSchema.
        createArtifact(groupId, CATALOG_SOURCE, ArtifactType.MCP_TOOL, COMPAT_SOURCE_TOOL,
                ContentTypes.APPLICATION_JSON, artifact -> artifact.getFirstVersion().setVersion("1.0.0"));
        createArtifactVersion(groupId, CATALOG_SOURCE, COMPAT_NO_OUTPUT_TOOL, ContentTypes.APPLICATION_JSON);
        createMcpTool(groupId, CATALOG_COMPATIBLE, COMPAT_RECORDS_CONSUMER_TOOL);
        createMcpTool(groupId, CATALOG_INCOMPATIBLE, COMPAT_INCOMPATIBLE_TOOL);
        createMcpTool(groupId, CATALOG_INDETERMINATE, COMPAT_OPTIONAL_INPUT_TOOL);
        var source = clientV3.wellKnown().mcpTools().byGroupId(groupId).byArtifactId(CATALOG_SOURCE);
        String[] verdicts = {"INCOMPATIBLE", "INDETERMINATE"};

        McpToolCompatibilityResults unpaginated = source.compatibility().get(config -> {
            config.queryParameters.version = "1.0.0";
            config.queryParameters.verdict = verdicts;
            config.queryParameters.limit = 500;
        });
        McpToolCompatibilityResults page = source.compatibility().get(config -> {
            config.queryParameters.version = "1.0.0";
            config.queryParameters.verdict = verdicts;
            config.queryParameters.offset = 1;
            config.queryParameters.limit = 500;
        });

        // This test's candidates are the newest tools, so the two it expects lead the results.
        List<String> matching = unpaginated.getResults().stream().map(result -> identity(result.getTool())).toList();
        assertEquals(Set.of(groupId + "/" + CATALOG_INCOMPATIBLE, groupId + "/" + CATALOG_INDETERMINATE),
                Set.copyOf(matching.subList(0, 2)));
        assertFalse(matching.contains(groupId + "/" + CATALOG_COMPATIBLE));
        assertEquals(matching.size(), unpaginated.getCount());
        // The offset skips the first of them, and count still covers every match.
        assertEquals(matching.subList(1, matching.size()),
                page.getResults().stream().map(result -> identity(result.getTool())).toList());
        assertEquals(unpaginated.getCount(), page.getCount());

        // Without the version the latest one is compared, which yields nothing.
        McpToolCompatibilityResults latest = source.compatibility().get(config -> config.queryParameters.verdict = verdicts);
        assertEquals(0, latest.getEvaluated());
        assertEquals(List.of("SOURCE_HAS_NO_OUTPUT_SCHEMA"),
                latest.getLimitations().stream().map(McpToolCompatibilityLimitation::getCode).toList());
    }

    @Test
    public void testGeneratedClientReadsErrorsAsProblemDetails() throws Exception {
        String groupId = TestUtils.generateGroupId();
        var missing = clientV3.wellKnown().mcpTools().byGroupId(groupId).byArtifactId("nonexistent-tool");
        String notFoundTitle = "MCP tool not found: " + groupId + "/nonexistent-tool";

        ProblemDetails compatibleNotFound = assertThrows(ProblemDetails.class, () -> missing.compatible().get());
        assertEquals(404, compatibleNotFound.getResponseStatusCode());
        assertEquals(404, compatibleNotFound.getStatus());
        assertEquals(notFoundTitle, compatibleNotFound.getTitle());

        ProblemDetails compatibilityNotFound = assertThrows(ProblemDetails.class, () -> missing.compatibility().get());
        assertEquals(404, compatibilityNotFound.getResponseStatusCode());
        assertEquals(404, compatibilityNotFound.getStatus());
        assertEquals(notFoundTitle, compatibilityNotFound.getTitle());

        createMcpTool(groupId, CATALOG_SOURCE, COMPAT_SOURCE_TOOL);
        ProblemDetails invalidVerdict = assertThrows(ProblemDetails.class, () -> clientV3.wellKnown().mcpTools()
                .byGroupId(groupId).byArtifactId(CATALOG_SOURCE).compatibility()
                .get(config -> config.queryParameters.verdict = new String[] {"compatible"}));
        assertEquals(400, invalidVerdict.getResponseStatusCode());
        assertEquals(400, invalidVerdict.getStatus());
        assertEquals("InvalidParameterValueException", invalidVerdict.getName());
        assertTrue(invalidVerdict.getTitle().contains("'verdict'"));
    }

    @SuppressWarnings("unchecked")
    private static void assertSameResult(Map<String, Object> wire, McpToolCompatibilityResult result) {
        Map<String, Object> wireTool = (Map<String, Object>) wire.get("tool");
        McpToolSearchResult tool = result.getTool();
        assertEquals(wireTool.get("groupId"), tool.getGroupId());
        assertEquals(wireTool.get("artifactId"), tool.getArtifactId());
        assertEquals(wireTool.get("name"), tool.getName());
        assertEquals(wireTool.get("title"), tool.getTitle());
        assertEquals(wireTool.get("description"), tool.getDescription());
        assertEquals(wireTool.get("owner"), tool.getOwner());
        assertEquals(((Number) wireTool.get("createdOn")).longValue(), tool.getCreatedOn());
        assertEquals(wireTool.get("parameters"), tool.getParameters());
        assertEquals(wire.get("verdict"), result.getVerdict().getValue());

        List<Map<String, Object>> wireReasons = (List<Map<String, Object>>) wire.get("reasons");
        assertEquals(wireReasons.size(), result.getReasons().size());
        for (int i = 0; i < wireReasons.size(); i++) {
            McpToolCompatibilityReason reason = result.getReasons().get(i);
            assertEquals(wireReasons.get(i).get("code"), reason.getCode());
            assertEquals(wireReasons.get(i).get("producerPointer"), reason.getProducerPointer());
            assertEquals(wireReasons.get(i).get("consumerPointer"), reason.getConsumerPointer());
            assertEquals(wireReasons.get(i).get("message"), reason.getMessage());
        }
        List<Map<String, Object>> wireLimitations = (List<Map<String, Object>>) wire.get("limitations");
        assertEquals(wireLimitations.size(), result.getLimitations().size());
        for (int i = 0; i < wireLimitations.size(); i++) {
            McpToolCompatibilityLimitation limitation = result.getLimitations().get(i);
            assertEquals(wireLimitations.get(i).get("code"), limitation.getCode());
            assertEquals(wireLimitations.get(i).get("side"), limitation.getSide().getValue());
            assertEquals(wireLimitations.get(i).get("pointer"), limitation.getPointer());
            assertEquals(wireLimitations.get(i).get("message"), limitation.getMessage());
        }
    }

    private static String identity(McpToolSearchResult tool) {
        return tool.getGroupId() + "/" + tool.getArtifactId();
    }

    private RequestSpecification givenSource(String groupId, String artifactId) {
        return givenAtRoot()
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId);
    }

    static List<String> compatibilityRoutes() {
        return List.of(ROOT_ROUTES + COMPATIBLE, ROOT_ROUTES + COMPATIBILITY, V3_ROUTES + COMPATIBLE,
                V3_ROUTES + COMPATIBILITY);
    }

    /**
     * A source whose open {@code outputSchema} makes one candidate compatible, one incompatible
     * and one indeterminate.
     */
    private void createCompatibilityCatalog(String groupId) throws Exception {
        createMcpTool(groupId, CATALOG_SOURCE, COMPAT_SOURCE_TOOL);
        createMcpTool(groupId, CATALOG_COMPATIBLE, COMPAT_RECORDS_CONSUMER_TOOL);
        createMcpTool(groupId, CATALOG_INCOMPATIBLE, COMPAT_INCOMPATIBLE_TOOL);
        createMcpTool(groupId, CATALOG_INDETERMINATE, COMPAT_OPTIONAL_INPUT_TOOL);
    }

    private static String resultsInGroup(String groupId) {
        return "results.findAll { it.tool.groupId == '" + groupId + "' }";
    }

    private static String result(String groupId, String artifactId) {
        return "results.find { it.tool.groupId == '" + groupId + "' && it.tool.artifactId == '" + artifactId + "' }";
    }

    private static String artifactIdsInGroup(String groupId) {
        return "tools.findAll { it.groupId == '" + groupId + "' }.artifactId";
    }

    /**
     * The verdict, reasons and limitations of every candidate in the group, by artifact ID.
     */
    private static Map<String, Map<String, Object>> outcomesInGroup(Response response, String groupId) {
        List<Map<String, Object>> results = response.path(resultsInGroup(groupId));
        return results.stream().collect(Collectors.toMap(
                result -> (String) ((Map<?, ?>) result.get("tool")).get("artifactId"),
                result -> outcome(result.get("verdict"), result.get("reasons"), result.get("limitations"))));
    }

    private static Map<String, Object> outcome(Object verdict, Object reasons, Object limitations) {
        return Map.of("verdict", verdict, "reasons", reasons, "limitations", limitations);
    }

    private static Map<String, String> reason(String code, String producerPointer, String consumerPointer,
            String message) {
        return Map.of("code", code, "producerPointer", producerPointer, "consumerPointer", consumerPointer,
                "message", message);
    }

    private static Map<String, String> consumerLimitation(String code, String pointer, String message) {
        return Map.of("code", code, "side", "CONSUMER", "pointer", pointer, "message", message);
    }

    private void disableVersion(String groupId, String artifactId, String version) throws Exception {
        WrappedVersionState disabled = new WrappedVersionState();
        disabled.setState(VersionState.DISABLED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression(version).state().put(disabled);
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
