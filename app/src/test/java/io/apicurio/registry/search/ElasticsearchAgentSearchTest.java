package io.apicurio.registry.search;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.storage.impl.search.ElasticsearchIndexUpdater;
import io.apicurio.registry.storage.impl.search.ElasticsearchStartupIndexer;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

@QuarkusTest
@TestProfile(ElasticsearchAgentSearchTestProfile.class)
public class ElasticsearchAgentSearchTest extends AbstractResourceTestBase {

    @Inject
    ElasticsearchStartupIndexer startupIndexer;

    @Inject
    ElasticsearchIndexUpdater indexUpdater;

    private static final String SENTIMENT_AGENT = """
            {
                "name": "SentimentAnalyzer",
                "description": "Analyzes customer feedback to determine emotional tone",
                "version": "1.0.0",
                "supportedInterfaces": [
                    { "url": "https://example.com/sentiment", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                ],
                "capabilities": { "streaming": false, "pushNotifications": false },
                "skills": [
                    { "id": "sentiment-analysis", "name": "Sentiment Analysis", "description": "Detect emotions in text", "tags": ["nlp", "emotions"] }
                ],
                "defaultInputModes": ["text"],
                "defaultOutputModes": ["text"]
            }
            """;

    private static final String TRANSLATOR_AGENT = """
            {
                "name": "TranslatorBot",
                "description": "Translates text between languages with high accuracy",
                "version": "1.0.0",
                "supportedInterfaces": [
                    { "url": "https://example.com/translator", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                ],
                "capabilities": { "streaming": true, "pushNotifications": false },
                "skills": [
                    { "id": "translate", "name": "Language Translation", "description": "Translate text between languages", "tags": ["translation", "i18n"] }
                ],
                "defaultInputModes": ["text"],
                "defaultOutputModes": ["text"]
            }
            """;

    @Test
    public void testFullTextSearchByContent() throws Exception {
        waitForStartupIndexer();
        String groupId = TestUtils.generateGroupId();

        createAgentCard(groupId, "sentiment-agent", SENTIMENT_AGENT);
        createAgentCard(groupId, "translator-agent", TRANSLATOR_AGENT);
        indexUpdater.awaitIdle(10, TimeUnit.SECONDS);

        RestAssured.given()
                .baseUri(serverRootUrl())
                .contentType(ContentType.JSON)
                .body("""
                        { "query": "sentiment emotions", "limit": 10, "offset": 0 }
                        """)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("agents.artifactId", hasItem("sentiment-agent"))
                .body("agents.artifactId", not(hasItem("translator-agent")));
    }

    @Test
    public void testMultiVersionArtifactCollapsesToOneHit() throws Exception {
        waitForStartupIndexer();
        String groupId = TestUtils.generateGroupId();

        createAgentCard(groupId, "multi-ver-agent", SENTIMENT_AGENT);

        String v2Content = SENTIMENT_AGENT.replace("\"1.0.0\"", "\"2.0.0\"");
        CreateVersion v2 = new CreateVersion();
        VersionContent vc = new VersionContent();
        vc.setContent(v2Content);
        vc.setContentType(ContentTypes.APPLICATION_JSON);
        v2.setContent(vc);
        clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId("multi-ver-agent").versions().post(v2);

        String v3Content = SENTIMENT_AGENT.replace("\"1.0.0\"", "\"3.0.0\"");
        CreateVersion v3 = new CreateVersion();
        VersionContent vc3 = new VersionContent();
        vc3.setContent(v3Content);
        vc3.setContentType(ContentTypes.APPLICATION_JSON);
        v3.setContent(vc3);
        clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId("multi-ver-agent").versions().post(v3);

        indexUpdater.awaitIdle(10, TimeUnit.SECONDS);

        RestAssured.given()
                .baseUri(serverRootUrl())
                .contentType(ContentType.JSON)
                .body("""
                        { "query": "sentiment", "limit": 10, "offset": 0 }
                        """)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("agents.findAll { it.artifactId == 'multi-ver-agent' }.size()", equalTo(1));
    }

    @Test
    public void testCountReflectsArtifactCardinalityNotVersionCount() throws Exception {
        waitForStartupIndexer();
        String groupId = TestUtils.generateGroupId();

        createAgentCard(groupId, "count-agent-1", SENTIMENT_AGENT);
        createAgentCard(groupId, "count-agent-2", TRANSLATOR_AGENT);

        String v2Content = SENTIMENT_AGENT.replace("\"1.0.0\"", "\"2.0.0\"");
        CreateVersion v2 = new CreateVersion();
        VersionContent vc = new VersionContent();
        vc.setContent(v2Content);
        vc.setContentType(ContentTypes.APPLICATION_JSON);
        v2.setContent(vc);
        clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId("count-agent-1").versions().post(v2);

        indexUpdater.awaitIdle(10, TimeUnit.SECONDS);

        RestAssured.given()
                .baseUri(serverRootUrl())
                .contentType(ContentType.JSON)
                .body(String.format("""
                        { "query": "text", "limit": 10, "offset": 0,
                          "filters": { "labels": { "groupId": "%s" } } }
                        """, groupId))
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(2));
    }

    private void createAgentCard(String groupId, String artifactId, String content) throws Exception {
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AGENT_CARD);
        createArtifact.setName(artifactId);

        CreateVersion createVersion = new CreateVersion();
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(versionContent);
        createArtifact.setFirstVersion(createVersion);

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
    }

    private String serverRootUrl() {
        int port = org.eclipse.microprofile.config.ConfigProvider.getConfig()
                .getValue("quarkus.http.test-port", Integer.class);
        return "http://localhost:" + port;
    }

    private void waitForStartupIndexer() throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!startupIndexer.isReady()) {
            if (System.nanoTime() >= deadlineNanos) {
                throw new IllegalStateException(
                        "Startup indexer did not become ready within 30 seconds");
            }
            Thread.sleep(100);
        }
    }
}
