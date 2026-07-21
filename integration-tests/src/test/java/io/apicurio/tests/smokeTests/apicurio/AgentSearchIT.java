package io.apicurio.tests.smokeTests.apicurio;

import static io.apicurio.deployment.Constants.SMOKE;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for agent full-text search. Exercises the PostgreSQL tsvector/tsquery path
 * when running against a real PostgreSQL instance.
 */
@Tag(SMOKE)
@QuarkusIntegrationTest
class AgentSearchIT extends ApicurioRegistryBaseIT {

    private static final String SENTIMENT_AGENT = """
            {
                "name": "SentimentAnalyzer",
                "description": "Analyzes customer feedback to determine emotional tone and satisfaction levels",
                "version": "1.0.0",
                "supportedInterfaces": [
                    { "url": "https://example.com/sentiment", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                ],
                "capabilities": { "streaming": false, "pushNotifications": false },
                "skills": [
                    { "id": "sentiment-analysis", "name": "Sentiment Analysis", "description": "Detect emotions in text", "tags": ["nlp", "emotions"] },
                    { "id": "satisfaction-scoring", "name": "Satisfaction Scoring", "description": "Score customer satisfaction", "tags": ["metrics"] }
                ],
                "defaultInputModes": ["text"],
                "defaultOutputModes": ["text"]
            }
            """;

    private static final String TRANSLATOR_AGENT = """
            {
                "name": "TranslatorBot",
                "description": "Translates text between languages with high accuracy",
                "version": "2.0.0",
                "supportedInterfaces": [
                    { "url": "https://example.com/translator", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                ],
                "capabilities": { "streaming": true, "pushNotifications": false },
                "skills": [
                    { "id": "translate", "name": "Language Translation", "description": "Translate text between languages", "tags": ["translation", "i18n"] },
                    { "id": "detect-language", "name": "Language Detection", "description": "Detect input language", "tags": ["detection"] }
                ],
                "defaultInputModes": ["text"],
                "defaultOutputModes": ["text"]
            }
            """;

    @Test
    @Tag("acceptance")
    void testSearchByAgentName() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createAgentCard(groupId, "name-search-agent", SENTIMENT_AGENT);

        given()
                .baseUri(getRegistryBaseUrl())
                .contentType(ContentType.JSON)
                .body("""
                        { "query": "SentimentAnalyzer", "limit": 10, "offset": 0 }
                        """)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("agents.artifactId", hasItem("name-search-agent"));
    }

    @Test
    @Tag("acceptance")
    void testSearchByDescription() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createAgentCard(groupId, "desc-search-agent", SENTIMENT_AGENT);

        given()
                .baseUri(getRegistryBaseUrl())
                .contentType(ContentType.JSON)
                .body("""
                        { "query": "customer feedback", "limit": 10, "offset": 0 }
                        """)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("agents.artifactId", hasItem("desc-search-agent"));
    }

    @Test
    @Tag("acceptance")
    void testSearchByDescriptionWords() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createAgentCard(groupId, "desc-words-agent", TRANSLATOR_AGENT);

        given()
                .baseUri(getRegistryBaseUrl())
                .contentType(ContentType.JSON)
                .body("""
                        { "query": "translates languages", "limit": 10, "offset": 0 }
                        """)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("agents.artifactId", hasItem("desc-words-agent"));
    }

    @Test
    void testSearchNoMatch() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createAgentCard(groupId, "no-match-it-agent", SENTIMENT_AGENT);

        given()
                .baseUri(getRegistryBaseUrl())
                .contentType(ContentType.JSON)
                .body("""
                        { "query": "xyznonexistentterm", "limit": 10, "offset": 0 }
                        """)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("agents.artifactId", not(hasItem("no-match-it-agent")));
    }

    @Test
    void testSearchDistinguishesAgents() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createAgentCard(groupId, "sentiment-it-agent", SENTIMENT_AGENT);
        createAgentCard(groupId, "translator-it-agent", TRANSLATOR_AGENT);

        given()
                .baseUri(getRegistryBaseUrl())
                .contentType(ContentType.JSON)
                .body("""
                        { "query": "sentiment", "limit": 10, "offset": 0 }
                        """)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("agents.artifactId", hasItem("sentiment-it-agent"))
                .body("agents.artifactId", not(hasItem("translator-it-agent")));
    }

    @Test
    void testSearchViaGetEndpoint() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createAgentCard(groupId, "get-search-agent", TRANSLATOR_AGENT);

        given()
                .baseUri(getRegistryBaseUrl())
                .queryParam("name", "TranslatorBot")
                .get("/.well-known/agents")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("agents.artifactId", hasItem("get-search-agent"));
    }

    @Test
    void testSearchPagination() throws Exception {
        String groupId = TestUtils.generateGroupId();
        for (int i = 0; i < 3; i++) {
            createAgentCard(groupId, "page-agent-" + i, SENTIMENT_AGENT);
        }

        given()
                .baseUri(getRegistryBaseUrl())
                .contentType(ContentType.JSON)
                .body("""
                        { "query": "SentimentAnalyzer", "limit": 2, "offset": 0 }
                        """)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(3));
    }

    private void createAgentCard(String groupId, String artifactId, String content) throws Exception {
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AGENT_CARD);

        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(content);
            if (root.has("name")) {
                createArtifact.setName(root.get("name").asText());
            }
            if (root.has("description")) {
                createArtifact.setDescription(root.get("description").asText());
            }
        } catch (Exception e) {
            // ignore
        }

        CreateVersion createVersion = new CreateVersion();
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(versionContent);
        createArtifact.setFirstVersion(createVersion);

        registryClient.groups().byGroupId(groupId).artifacts().post(createArtifact);
    }
}
