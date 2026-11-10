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
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
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
