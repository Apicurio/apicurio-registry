package io.apicurio.registry.federation;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.noprofile.rest.a2a.ExperimentalFeaturesEnabledProfile;
import io.apicurio.registry.rest.client.models.Labels;
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

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * The public-only mode of the agent search that a coordinator asks of its peers. Authentication is
 * off here, which is the case the mode exists for: without it every card is returned.
 */
@QuarkusTest
@TestProfile(ExperimentalFeaturesEnabledProfile.class)
public class PublicOnlyAgentSearchTest extends AbstractResourceTestBase {

    private static final String CARD = """
            {
                "name": "TestAgent",
                "description": "A test AI agent",
                "version": "1.0.0",
                "supportedInterfaces": [
                    { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                ],
                "capabilities": { "streaming": false, "pushNotifications": false },
                "skills": [ { "id": "test-skill", "name": "Test Skill", "description": "A test skill", "tags": ["t"] } ],
                "defaultInputModes": ["text"],
                "defaultOutputModes": ["text"]
            }
            """;

    private String serverRootUrl;

    @BeforeEach
    void setUpRoot() {
        serverRootUrl = "http://localhost:" + ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
    }

    private RequestSpecification atRoot() {
        return RestAssured.given().baseUri(serverRootUrl);
    }

    private void card(String group, String artifactId, String skill, String visibility) throws Exception {
        createArtifact(group, artifactId, ArtifactType.AGENT_CARD, CARD.replace("test-skill", skill),
                ContentTypes.APPLICATION_JSON, request -> {
                    if (visibility != null) {
                        Labels labels = new Labels();
                        labels.setAdditionalData(Map.of("apicurio.agent.visibility", visibility));
                        request.setLabels(labels);
                    }
                });
    }

    @Test
    void returnsOnlyPublicCardsAndSaysSo() throws Exception {
        String group = TestUtils.generateGroupId();
        String skill = "mode-" + UUID.randomUUID();
        card(group, "public-card", skill, "public");
        card(group, "private-card", skill, "private");
        card(group, "entitled-card", skill, "entitled");
        card(group, "unlabelled-card", skill, null);

        // Without the mode nothing changes: with authentication off every card is returned, and
        // the answer does not claim a filter that was not applied.
        atRoot().queryParam("skill", skill).get("/.well-known/agents").then().statusCode(200)
                .body("count", equalTo(4)).body("publicOnly", nullValue());
        atRoot().queryParam("skill", skill).queryParam("publicOnly", false).get("/.well-known/agents").then()
                .statusCode(200).body("count", equalTo(4)).body("publicOnly", nullValue());

        atRoot().queryParam("skill", skill).queryParam("publicOnly", true).get("/.well-known/agents").then()
                .statusCode(200).body("count", equalTo(1)).body("publicOnly", equalTo(true))
                .body("agents.artifactId", contains("public-card"));
    }

    @Test
    void filtersBeforeItPaginates() throws Exception {
        String group = TestUtils.generateGroupId();
        String skill = "page-" + UUID.randomUUID();
        // Results are newest first, so the public card is last: a filter applied to a page would
        // find only cards that are not public on the first one.
        card(group, "oldest-public", skill, "public");
        card(group, "newer-private", skill, "private");
        card(group, "newest-unlabelled", skill, null);

        atRoot().queryParam("skill", skill).queryParam("publicOnly", true).queryParam("limit", 1)
                .get("/.well-known/agents").then().statusCode(200).body("count", equalTo(1))
                .body("agents.artifactId", contains("oldest-public"));
        atRoot().queryParam("skill", skill).queryParam("publicOnly", true).queryParam("offset", 1)
                .get("/.well-known/agents").then().statusCode(200).body("count", equalTo(1))
                .body("agents", empty());
    }

    @Test
    void theSdkEndpointHasTheSameMode() throws Exception {
        String group = TestUtils.generateGroupId();
        String skill = "sdk-" + UUID.randomUUID();
        card(group, "public-card", skill, "public");
        card(group, "unlabelled-card", skill, null);

        RestAssured.given().queryParam("skill", skill).queryParam("publicOnly", true)
                .get("/registry/v3/well-known/agents").then().statusCode(200).body("count", equalTo(1))
                .body("publicOnly", equalTo(true)).body("agents.artifactId", contains("public-card"));
    }
}
