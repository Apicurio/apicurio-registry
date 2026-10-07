package io.apicurio.registry.federation;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.Vertx;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Federated search with authentication on: who may search, what the caller sees of this registry,
 * and the guarantee that what leaves for a peer is public and anonymous.
 */
@QuarkusTest
@TestProfile(FederatedSearchAuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class FederatedSearchAuthTest extends AbstractResourceTestBase {

    private static final String PEERS_PATH = "/registry/v3/admin/peers";
    private static final String SEARCH_PATH = "/registry/v3/search/federated";

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

    private final List<StubPeer> stubs = new ArrayList<>();

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return client("alice", "alice", vertx);
    }

    private RegistryClient client(String user, String password, Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create().registryUrl(registryV3ApiUrl)
                .vertx(vertx).basicAuth(user, password));
    }

    private static RequestSpecification as(String user) {
        return given().auth().preemptive().basic(user, user);
    }

    @AfterEach
    void cleanUp() throws Exception {
        List<String> peerIds = as("alice").queryParam("limit", 1000).get(PEERS_PATH).then().statusCode(200)
                .extract().jsonPath().getList("peers.peerId", String.class);
        for (String peerId : peerIds) {
            as("alice").pathParam("peerId", peerId).delete(PEERS_PATH + "/{peerId}").then().statusCode(204);
        }
        for (StubPeer stub : stubs) {
            stub.close();
        }
        stubs.clear();
    }

    private void card(RegistryClient client, String artifactId, String skill, String visibility) {
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AGENT_CARD);
        Labels labels = new Labels();
        labels.setAdditionalData(Map.of("apicurio.agent.visibility", visibility));
        createArtifact.setLabels(labels);
        CreateVersion createVersion = new CreateVersion();
        VersionContent content = new VersionContent();
        content.setContent(CARD.replace("test-skill", skill));
        content.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(content);
        createArtifact.setFirstVersion(createVersion);
        client.groups().byGroupId(TestUtils.generateGroupId()).artifacts().post(createArtifact);
    }

    private static void registerPeer(String peerId, String url) {
        as("alice").contentType(ContentType.JSON).body(Map.of("peerId", peerId, "url", url)).post(PEERS_PATH).then()
                .statusCode(204);
    }

    private static ValidatableResponse searchAs(String user, String skill) {
        return as(user).queryParam("skill", skill).get(SEARCH_PATH).then().statusCode(200);
    }

    @Test
    void searchNeedsACallerWhoMayRead() {
        given().get(SEARCH_PATH).then().statusCode(401);
        as("carol").get(SEARCH_PATH).then().statusCode(403);
        as("duncan").get(SEARCH_PATH).then().statusCode(200);
    }

    @Test
    void theCallerSeesWhatTheirVisibilityAllowsHereAndAPeerOnlyEverReturnsPublicCards() throws Exception {
        String skill = "auth-" + UUID.randomUUID();
        card(client("bob1", "bob1", vertx), "public-card", skill, "public");
        card(client("bob1", "bob1", vertx), "entitled-card", skill, "entitled");
        card(client("bob1", "bob1", vertx), "private-card", skill, "private");
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        registerPeer("self", "http://localhost:" + port);

        // The administrator sees everything here, the owner sees their own private card, and a
        // reader who owns nothing sees only what is public or entitled.
        searchAs("alice", skill)
                .body("agents.findAll { it.source == 'local' }.artifactId",
                        containsInAnyOrder("public-card", "entitled-card", "private-card"));
        searchAs("bob1", skill)
                .body("agents.findAll { it.source == 'local' }.artifactId",
                        containsInAnyOrder("public-card", "entitled-card", "private-card"));
        searchAs("duncan", skill)
                .body("agents.findAll { it.source == 'local' }.artifactId",
                        containsInAnyOrder("public-card", "entitled-card"));

        // Whoever asks, the peer is queried anonymously for public cards, so that is all it returns.
        for (String user : List.of("alice", "bob1", "duncan")) {
            searchAs(user, skill).body("sources.outcome", contains("ok", "ok"))
                    .body("agents.findAll { it.source == 'self' }.artifactId", contains("public-card"));
        }
    }

    @Test
    void nothingThatIdentifiesTheCallerIsSentToAPeer() throws Exception {
        String skill = "creds-" + UUID.randomUUID();
        StubPeer peer = StubPeer.start(vertx, (request, response) -> StubPeer.publicAgents(response, 0));
        stubs.add(peer);
        registerPeer("stub", peer.url());

        as("alice").header("Cookie", "session=secret").queryParam("skill", skill).get(SEARCH_PATH).then()
                .statusCode(200).body("sources.outcome", contains("ok", "ok"));

        assertEquals(1, peer.requests().size());
        assertNull(peer.requests().get(0).header("Authorization"));
        assertNull(peer.requests().get(0).header("Cookie"));
    }

    @Test
    void aPeerThatAnsweredAsIfAuthenticationWereOffIsStillDiscardedWithoutTheConfirmation() throws Exception {
        // The cards a peer without the public-only mode would return to any anonymous caller.
        StubPeer legacy = StubPeer.start(vertx, (request, response) -> StubPeer.json(response,
                "{\"count\":1,\"agents\":[{\"groupId\":\"g\",\"artifactId\":\"private-card\"}]}"));
        stubs.add(legacy);
        registerPeer("legacy", legacy.url());

        as("alice").get(SEARCH_PATH).then().statusCode(200)
                .body("agents.findAll { it.source == 'legacy' }", empty())
                .body("sources.findAll { it.source == 'legacy' }.outcome", contains("unsupported"));
    }
}
