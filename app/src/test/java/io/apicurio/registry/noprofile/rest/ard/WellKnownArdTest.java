package io.apicurio.registry.noprofile.rest.ard;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.noprofile.rest.aicatalog.AiCatalogEnabledProfile;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

/**
 * Tests for the ARD (Agentic Resource Discovery) well-known endpoints.
 */
@QuarkusTest
@TestProfile(AiCatalogEnabledProfile.class)
public class WellKnownArdTest extends AbstractResourceTestBase {

    private String serverRootUrl;

    @BeforeEach
    public void setUpWellKnown() {
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        serverRootUrl = "http://localhost:" + port;
    }

    private RequestSpecification givenAtRoot() {
        return RestAssured.given().baseUri(serverRootUrl);
    }

    private static final String AGENT_CARD_CONTENT = """
            {
                "name": "ArdAgent",
                "description": "An agent used by ARD tests",
                "version": "1.0.0",
                "supportedInterfaces": [
                    { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                ],
                "capabilities": {
                    "streaming": true
                },
                "skills": [
                    {
                        "id": "ard-skill",
                        "name": "ARD Skill",
                        "description": "A skill used for ARD tests",
                        "tags": ["ard"]
                    }
                ],
                "defaultInputModes": ["text"],
                "defaultOutputModes": ["text"]
            }
            """;

    private static final String MCP_TOOL_CONTENT = """
            {
                "name": "ard_lookup",
                "title": "ARD Lookup",
                "description": "Look up ARD entries",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "query": { "type": "string" }
                    },
                    "required": ["query"]
                }
            }
            """;

    @Test
    public void testArdSearchTextQuery() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String unique = TestUtils.generateArtifactId().replace("-", "");
        String agentId = "ard-search-" + unique;

