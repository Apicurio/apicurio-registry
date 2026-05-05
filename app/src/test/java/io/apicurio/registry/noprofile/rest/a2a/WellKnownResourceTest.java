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
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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
                .body("defaultInputModes", hasItem("text"))
                .body("defaultOutputModes", hasItem("text"))
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
                    "query": "TestAgent",
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
}
