package io.apicurio.tests.smokeTests.apicurio;

import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.apicurio.tests.utils.Constants.ACCEPTANCE;
import static io.apicurio.tests.utils.Constants.SMOKE;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for A2A Agent Card artifact type.
 */
@Tag(SMOKE)
@QuarkusIntegrationTest
class AgentCardIT extends ApicurioRegistryBaseIT {

    private static final String VALID_AGENT_CARD = """
            {
              "name": "TestAgent",
              "description": "A test agent for integration testing",
              "version": "1.0.0",
              "url": "https://example.com/agent",
              "provider": {
                "organization": "Test Org",
                "url": "https://example.com"
              },
              "capabilities": {
                "streaming": true,
                "pushNotifications": false,
                "stateTransitionHistory": true
              },
              "skills": [
                {
                  "id": "search",
                  "name": "Web Search",
                  "description": "Search the web for information",
                  "tags": ["search", "web"]
                },
                {
                  "id": "summarize",
                  "name": "Text Summarization",
                  "description": "Summarize text content",
                  "tags": ["nlp", "summarization"]
                }
              ],
              "defaultInputModes": ["text"],
              "defaultOutputModes": ["text"]
            }
            """;

    private static final String UPDATED_AGENT_CARD = """
            {
              "name": "TestAgent",
              "description": "Updated test agent",
              "version": "2.0.0",
              "url": "https://example.com/agent/v2",
              "provider": {
                "organization": "Test Org",
                "url": "https://example.com"
              },
              "capabilities": {
                "streaming": true,
                "pushNotifications": true,
                "stateTransitionHistory": true
              },
              "skills": [
                {
                  "id": "search",
                  "name": "Enhanced Web Search",
                  "description": "Search the web with improved accuracy",
                  "tags": ["search", "web", "enhanced"]
                },
                {
                  "id": "summarize",
                  "name": "Text Summarization",
                  "description": "Summarize text content",
                  "tags": ["nlp", "summarization"]
                },
                {
                  "id": "translate",
                  "name": "Translation",
                  "description": "Translate text between languages",
                  "tags": ["nlp", "translation"]
                }
              ],
              "defaultInputModes": ["text", "audio"],
              "defaultOutputModes": ["text", "audio"]
            }
            """;

    private static final String INVALID_AGENT_CARD = """
            {
              "description": "Missing name field"
            }
            """;

    @Test
    @Tag(ACCEPTANCE)
    void testAgentCardArtifactType() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create agent card artifact
        createArtifact(groupId, artifactId, ArtifactType.AGENT_CARD, VALID_AGENT_CARD,
                ContentTypes.APPLICATION_JSON, IfArtifactExists.FAIL, null);

        // Verify artifact was created
        retryOp((rc) -> {
            var meta = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
            assertEquals(ArtifactType.AGENT_CARD, meta.getArtifactType());
            assertEquals("TestAgent", meta.getName());
        });

