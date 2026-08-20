package io.apicurio.registry.noprofile.rest.a2a;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests for the A2A well-known endpoint.
 */
@QuarkusTest
@TestProfile(ExperimentalFeaturesEnabledProfile.class)
public class WellKnownResourceTest extends AbstractResourceTestBase {

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
                "name": "TestAgent",
                "description": "A test AI agent",
                "version": "1.0.0",
                "supportedInterfaces": [
                    { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                ],
                "capabilities": {
                    "streaming": true,
                    "pushNotifications": false
                },
                "skills": [
                    {
                        "id": "test-skill",
                        "name": "Test Skill",
                        "description": "A test skill",
                        "tags": ["testing"]
                    }
                ],
                "defaultInputModes": ["text"],
                "defaultOutputModes": ["text"]
            }
            """;

    private static final String STREAMING_AGENT_CARD = """
            {
                "name": "StreamingAgent",
                "description": "An agent with streaming capabilities",
                "version": "2.0.0",
                "supportedInterfaces": [
                    { "url": "https://example.com/streaming-agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                ],
                "capabilities": {
                    "streaming": true,
                    "pushNotifications": true
                },
                "skills": [
                    {
                        "id": "data-processing",
                        "name": "Data Processing",
                        "description": "Process data streams",
                        "tags": ["data"]
                    },
                    {
                        "id": "real-time-analysis",
                        "name": "Real-time Analysis",
                        "description": "Analyze data in real time",
                        "tags": ["analysis"]
                    }
                ],
                "defaultInputModes": ["text", "image"],
                "defaultOutputModes": ["text"]
            }
            """;

    private static final String MCP_TOOL_CONTENT = """
            {
                "name": "get_weather",
                "description": "Get the current weather for a city",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "city": { "type": "string" }
                    },
                    "required": ["city"]
                }
            }
            """;

    private static final String STALE_DRAFT_AGENT_CARD = """
            {
                "name": "StaleDraftAgent",
                "description": "A draft revision that must not reach the structured content index",
                "version": "1.0.1",
                "supportedInterfaces": [
                    { "url": "https://example.com/stale-agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                ],
                "capabilities": {
                    "streaming": false,
                    "pushNotifications": false
                },
                "skills": [
                    {
                        "id": "stale-draft-only-skill",
                        "name": "Stale Draft Only Skill",
                        "description": "Present only in a non-latest DRAFT version",
                        "tags": ["stale"]
                    }
                ],
                "defaultInputModes": ["text"],
                "defaultOutputModes": ["text"]
            }
            """;

    private ValidatableResponse searchAgentsBySkill(String skill) {
        String requestBody = """
                {
                    "filters": { "skills": ["%s"] },
                    "limit": 50,
                    "offset": 0
                }
                """.formatted(skill);

        return givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200);
    }

    @Test
    public void testGetAgentCard() {
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .get("/.well-known/agent.json")
                .then()
                .statusCode(200)
                .body("name", equalTo("Apicurio Registry"))
                .body("description", equalTo("API and Schema Registry with A2A Agent support"))
                .body("version", notNullValue())
                .body("protocolVersion", notNullValue())
                .body("provider.organization", equalTo("Apicurio"))
                .body("provider.url", equalTo("https://www.apicur.io"))
                .body("supportedInterfaces", hasSize(1))
                .body("supportedInterfaces[0].protocolBinding", equalTo("http+json"))
                .body("capabilities.streaming", equalTo(false))
                .body("capabilities.pushNotifications", equalTo(false))
                .body("capabilities.extendedAgentCard", equalTo(false))
                .body("skills", hasSize(5))
                .body("skills.id", hasItem("schema-validation"))
                .body("skills.id", hasItem("schema-search"))
                .body("skills.id", hasItem("artifact-management"))
                .body("skills.id", hasItem("compatibility-check"))
                .body("skills.id", hasItem("agent-discovery"))
                .body("defaultInputModes", hasItem("text/plain"))
                .body("defaultOutputModes", hasItem("text/plain"))
                .body("securitySchemes", notNullValue());
    }

    @Test
    public void testGetAgentCardViaA2APath() {
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .get("/.well-known/a2a")
                .then()
                .statusCode(200)
                .body("name", equalTo("Apicurio Registry"))
                .body("supportedInterfaces", hasSize(1))
                .body("capabilities.extendedAgentCard", equalTo(false));
    }

    @Test
    public void testGetRegisteredAgentCard() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create an agent card artifact using the client
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AGENT_CARD);

        CreateVersion createVersion = new CreateVersion();
        VersionContent content = new VersionContent();
        content.setContent(AGENT_CARD_CONTENT);
        content.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(content);
        createArtifact.setFirstVersion(createVersion);

        CreateArtifactResponse response = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Now retrieve it via the well-known endpoint
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .get("/.well-known/agents/{groupId}/{artifactId}")
                .then()
                .statusCode(200)
                .body("name", equalTo("TestAgent"))
                .body("description", equalTo("A test AI agent"))
                .body("version", equalTo("1.0.0"))
                .body("supportedInterfaces", hasSize(1))
                .body("supportedInterfaces[0].url", equalTo("https://example.com/agent"))
                .body("supportedInterfaces[0].protocolBinding", equalTo("http+json"))
                .body("capabilities.streaming", equalTo(true))
                .body("skills", hasSize(1))
                .body("skills[0].id", equalTo("test-skill"));
    }

    @Test
    public void testGetRegisteredAgentCardNotFound() {
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", "nonexistent-group")
                .pathParam("artifactId", "nonexistent-agent")
                .get("/.well-known/agents/{groupId}/{artifactId}")
                .then()
                .statusCode(404);
    }

    @Test
    public void testGetNonAgentCardArtifact() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create an Avro schema (not an agent card)
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

        // Try to retrieve it via the well-known endpoint - should fail
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .get("/.well-known/agents/{groupId}/{artifactId}")
                .then()
                .statusCode(404);
    }

    @Test
    public void testSearchAgents() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create two agent cards
        createAgentCard(groupId, "agent1", AGENT_CARD_CONTENT);
        createAgentCard(groupId, "agent2", STREAMING_AGENT_CARD);

        // Search for all agents (no filters)
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .get("/.well-known/agents")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(2))
                .body("agents", notNullValue());
    }

    @Test
    public void testSearchAgentsPartialNameMatch() throws Exception {
        String groupId = TestUtils.generateGroupId();

        createAgentCard(groupId, "partialmatchagent-alpha", AGENT_CARD_CONTENT);

        // The name filter is documented as a partial match, so a substring should match even
        // though the caller did not supply any wildcards.
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .queryParam("name", "partialmatchagent")
                .get("/.well-known/agents")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("agents.artifactId", hasItem("partialmatchagent-alpha"));
    }

    @Test
    public void testSearchAgentsExplicitWildcardIsPreserved() throws Exception {
        String groupId = TestUtils.generateGroupId();

        createAgentCard(groupId, "explicitwildcardagent-alpha", AGENT_CARD_CONTENT);

        // A caller-supplied wildcard must still work (the value is not wrapped a second time).
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .queryParam("name", "*explicitwildcardagent*")
                .get("/.well-known/agents")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("agents.artifactId", hasItem("explicitwildcardagent-alpha"));
    }

    @Test
    public void testSearchAgentsPartialWildcardIsPreserved() throws Exception {
        String groupId = TestUtils.generateGroupId();

        createAgentCard(groupId, "boundedwildcardagent-alpha", AGENT_CARD_CONTENT);
        createAgentCard(groupId, "zzz-boundedwildcardagent-beta", AGENT_CARD_CONTENT);

        // A prefix-only search stays a prefix search - if the value were wrapped again it would
        // also match the agent that only contains the term in the middle.
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .queryParam("name", "boundedwildcardagent*")
                .get("/.well-known/agents")
                .then()
                .statusCode(200)
                .body("agents.artifactId", hasItem("boundedwildcardagent-alpha"))
                .body("agents.artifactId", not(hasItem("zzz-boundedwildcardagent-beta")));

        // Same for a suffix-only search.
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .queryParam("name", "*boundedwildcardagent-beta")
                .get("/.well-known/agents")
                .then()
                .statusCode(200)
                .body("agents.artifactId", hasItem("zzz-boundedwildcardagent-beta"))
                .body("agents.artifactId", not(hasItem("boundedwildcardagent-alpha")));
    }

    @Test
    public void testSearchMcpToolsPartialNameMatch() throws Exception {
        String groupId = TestUtils.generateGroupId();

        createMcpTool(groupId, "partialmatchtool-alpha", MCP_TOOL_CONTENT);

        // Same partial-match behaviour is documented for the MCP tool discovery endpoint.
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .queryParam("name", "partialmatchtool")
                .get("/.well-known/mcp-tools")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("tools.artifactId", hasItem("partialmatchtool-alpha"));
    }

    @Test
    public void testSearchAgentsWhitespaceNameDoesNotMatchAll() throws Exception {
        // Whitespace-only name should be trimmed to empty string rather than wrapped into "**".
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .queryParam("name", "   ")
                .get("/.well-known/agents")
                .then()
                .statusCode(200);
    }

    @Test
    public void testSearchAgentsWithPagination() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create multiple agent cards
        for (int i = 0; i < 3; i++) {
            createAgentCard(groupId, "agent-page-" + i, AGENT_CARD_CONTENT);
        }

        // Search with pagination
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .queryParam("offset", 0)
                .queryParam("limit", 2)
                .get("/.well-known/agents")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(3))
                .body("agents", hasSize(2));
    }

    @Test
    public void testSearchAgentsEndpointReturnsCorrectStructure() throws Exception {
        // Test that the search endpoint returns the expected structure
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .get("/.well-known/agents")
                .then()
                .statusCode(200)
                .body("count", notNullValue())
                .body("agents", notNullValue());
    }

    @Test
    public void testGetPublicAgents() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create an agent card and mark it as public
        createAgentCard(groupId, "public-agent", AGENT_CARD_CONTENT);
        setVisibility(groupId, "public-agent", "public");

        // Create an agent card without public label
        createAgentCard(groupId, "private-agent", STREAMING_AGENT_CARD);

        // Public endpoint should return only the public agent
        givenAtRoot()
                .when()
                .get("/.well-known/agents/public")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("agents.artifactId", hasItem("public-agent"));
    }

    @Test
    public void testGetPublicAgentsNoAuthRequired() {
        givenAtRoot()
                .when()
                .get("/.well-known/agents/public")
                .then()
                .statusCode(200)
                .body("count", notNullValue())
                .body("agents", notNullValue());
    }

    @Test
    public void testGetEntitledAgents() throws Exception {
        String groupId = TestUtils.generateGroupId();

        createAgentCard(groupId, "entitled-agent-1", AGENT_CARD_CONTENT);
        createAgentCard(groupId, "entitled-agent-2", STREAMING_AGENT_CARD);

        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .get("/.well-known/agents/entitled")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(2))
                .body("agents", notNullValue());
    }

    @Test
    public void testSearchAgentsAdvanced() throws Exception {
        String groupId = TestUtils.generateGroupId();

        createAgentCard(groupId, "search-agent-1", AGENT_CARD_CONTENT);
        createAgentCard(groupId, "search-agent-2", STREAMING_AGENT_CARD);

        String requestBody = """
                {
                    "limit": 50,
                    "offset": 0
                }
                """;

        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(2))
                .body("agents", notNullValue());
    }

    @Test
    public void testSearchAgentsAdvancedWithFilters() throws Exception {
        String groupId = TestUtils.generateGroupId();

        createAgentCard(groupId, "filter-agent", AGENT_CARD_CONTENT);
        setVisibility(groupId, "filter-agent", "public");

        String requestBody = """
                {
                    "filters": {
                        "labels": {
                            "apicurio.agent.visibility": "public"
                        }
                    },
                    "limit": 20,
                    "offset": 0
                }
                """;

        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("agents.artifactId", hasItem("filter-agent"));
    }

    @Test
    public void testSearchAgentsBySkillFilter() throws Exception {
        String groupId = TestUtils.generateGroupId();

        createAgentCard(groupId, "skill-agent-basic", AGENT_CARD_CONTENT);
        createAgentCard(groupId, "skill-agent-streaming", STREAMING_AGENT_CARD);

        String requestBody = """
                {
                    "filters": {
                        "skills": ["data-processing"]
                    },
                    "limit": 50,
                    "offset": 0
                }
                """;

        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("agents.artifactId", hasItem("skill-agent-streaming"))
                .body("agents.artifactId", not(hasItem("skill-agent-basic")));
    }

    @Test
    public void testSearchAgentsByCapabilityFilter() throws Exception {
        String groupId = TestUtils.generateGroupId();

        createAgentCard(groupId, "cap-agent-basic", AGENT_CARD_CONTENT);
        createAgentCard(groupId, "cap-agent-streaming", STREAMING_AGENT_CARD);

        // Positive filter: only the streaming agent has pushNotifications enabled
        String requestBody = """
                {
                    "filters": {
                        "capabilities": { "pushNotifications": true }
                    },
                    "limit": 50,
                    "offset": 0
                }
                """;

        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("agents.artifactId", hasItem("cap-agent-streaming"))
                .body("agents.artifactId", not(hasItem("cap-agent-basic")));

        // Negated filter: pushNotifications=false must exclude the streaming agent
        String negatedRequestBody = """
                {
                    "filters": {
                        "capabilities": { "pushNotifications": false }
                    },
                    "limit": 50,
                    "offset": 0
                }
                """;

        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body(negatedRequestBody)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("agents.artifactId", hasItem("cap-agent-basic"))
                .body("agents.artifactId", not(hasItem("cap-agent-streaming")));
    }

    @Test
    public void testSearchAgentsByInputModeFilter() throws Exception {
        String groupId = TestUtils.generateGroupId();

        createAgentCard(groupId, "mode-agent-basic", AGENT_CARD_CONTENT);
        createAgentCard(groupId, "mode-agent-streaming", STREAMING_AGENT_CARD);

        String requestBody = """
                {
                    "filters": {
                        "inputModes": ["image"]
                    },
                    "limit": 50,
                    "offset": 0
                }
                """;

        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("agents.artifactId", hasItem("mode-agent-streaming"))
                .body("agents.artifactId", not(hasItem("mode-agent-basic")));
    }

    @Test
    public void testSearchAgentsByNonMatchingStructureFilter() throws Exception {
        String groupId = TestUtils.generateGroupId();

        createAgentCard(groupId, "nomatch-agent-basic", AGENT_CARD_CONTENT);
        createAgentCard(groupId, "nomatch-agent-streaming", STREAMING_AGENT_CARD);

        // A skill that no agent card declares must yield no matches (empty-result path).
        String requestBody = """
                {
                    "filters": {
                        "skills": ["no-such-skill-zzz-99999"]
                    },
                    "limit": 50,
                    "offset": 0
                }
                """;

        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", equalTo(0))
                .body("agents.artifactId", not(hasItem("nomatch-agent-basic")))
                .body("agents.artifactId", not(hasItem("nomatch-agent-streaming")));
    }

    @Test
    public void testSearchAgentsByMultipleStructureFilters() throws Exception {
        String groupId = TestUtils.generateGroupId();

        createAgentCard(groupId, "multi-agent-basic", AGENT_CARD_CONTENT);
        createAgentCard(groupId, "multi-agent-streaming", STREAMING_AGENT_CARD);

        // Both structure filters must match the same agent (they are ANDed). Only the streaming
        // agent has the "data-processing" skill AND pushNotifications enabled; the basic agent
        // has neither, so it must be excluded.
        String requestBody = """
                {
                    "filters": {
                        "skills": ["data-processing"],
                        "capabilities": { "pushNotifications": true }
                    },
                    "limit": 50,
                    "offset": 0
                }
                """;

        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("agents.artifactId", hasItem("multi-agent-streaming"))
                .body("agents.artifactId", not(hasItem("multi-agent-basic")));
    }

    @Test
    public void testUpdatingNonLatestDraftVersionKeepsLatestVersionIndexed() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "stale-index-agent";

        // v1 is created as a DRAFT and then left behind by v2, which becomes the latest version.
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AGENT_CARD);
        CreateVersion firstVersion = new CreateVersion();
        firstVersion.setVersion("1.0");
        firstVersion.setIsDraft(true);
        VersionContent firstContent = new VersionContent();
        firstContent.setContent(AGENT_CARD_CONTENT);
        firstContent.setContentType(ContentTypes.APPLICATION_JSON);
        firstVersion.setContent(firstContent);
        createArtifact.setFirstVersion(firstVersion);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        CreateVersion latestVersion = new CreateVersion();
        latestVersion.setVersion("2.0");
        VersionContent latestContent = new VersionContent();
        latestContent.setContent(STREAMING_AGENT_CARD);
        latestContent.setContentType(ContentTypes.APPLICATION_JSON);
        latestVersion.setContent(latestContent);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(latestVersion);

        // Rewriting the older DRAFT version must not move the artifact-level structured index off v2.
        VersionContent updatedDraftContent = new VersionContent();
        updatedDraftContent.setContentType(ContentTypes.APPLICATION_JSON);
        updatedDraftContent.setContent(STALE_DRAFT_AGENT_CARD);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("1.0").content().put(updatedDraftContent);

        // The latest version's skill is still searchable.
        searchAgentsBySkill("data-processing")
                .body("count", greaterThanOrEqualTo(1))
                .body("agents.artifactId", hasItem(artifactId));

        // The skill that only exists in the older DRAFT version never enters the index.
        searchAgentsBySkill("stale-draft-only-skill")
                .body("agents.artifactId", not(hasItem(artifactId)));
    }

    @Test
    public void testSearchAgentsAdvancedWithQueryByArtifactId() throws Exception {
        String groupId = TestUtils.generateGroupId();

        createAgentCard(groupId, "my-search-target", AGENT_CARD_CONTENT);

        String requestBody = """
                {
                    "query": "my-search-target",
                    "limit": 10,
                    "offset": 0
                }
                """;

        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("agents.artifactId", hasItem("my-search-target"));
    }

    @Test
    public void testSearchAgentsAdvancedEmptyBody() {
        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body("{}")
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", notNullValue())
                .body("agents", notNullValue());
    }

    @Test
    public void testGetPublicAgentsWithPagination() throws Exception {
        String groupId = TestUtils.generateGroupId();

        for (int i = 0; i < 3; i++) {
            createAgentCard(groupId, "pub-page-" + i, AGENT_CARD_CONTENT);
            setVisibility(groupId, "pub-page-" + i, "public");
        }

        givenAtRoot()
                .when()
                .queryParam("offset", 0)
                .queryParam("limit", 2)
                .get("/.well-known/agents/public")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(3))
                .body("agents", hasSize(2));
    }

    @Test
    public void testGetPublicAgentsNegativeOffset() {
        givenAtRoot()
                .when()
                .queryParam("offset", -1)
                .get("/.well-known/agents/public")
                .then()
                .statusCode(200)
                .body("count", notNullValue())
                .body("agents", notNullValue());
    }

    @Test
    public void testGetPublicAgentsNegativeLimit() {
        givenAtRoot()
                .when()
                .queryParam("limit", -1)
                .get("/.well-known/agents/public")
                .then()
                .statusCode(200)
                .body("count", notNullValue())
                .body("agents", notNullValue());
    }

    @Test
    public void testGetPublicAgentsNegativeOffsetLimitViaV3Path() {
        givenAtRoot()
                .when()
                .queryParam("offset", -1)
                .queryParam("limit", -1)
                .get("/apis/registry/v3/well-known/agents/public")
                .then()
                .statusCode(200)
                .body("count", notNullValue())
                .body("agents", notNullValue());
    }

    @Test
    public void testSearchAgentsAdvancedWithQueryWildcard() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createAgentCard(groupId, "wildcard-target-agent", AGENT_CARD_CONTENT);

        String requestBody = """
                {
                    "query": "*wildcard-target*",
                    "limit": 10,
                    "offset": 0
                }
                """;

        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("agents.artifactId", hasItem("wildcard-target-agent"));
    }

    @Test
    public void testSearchAgentsAdvancedNegativeOffsetLimit() {
        String requestBody = """
                {
                    "limit": -5,
                    "offset": -10
                }
                """;

        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", notNullValue())
                .body("agents", notNullValue());
    }

    @Test
    public void testSearchAgentsAdvancedMalformedJson() {
        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body("{broken json")
                .post("/.well-known/agents/search")
                .then()
                .statusCode(400);
    }

    @Test
    public void testDefaultVisibilityExcludesFromPublic() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create agent without visibility label — defaults to "entitled"
        createAgentCard(groupId, "default-vis-agent", AGENT_CARD_CONTENT);

        // Should NOT appear on public endpoint (default is "entitled", not "public")
        givenAtRoot()
                .when()
                .get("/.well-known/agents/public")
                .then()
                .statusCode(200)
                .body("agents.artifactId", not(hasItem("default-vis-agent")));
    }

    @Test
    public void testExistingSearchAgentsStillWorks() throws Exception {
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .get("/.well-known/agents")
                .then()
                .statusCode(200)
                .body("count", notNullValue())
                .body("agents", notNullValue());
    }

    private void setVisibility(String groupId, String artifactId, String visibility) {
        EditableArtifactMetaData meta = new EditableArtifactMetaData();
        Labels labels = new Labels();
        labels.setAdditionalData(Map.of("apicurio.agent.visibility", visibility));
        meta.setLabels(labels);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(meta);
    }

    @Test
    public void testGetAgentCardViaOrchestrateAlias() {
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .get("/.well-known/agent-card.json")
                .then()
                .statusCode(200)
                .body("name", equalTo("Apicurio Registry"))
                .body("supportedInterfaces", hasSize(1))
                .body("skills", hasSize(5))
                .body("skills.id", hasItem("schema-validation"))
                .body("skills.id", hasItem("agent-discovery"));
    }

    @Test
    public void testOrchestrateAliasMatchesCanonicalEndpoint() {
        String canonical = givenAtRoot()
                .when()
                .get("/.well-known/agent.json")
                .then()
                .statusCode(200)
                .extract().body().asString();

        String alias = givenAtRoot()
                .when()
                .get("/.well-known/agent-card.json")
                .then()
                .statusCode(200)
                .extract().body().asString();

        org.junit.jupiter.api.Assertions.assertEquals(canonical, alias);
    }

    @Test
    public void testSearchAgentsReturnsInterfaceUrls() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createAgentCard(groupId, "url-agent", AGENT_CARD_CONTENT);

        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .get("/.well-known/agents")
                .then()
                .statusCode(200)
                .body("agents.find { it.artifactId == 'url-agent' }.supportedInterfaces[0].url",
                        equalTo("https://example.com/agent"))
                .body("agents.find { it.artifactId == 'url-agent' }.supportedInterfaces[0].protocolVersion",
                        equalTo("1.0"));
    }

    @Test
    public void testGetSchemaWithRenamedParam() {
        givenAtRoot()
                .when()
                .get("/.well-known/schemas/prompt-template/v1")
                .then()
                .statusCode(200);

        givenAtRoot()
                .when()
                .get("/.well-known/schemas/nonexistent/v1")
                .then()
                .statusCode(404);
    }

    @Test
    public void testSearchAgentsBySkillTreatsLikeWildcardsLiterally() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // '_' and '%' are LIKE wildcards. Structure filters compare the element value with '=', so both
        // characters must match literally: searching for the first skill id must not also return the
        // decoy agent, which is what that id would match if it were ever treated as a LIKE pattern.
        createAgentCard(groupId, "wildcard-agent-literal",
                agentCardWithSkill("WildcardLiteralAgent", "report_2024%draft"));
        createAgentCard(groupId, "wildcard-agent-decoy",
                agentCardWithSkill("WildcardDecoyAgent", "reportx2024-quarterly-draft"));

        searchAgentsBySkill("report_2024%draft")
                .body("agents.artifactId", hasItem("wildcard-agent-literal"))
                .body("agents.artifactId", not(hasItem("wildcard-agent-decoy")));
    }

    private static String agentCardWithSkill(String agentName, String skillId) {
        return """
                {
                    "name": "%s",
                    "description": "An agent whose skill id contains SQL wildcard characters",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/wildcard-agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {
                        "streaming": false,
                        "pushNotifications": false
                    },
                    "skills": [
                        {
                            "id": "%s",
                            "name": "Wildcard Skill",
                            "description": "A skill whose id contains wildcard characters",
                            "tags": ["wildcard"]
                        }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """.formatted(agentName, skillId);
    }

    private void createAgentCard(String groupId, String artifactId, String content) throws Exception {
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
