package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.Vertx;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests for A2A agent visibility and entitlement filtering with authentication enabled.
 */
@QuarkusTest
@TestProfile(A2AAuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class A2AAuthTest extends AbstractResourceTestBase {

    private static final String ADMIN_USERNAME = "alice";
    private static final String ADMIN_PASSWORD = "alice";
    private static final String DEVELOPER_USERNAME = "bob1";
    private static final String DEVELOPER_PASSWORD = "bob1";
    private static final String READONLY_USERNAME = "duncan";
    private static final String READONLY_PASSWORD = "duncan";

    private static final String AGENT_CARD_CONTENT = """
            {
                "name": "TestAgent",
                "description": "A test AI agent",
                "version": "1.0.0",
                "supportedInterfaces": [
                    { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                ],
                "capabilities": { "streaming": false, "pushNotifications": false },
                "skills": [
                    { "id": "test-skill", "name": "Test Skill", "description": "A test skill", "tags": ["testing"] }
                ],
                "defaultInputModes": ["text"],
                "defaultOutputModes": ["text"]
            }
            """;

    private String serverRootUrl;

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl)
                .vertx(vertx)
                .basicAuth(ADMIN_USERNAME, ADMIN_PASSWORD));
    }

    @BeforeEach
    public void setUpA2AAuth() {
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        serverRootUrl = "http://localhost:" + port;
    }

    private RequestSpecification givenAtRoot() {
        return RestAssured.given().baseUri(serverRootUrl);
    }

    private RequestSpecification givenAsAdmin() {
        return givenAtRoot().auth().preemptive().basic(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    private RequestSpecification givenAsDeveloper() {
        return givenAtRoot().auth().preemptive().basic(DEVELOPER_USERNAME, DEVELOPER_PASSWORD);
    }

    private RequestSpecification givenAsReadonly() {
        return givenAtRoot().auth().preemptive().basic(READONLY_USERNAME, READONLY_PASSWORD);
    }

    private RegistryClient adminClient() {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl).vertx(vertx)
                .basicAuth(ADMIN_USERNAME, ADMIN_PASSWORD));
    }

    private RegistryClient developerClient() {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl).vertx(vertx)
                .basicAuth(DEVELOPER_USERNAME, DEVELOPER_PASSWORD));
    }

    // --- Entitled endpoint requires authentication ---

    @Test
    public void testEntitledEndpointReturns401WhenUnauthenticated() {
        givenAtRoot()
                .when()
                .get("/.well-known/agents/entitled")
                .then()
                .statusCode(401);
    }

    @Test
    public void testSearchEndpointReturns401WhenUnauthenticated() {
        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body("{\"limit\": 10}")
                .post("/.well-known/agents/search")
                .then()
                .statusCode(401);
    }

    // --- Public endpoint works without authentication ---

    @Test
    public void testPublicEndpointNoAuthRequired() {
        givenAtRoot()
                .when()
                .get("/.well-known/agents/public")
                .then()
                .statusCode(200)
                .body("count", notNullValue())
                .body("agents", notNullValue());
    }

    // --- Private visibility: only owner and admin can see ---

    @Test
    public void testPrivateAgentVisibleOnlyToOwnerAndAdmin() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "private-agent-auth-test";

        // Developer creates an agent card
        createAgentCard(developerClient(), groupId, artifactId, AGENT_CARD_CONTENT);
        setVisibility(adminClient(), groupId, artifactId, "private");

        // Developer (owner) can see it via entitled endpoint
        givenAsDeveloper()
                .when()
                .get("/.well-known/agents/entitled")
                .then()
                .statusCode(200)
                .body("agents.artifactId", hasItem(artifactId));

        // Admin can see it via entitled endpoint
        givenAsAdmin()
                .when()
                .get("/.well-known/agents/entitled")
                .then()
                .statusCode(200)
                .body("agents.artifactId", hasItem(artifactId));

        // Readonly user (not owner, not admin) cannot see it
        givenAsReadonly()
                .when()
                .get("/.well-known/agents/entitled")
                .then()
                .statusCode(200)
                .body("agents.artifactId", not(hasItem(artifactId)));
    }

    // --- Public visibility: visible to everyone ---

    @Test
    public void testPublicAgentVisibleToEveryone() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "public-agent-auth-test";

        createAgentCard(adminClient(), groupId, artifactId, AGENT_CARD_CONTENT);
        setVisibility(adminClient(), groupId, artifactId, "public");

        // Unauthenticated can see it via public endpoint
        givenAtRoot()
                .when()
                .get("/.well-known/agents/public")
                .then()
                .statusCode(200)
                .body("agents.artifactId", hasItem(artifactId));

        // Authenticated users can also see it via entitled endpoint
        givenAsReadonly()
                .when()
                .get("/.well-known/agents/entitled")
                .then()
                .statusCode(200)
                .body("agents.artifactId", hasItem(artifactId));
    }

    // --- Entitled (default) visibility: visible to any authenticated user ---

    @Test
    public void testEntitledAgentVisibleToAuthenticatedUsers() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "entitled-agent-auth-test";

        createAgentCard(adminClient(), groupId, artifactId, AGENT_CARD_CONTENT);
        // No explicit visibility label set — defaults to "entitled"

        // Authenticated readonly user can see it
        givenAsReadonly()
                .when()
                .get("/.well-known/agents/entitled")
                .then()
                .statusCode(200)
                .body("agents.artifactId", hasItem(artifactId));

        // Not visible on the public endpoint (no public label)
        givenAtRoot()
                .when()
                .get("/.well-known/agents/public")
                .then()
                .statusCode(200)
                .body("agents.artifactId", not(hasItem(artifactId)));
    }

    // --- Advanced search respects visibility ---

    @Test
    public void testAdvancedSearchRespectsPrivateVisibility() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "private-search-test";

        createAgentCard(developerClient(), groupId, artifactId, AGENT_CARD_CONTENT);
        setVisibility(adminClient(), groupId, artifactId, "private");

        String requestBody = """
                {
                    "limit": 50,
                    "offset": 0
                }
                """;

        // Readonly user cannot see private agent via search
        givenAsReadonly()
                .when()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("agents.artifactId", not(hasItem(artifactId)));

        // Developer (owner) can see it via search
        givenAsDeveloper()
                .when()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .post("/.well-known/agents/search")
                .then()
                .statusCode(200)
                .body("agents.artifactId", hasItem(artifactId));
    }

    // --- Helpers ---

    private void createAgentCard(RegistryClient client, String groupId, String artifactId,
            String content) {
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AGENT_CARD);

        CreateVersion createVersion = new CreateVersion();
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(versionContent);
        createArtifact.setFirstVersion(createVersion);

        client.groups().byGroupId(groupId).artifacts().post(createArtifact);
    }

    private void setVisibility(RegistryClient client, String groupId, String artifactId,
            String visibility) {
        EditableArtifactMetaData meta = new EditableArtifactMetaData();
        Labels labels = new Labels();
        labels.setAdditionalData(Map.of("apicurio.agent.visibility", visibility));
        meta.setLabels(labels);
        client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(meta);
    }
}
