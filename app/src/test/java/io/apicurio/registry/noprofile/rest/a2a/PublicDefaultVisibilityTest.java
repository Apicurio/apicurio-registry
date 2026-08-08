package io.apicurio.registry.noprofile.rest.a2a;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/**
 * Tests the public agent discovery endpoint when {@code apicurio.a2a.default-visibility} is set to
 * {@code public}. Agent Cards that rely on that default carry no visibility label, so they cannot be
 * matched by a label search filter and must be resolved through the same effective-visibility logic
 * the entitled endpoint uses.
 */
@QuarkusTest
@TestProfile(PublicDefaultVisibilityTest.PublicDefaultVisibilityProfile.class)
public class PublicDefaultVisibilityTest extends AbstractResourceTestBase {

    public static class PublicDefaultVisibilityProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "apicurio.features.experimental.enabled", "true",
                    "apicurio.a2a.enabled", "true",
                    "apicurio.a2a.default-visibility", "public");
        }
    }

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

    /**
     * An Agent Card with no visibility label is public when the configured default is public, so it
     * must be returned by the public discovery endpoint.
     */
    @Test
    public void testUnlabeledAgentIsPublicWhenDefaultIsPublic() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createAgentCard(groupId, "unlabeled-public-agent", AGENT_CARD_CONTENT);

        givenAtRoot()
                .when()
                .get("/.well-known/agents/public")
                .then()
                .statusCode(200)
                .body("agents.artifactId", hasItem("unlabeled-public-agent"));
    }

    /**
     * An explicit label still wins over the configured default, so a private agent must not leak
     * onto the unauthenticated public endpoint.
     */
    @Test
    public void testExplicitPrivateLabelIsExcludedWhenDefaultIsPublic() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createAgentCard(groupId, "explicit-private-agent", AGENT_CARD_CONTENT);
        setVisibility(groupId, "explicit-private-agent", "private");

        givenAtRoot()
                .when()
                .get("/.well-known/agents/public")
                .then()
                .statusCode(200)
                .body("agents.artifactId", not(hasItem("explicit-private-agent")));
    }

    /**
     * An unrecognized visibility label is treated as private by resolveVisibility, so it must not be
     * exposed on the unauthenticated public endpoint even when the configured default is public.
     */
    @Test
    public void testUnrecognizedLabelIsExcludedWhenDefaultIsPublic() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createAgentCard(groupId, "unrecognized-vis-agent", AGENT_CARD_CONTENT);
        setVisibility(groupId, "unrecognized-vis-agent", "internal-only");

        givenAtRoot()
                .when()
                .get("/.well-known/agents/public")
                .then()
                .statusCode(200)
                .body("agents.artifactId", not(hasItem("unrecognized-vis-agent")));
    }

    /**
     * The visibility label key is matched case-insensitively. Labels are read from the serialized
     * {@code labels} column, which preserves the supplied case, so an exact-match lookup would miss
     * a mixed-case key, fall back to the configured default, and expose a card marked private on
     * the unauthenticated public endpoint.
     */
    @Test
    public void testMixedCaseVisibilityLabelKeyIsHonoredWhenDefaultIsPublic() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createAgentCard(groupId, "mixed-case-key-agent", AGENT_CARD_CONTENT);
        setLabel(groupId, "mixed-case-key-agent", "Apicurio.Agent.Visibility", "private");

        givenAtRoot()
                .when()
                .get("/.well-known/agents/public")
                .then()
                .statusCode(200)
                .body("agents.artifactId", not(hasItem("mixed-case-key-agent")));
    }

    /**
     * An explicitly public agent is still returned, so the resolved-visibility path does not regress
     * the labelled case.
     */
    @Test
    public void testExplicitPublicLabelIsIncludedWhenDefaultIsPublic() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createAgentCard(groupId, "explicit-public-agent", AGENT_CARD_CONTENT);
        setVisibility(groupId, "explicit-public-agent", "public");

        givenAtRoot()
                .when()
                .get("/.well-known/agents/public")
                .then()
                .statusCode(200)
                .body("agents.artifactId", hasItem("explicit-public-agent"));
    }

    /**
     * Pagination is applied after visibility resolution on this path, so a limit of 1 must return a
     * single agent while the count still reflects every public agent.
     */
    @Test
    public void testPaginationAppliesOnResolvedVisibilityPath() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createAgentCard(groupId, "paged-agent-1", AGENT_CARD_CONTENT);
        createAgentCard(groupId, "paged-agent-2", AGENT_CARD_CONTENT);

        // count reports every public agent, the page itself is limited
        int total = givenAtRoot()
                .when()
                .get("/.well-known/agents/public")
                .then()
                .statusCode(200)
                .extract().path("count");

        givenAtRoot()
                .when()
                .queryParam("offset", 0)
                .queryParam("limit", 1)
                .get("/.well-known/agents/public")
                .then()
                .statusCode(200)
                .body("agents.size()", equalTo(1))
                .body("count", equalTo(total));
    }

    private void setVisibility(String groupId, String artifactId, String visibility) {
        setLabel(groupId, artifactId, "apicurio.agent.visibility", visibility);
    }

    private void setLabel(String groupId, String artifactId, String key, String value) {
        EditableArtifactMetaData meta = new EditableArtifactMetaData();
        Labels labels = new Labels();
        labels.setAdditionalData(Map.of(key, value));
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