        // Create a new version
        VersionMetaData vmd = createArtifactVersion(groupId, artifactId, UPDATED_AGENT_CARD,
                ContentTypes.APPLICATION_JSON, null);
        assertNotNull(vmd);
        assertEquals("2", vmd.getVersion());
    }

    @Test
    void testAgentCardValidation() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Enable validation rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("FULL");
        registryClient.admin().rules().post(createRule);

        retryOp((rc) -> rc.admin().rules().byRuleType(createRule.getRuleType().name()).get());

        // Try to create invalid agent card - should fail
        retryAssertClientError("RuleViolationException", 409, (rc) -> {
            CreateVersion tcv = TestUtils.clientCreateVersion(INVALID_AGENT_CARD, ContentTypes.APPLICATION_JSON);
            rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(tcv, config -> {
                config.headers.add("X-Registry-ArtifactType", ArtifactType.AGENT_CARD);
                config.queryParameters.dryRun = true;
            });
        }, errorCodeExtractor);

        // Valid agent card should work
        createArtifact(groupId, artifactId, ArtifactType.AGENT_CARD, VALID_AGENT_CARD,
                ContentTypes.APPLICATION_JSON, IfArtifactExists.FAIL, null);
    }

    @Test
    void testAgentDiscoveryEndpoint() throws Exception {
        String groupId = "agents";
        String artifactId = TestUtils.generateArtifactId();

        // Create an agent in the agents group
        createArtifact(groupId, artifactId, ArtifactType.AGENT_CARD, VALID_AGENT_CARD,
                ContentTypes.APPLICATION_JSON, IfArtifactExists.FAIL, null);

        // Call discovery endpoint
        given().when().get("/.well-known/agents.json").then().statusCode(200)
                .contentType(ContentType.JSON).body("version", notNullValue())
                .body("generatedAt", notNullValue()).body("count", greaterThanOrEqualTo(1))
                .body("agents", notNullValue());
    }

    @Test
    void testAgentsAPI() throws Exception {
        String agentId = "test-agent-" + System.currentTimeMillis();

        String createAgentRequest = """
                {
                  "agentId": "%s",
                  "version": "1.0.0",
                  "content": %s
                }
                """.formatted(agentId, VALID_AGENT_CARD);

        // Create agent via dedicated API
        given().contentType(ContentType.JSON).body(createAgentRequest).when()
                .post("/apis/a2a/v1/agents").then().statusCode(201).contentType(ContentType.JSON)
                .body("name", equalTo("TestAgent")).body("description", notNullValue());

        // Get agent
        given().when().get("/apis/a2a/v1/agents/" + agentId).then().statusCode(200)
                .contentType(ContentType.JSON).body("name", equalTo("TestAgent"))
                .body("skills[0].id", equalTo("search"));

        // List agents
        given().when().get("/apis/a2a/v1/agents").then().statusCode(200).contentType(ContentType.JSON)
                .body("count", greaterThanOrEqualTo(1)).body("agents", notNullValue());

        // Delete agent
        given().when().delete("/apis/a2a/v1/agents/" + agentId).then().statusCode(204);
    }

    @Test
    void testAgentSearch() throws Exception {
        String agentId1 = "search-agent-1-" + System.currentTimeMillis();
        String agentId2 = "search-agent-2-" + System.currentTimeMillis();

        String agentWithStreaming = """
                {
                  "name": "StreamingAgent",
                  "capabilities": {
                    "streaming": true
                  },
                  "skills": [
                    {
                      "id": "analyze",
                      "name": "Data Analysis",
                      "tags": ["analytics"]
                    }
                  ]
                }
                """;

        String agentWithoutStreaming = """
                {
                  "name": "NonStreamingAgent",
                  "capabilities": {
                    "streaming": false
                  },
                  "skills": [
                    {
                      "id": "process",
                      "name": "Data Processing",
                      "tags": ["processing"]
                    }
                  ]
                }
                """;

        // Create two agents
        given().contentType(ContentType.JSON)
                .body("""
                        {"agentId": "%s", "version": "1.0.0", "content": %s}
                        """.formatted(agentId1, agentWithStreaming))
                .when().post("/apis/a2a/v1/agents").then().statusCode(201);

        given().contentType(ContentType.JSON)
                .body("""
                        {"agentId": "%s", "version": "1.0.0", "content": %s}
                        """.formatted(agentId2, agentWithoutStreaming))
                .when().post("/apis/a2a/v1/agents").then().statusCode(201);

        // Search by capability
        given().queryParam("capability", "streaming").when().get("/apis/a2a/v1/agents/search").then()
                .statusCode(200).contentType(ContentType.JSON).body("count", greaterThanOrEqualTo(1));

        // Search by skill
        given().queryParam("skill", "analyze").when().get("/apis/a2a/v1/agents/search").then()
                .statusCode(200).contentType(ContentType.JSON).body("count", greaterThanOrEqualTo(1));

        // Search by tag
        given().queryParam("tag", "analytics").when().get("/apis/a2a/v1/agents/search").then()
                .statusCode(200).contentType(ContentType.JSON).body("count", greaterThanOrEqualTo(1));

        // Cleanup
        given().when().delete("/apis/a2a/v1/agents/" + agentId1).then().statusCode(204);
        given().when().delete("/apis/a2a/v1/agents/" + agentId2).then().statusCode(204);
    }

    @AfterEach
    void deleteRules() throws Exception {
        registryClient.admin().rules().delete();
        retryOp((rc) -> {
            List<RuleType> rules = rc.admin().rules().get();
            assertEquals(0, rules.size(), "All global rules not deleted");
        });
    }
}
