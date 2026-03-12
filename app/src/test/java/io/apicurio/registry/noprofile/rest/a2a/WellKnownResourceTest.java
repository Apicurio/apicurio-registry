package io.apicurio.registry.noprofile.rest.a2a;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
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
                "url": "https://example.com/agent",
                "capabilities": {
                    "streaming": true,
                    "pushNotifications": false
                },
                "skills": [
                    {
                        "id": "test-skill",
                        "name": "Test Skill",
                        "description": "A test skill"
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
                "url": "https://example.com/streaming-agent",
                "capabilities": {
                    "streaming": true,
                    "pushNotifications": true
                },
                "skills": [
                    {
                        "id": "data-processing",
                        "name": "Data Processing"
                    },
                    {
                        "id": "real-time-analysis",
                        "name": "Real-time Analysis"
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
                .body("provider.organization", equalTo("Apicurio"))
                .body("provider.url", equalTo("https://www.apicur.io"))
                .body("capabilities.streaming", equalTo(false))
                .body("capabilities.pushNotifications", equalTo(false))
                .body("skills", hasSize(5))
                .body("skills.id", hasItem("schema-validation"))
                .body("skills.id", hasItem("schema-search"))
                .body("skills.id", hasItem("artifact-management"))
                .body("skills.id", hasItem("compatibility-check"))
                .body("skills.id", hasItem("agent-discovery"))
                .body("defaultInputModes", hasItem("text"))
                .body("defaultOutputModes", hasItem("text"))
                .body("authentication.schemes", notNullValue())
                .body("supportsExtendedAgentCard", equalTo(false));
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
                .body("url", equalTo("https://example.com/agent"))
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