        createAgentCard(groupId, agentId, AGENT_CARD_CONTENT);

        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body("{\"query\": {\"text\": \"ard-search-" + unique + "\"}}")
                .post("/.well-known/ard/search")
                .then()
                .statusCode(200)
                .body("results", hasSize(1))
                .body("results[0].identifier",
                        startsWith("urn:air:"))
                .body("results[0].identifier",
                        containsString(":" + groupId + ":" + agentId))
                .body("results[0].type", equalTo("application/a2a-agent-card+json"))
                .body("results[0].score", equalTo(100))
                .body("results[0].source",
                        startsWith("http://localhost:"))
                .body("pageToken", nullValue());
    }

    @Test
    public void testArdSearchRequiresQueryText() {
        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body("{\"query\": {}}")
                .post("/.well-known/ard/search")
                .then()
                .statusCode(400);
    }

    @Test
    public void testArdSearchFilterByType() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String unique = TestUtils.generateArtifactId().replace("-", "");
        String agentId = "ard-ft-a-" + unique;
        String toolId = "ard-ft-t-" + unique;

        createAgentCard(groupId, agentId, AGENT_CARD_CONTENT);
        createMcpTool(groupId, toolId, MCP_TOOL_CONTENT);

        // Both entries match the text query; the type filter narrows to agents only.
        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body("{\"query\": {\"text\": \"ard-ft-\", \"filter\": "
                        + "{\"type\": [\"application/a2a-agent-card+json\"]}}}")
                .post("/.well-known/ard/search")
                .then()
                .statusCode(200)
                .body("results", hasSize(1))
                .body("results[0].type", equalTo("application/a2a-agent-card+json"))
                .body("results[0].identifier",
                        containsString(":" + groupId + ":" + agentId))
                // Both criteria (text + type) matched -> score 100.
                .body("results[0].score", equalTo(100));

        // And to MCP tools only.
        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body("{\"query\": {\"text\": \"ard-ft-\", \"filter\": "
                        + "{\"type\": [\"mcp-server-card\"]}}}")
                .post("/.well-known/ard/search")
                .then()
                .statusCode(200)
                .body("results", hasSize(1))
                .body("results[0].type", equalTo("application/mcp-server-card+json"))
                .body("results[0].identifier",
                        containsString(":" + groupId + ":" + toolId));
    }

    @Test
    public void testArdSearchUnknownFilterKey() {
        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body("{\"query\": {\"text\": \"something\", "
                        + "\"filter\": {\"unsupported.key\": [\"x\"]}}}")
                .post("/.well-known/ard/search")
                .then()
                .statusCode(400);
    }

    @Test
    public void testArdSearchPagination() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String unique = TestUtils.generateArtifactId().replace("-", "");

        for (int i = 0; i < 3; i++) {
            createAgentCard(groupId, "ard-page-" + unique + "-" + i, AGENT_CARD_CONTENT);
        }

        String pageToken = givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body("{\"query\": {\"text\": \"ard-page-" + unique + "-\"}, \"pageSize\": 2}")
                .post("/.well-known/ard/search")
                .then()
                .statusCode(200)
                .body("results", hasSize(2))
                .body("pageToken", notNullValue())
                .extract().path("pageToken");

        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body("{\"query\": {\"text\": \"ard-page-" + unique + "-\"}, \"pageSize\": 2, "
                        + "\"pageToken\": \"" + pageToken + "\"}")
                .post("/.well-known/ard/search")
                .then()
                .statusCode(200)
                .body("results", hasSize(1))
                .body("pageToken", nullValue());
    }

    @Test
    public void testArdSearchFederationNonNoneStillReturnsOwnResults() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String unique = TestUtils.generateArtifactId().replace("-", "");
        String agentId = "ard-fed-" + unique;

        createAgentCard(groupId, agentId, AGENT_CARD_CONTENT);

        // Only federation "none" is supported; other values are accepted but the registry
        // still returns only its own results.
        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body("{\"query\": {\"text\": \"ard-fed-" + unique + "\"}, "
                        + "\"federation\": \"all\"}")
                .post("/.well-known/ard/search")
                .then()
                .statusCode(200)
                .body("results", hasSize(1))
                .body("results[0].identifier",
                        containsString(":" + groupId + ":" + agentId));
    }

    @Test
    public void testArdListAgents() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String unique = TestUtils.generateArtifactId().replace("-", "");
        String agentId = "ard-list-" + unique;

        createAgentCard(groupId, agentId, AGENT_CARD_CONTENT);

        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .get("/.well-known/ard/agents")
                .then()
                .statusCode(200)
                .body("specVersion", equalTo("1.0"))
                .body("host.displayName", equalTo("Apicurio Registry"))
                .body("entries.url",
                        hasItem(endsWith(groupId + "/" + agentId)));
    }

    @Test
    public void testArdListAgentsTypeFilterNarrowsResults() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String unique = TestUtils.generateArtifactId().replace("-", "");
        String agentId = "ard-lt-a-" + unique;
        String toolId = "ard-lt-t-" + unique;

        createAgentCard(groupId, agentId, AGENT_CARD_CONTENT);
        createMcpTool(groupId, toolId, MCP_TOOL_CONTENT);

        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .queryParam("filter", "type=application/a2a-agent-card+json")
                .get("/.well-known/ard/agents")
                .then()
                .statusCode(200)
                .body("entries.url",
                        hasItem(endsWith(groupId + "/" + agentId)))
                .body("entries.url",
                        not(hasItem(endsWith(groupId + "/" + toolId))))
                .body("entries.type", everyItem(equalTo("application/a2a-agent-card+json")));
    }

    @Test
    public void testArdListAgentsUnsupportedTypeFilterValue() {
        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .queryParam("filter", "type=application/unknown+json")
                .get("/.well-known/ard/agents")
                .then()
                .statusCode(400);
    }

    @Test
    public void testArdExploreTypeFacets() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String unique = TestUtils.generateArtifactId().replace("-", "");
        String agentId = "ard-ex-a-" + unique;
        String toolId = "ard-ex-t-" + unique;

        createAgentCard(groupId, agentId, AGENT_CARD_CONTENT);
        createMcpTool(groupId, toolId, MCP_TOOL_CONTENT);

        // Explore without a query computes facets over all visible entries; this registry
        // contains at least the two entries created above.
        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body("{\"resultType\": {\"facets\": [{\"field\": \"type\"}]}}")
                .post("/.well-known/ard/explore")
                .then()
                .statusCode(200)
                .body("resultType", equalTo("facets"))
                .body("facets.type.buckets.value",
                        hasItems("application/a2a-agent-card+json",
                                "application/mcp-server-card+json"))
                .body("facets.type.buckets.find { it.value == 'application/a2a-agent-card+json' }.count",
                        greaterThanOrEqualTo(1))
                .body("facets.type.buckets.find { it.value == 'application/mcp-server-card+json' }.count",
                        greaterThanOrEqualTo(1));
    }

    @Test
    public void testArdExploreTypeFacetsWithQuery() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String unique = TestUtils.generateArtifactId().replace("-", "");
        String agentId = "ard-exq-" + unique;

        createAgentCard(groupId, agentId, AGENT_CARD_CONTENT);

        // The query narrows the matched set to exactly the created agent card.
        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body("{\"query\": {\"text\": \"ard-exq-" + unique + "\"}, "
                        + "\"resultType\": {\"facets\": [{\"field\": \"type\"}]}}")
                .post("/.well-known/ard/explore")
                .then()
                .statusCode(200)
                .body("resultType", equalTo("facets"))
                .body("facets.type.buckets", hasSize(1))
                .body("facets.type.buckets[0].value", equalTo("application/a2a-agent-card+json"))
                .body("facets.type.buckets[0].count", equalTo(1));
    }

    @Test
    public void testArdExploreUnsupportedFacetField() {
        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body("{\"resultType\": {\"facets\": [{\"field\": \"unsupported.field\"}]}}")
                .post("/.well-known/ard/explore")
                .then()
                .statusCode(400);
    }

    private void createAgentCard(String groupId, String artifactId, String content) {
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AGENT_CARD);

        CreateVersion createVersion = new CreateVersion();
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(versionContent);
        createArtifact.setFirstVersion(createVersion);

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
    }

    private void createMcpTool(String groupId, String artifactId, String content) {
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
