package io.apicurio.registry.search;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.storage.impl.search.ElasticsearchIndexUpdater;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/**
 * Integration tests for well-known A2A agent searches that use skill/capability/mode filters.
 * These filters are translated into structure filters, which can only be served by the
 * Elasticsearch search index; this test verifies the artifact search path is routed through
 * the index (see {@code ElasticsearchSearchDecorator#searchArtifacts}).
 */
@QuarkusTest
@TestProfile(ElasticsearchA2ATestProfile.class)
public class WellKnownAgentSearchViaIndexTest extends AbstractResourceTestBase {

    @Inject
    ElasticsearchIndexUpdater indexUpdater;

    private String serverRootUrl;

    @BeforeEach
    public void setUpWellKnown() {
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        serverRootUrl = "http://localhost:" + port;
    }

    private RequestSpecification givenAtRoot() {
        return RestAssured.given().baseUri(serverRootUrl);
    }

    private static String agentCard(String name, String skillId, boolean streaming) {
        return """
                {
                    "name": "%s",
                    "description": "Agent for skill %s",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/%s", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {
                        "streaming": %s,
                        "pushNotifications": false
                    },
                    "skills": [
                        {
                            "id": "%s",
                            "name": "Skill %s",
                            "description": "A skill",
                            "tags": ["testing"]
                        }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """.formatted(name, skillId, name, streaming, skillId, skillId);
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

    @Test
    public void testSearchAgentsBySkill() throws Exception {
        String groupId = TestUtils.generateGroupId();
        // Skill IDs must be unique across the shared index, since skill search is not
        // scoped to a group
        String skillA = "skill-a-" + TestUtils.generateArtifactId();
        String skillB = "skill-b-" + TestUtils.generateArtifactId();

        createAgentCard(groupId, "skill-search-agent-1", agentCard("SkillAgentOne", skillA, true));
        createAgentCard(groupId, "skill-search-agent-2", agentCard("SkillAgentTwo", skillB, true));

        indexUpdater.awaitIdle(10, TimeUnit.SECONDS);

        // Searching by skill A must return exactly the first agent
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .queryParam("skill", skillA)
                .get("/.well-known/agents")
                .then()
                .statusCode(200)
                .body("count", equalTo(1))
                .body("agents[0].artifactId", equalTo("skill-search-agent-1"))
                .body("agents[0].groupId", equalTo(groupId))
                .body("agents[0].skills", hasItem(skillA));

        // Searching for an unknown skill must return no results
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .queryParam("skill", "no-such-skill-" + TestUtils.generateArtifactId())
                .get("/.well-known/agents")
                .then()
                .statusCode(200)
                .body("count", equalTo(0));
    }

    @Test
    public void testSearchAgentsBySkillAndName() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String sharedSkill = "shared-skill-" + TestUtils.generateArtifactId();

        createAgentCard(groupId, "combined-agent-alpha", agentCard("CombinedAlpha", sharedSkill, true));
        createAgentCard(groupId, "combined-agent-beta", agentCard("CombinedBeta", sharedSkill, true));

        indexUpdater.awaitIdle(10, TimeUnit.SECONDS);

        // The skill matches both agents; the name filter narrows to one. As in the other
        // well-known search tests, the name filter matches against the artifactId.
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .queryParam("skill", sharedSkill)
                .queryParam("name", "combined-agent-alpha")
                .get("/.well-known/agents")
                .then()
                .statusCode(200)
                .body("count", equalTo(1))
                .body("agents[0].artifactId", equalTo("combined-agent-alpha"));

        // The skill alone matches both
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .queryParam("skill", sharedSkill)
                .get("/.well-known/agents")
                .then()
                .statusCode(200)
                .body("count", equalTo(2));
    }

    @Test
    public void testSearchAgentsByCapability() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String skillStreaming = "cap-skill-" + TestUtils.generateArtifactId();
        String skillPlain = "cap-skill-" + TestUtils.generateArtifactId();

        createAgentCard(groupId, "capability-agent-streaming",
                agentCard("CapabilityStreamingAgent", skillStreaming, true));
        createAgentCard(groupId, "capability-agent-plain",
                agentCard("CapabilityPlainAgent", skillPlain, false));

        indexUpdater.awaitIdle(10, TimeUnit.SECONDS);

        // capability=streaming:true must include the streaming agent and exclude the other
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .queryParam("capability", "streaming:true")
                .get("/.well-known/agents")
                .then()
                .statusCode(200)
                .body("agents.artifactId", hasItem("capability-agent-streaming"))
                .body("agents.artifactId", not(hasItem("capability-agent-plain")));

        // capability=streaming:false must include the non-streaming agent and exclude the other
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .queryParam("capability", "streaming:false")
                .get("/.well-known/agents")
                .then()
                .statusCode(200)
                .body("agents.artifactId", hasItem("capability-agent-plain"))
                .body("agents.artifactId", not(hasItem("capability-agent-streaming")));
    }

    @Test
    public void testAdvancedSearchAgentsBySkill() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String skillId = "advanced-skill-" + TestUtils.generateArtifactId();

        createAgentCard(groupId, "advanced-search-agent", agentCard("AdvancedSearchAgent", skillId, true));

        indexUpdater.awaitIdle(10, TimeUnit.SECONDS);

        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .body("""
                        {
                            "filters": {
                                "skills": ["%s"]
                            }
                        }
                        """.formatted(skillId))
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("count", equalTo(1))
                .body("agents[0].artifactId", equalTo("advanced-search-agent"));
    }

    @Test
    public void testSearchAgentsByInputMode() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String skillId = "mode-skill-" + TestUtils.generateArtifactId();

        createAgentCard(groupId, "input-mode-agent", agentCard("InputModeAgent", skillId, true));

        indexUpdater.awaitIdle(10, TimeUnit.SECONDS);

        // All test agent cards declare the "text" input mode; combine with the unique skill
        // to isolate this test's agent
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .queryParam("skill", skillId)
                .queryParam("inputMode", "text")
                .get("/.well-known/agents")
                .then()
                .statusCode(200)
                .body("count", equalTo(1))
                .body("agents[0].artifactId", equalTo("input-mode-agent"));
    }
}
