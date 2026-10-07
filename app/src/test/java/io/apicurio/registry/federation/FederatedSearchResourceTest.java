package io.apicurio.registry.federation;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Federated agent search through the REST API, with stub peers and with the registry as its own peer.
 */
@QuarkusTest
@TestProfile(FederatedSearchTestProfile.class)
public class FederatedSearchResourceTest extends AbstractResourceTestBase {

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

    @AfterEach
    void cleanUp() throws Exception {
        List<String> peerIds = given().queryParam("limit", 1000).get(PEERS_PATH).then().statusCode(200).extract()
                .jsonPath().getList("peers.peerId", String.class);
        for (String peerId : peerIds) {
            given().pathParam("peerId", peerId).delete(PEERS_PATH + "/{peerId}").then().statusCode(204);
        }
        for (StubPeer stub : stubs) {
            stub.close();
        }
        stubs.clear();
    }

    private StubPeer stub(java.util.function.BiConsumer<io.vertx.core.http.HttpServerRequest,
            io.vertx.core.http.HttpServerResponse> handler) throws Exception {
        StubPeer stub = StubPeer.start(vertx, handler);
        stubs.add(stub);
        return stub;
    }

    private static void registerPeer(String peerId, String url) {
        given().contentType(ContentType.JSON).body(Map.of("peerId", peerId, "url", url)).post(PEERS_PATH).then()
                .statusCode(204);
    }

    private void card(String artifactId, String skill, String visibility) throws Exception {
        createArtifact(TestUtils.generateGroupId(), artifactId, ArtifactType.AGENT_CARD,
                CARD.replace("test-skill", skill), ContentTypes.APPLICATION_JSON, request -> {
                    if (visibility != null) {
                        Labels labels = new Labels();
                        labels.setAdditionalData(Map.of("apicurio.agent.visibility", visibility));
                        request.setLabels(labels);
                    }
                });
    }

    private static ValidatableResponse search(String skill) {
        return given().queryParam("skill", skill).get(SEARCH_PATH).then().statusCode(200);
    }

    @Test
    void withoutPeersTheAnswerIsThisRegistryAlone() throws Exception {
        String skill = "alone-" + UUID.randomUUID();
        card("only-card", skill, "public");

        search(skill).body("agents.source", contains("local")).body("agents.artifactId", contains("only-card"))
                .body("sources", hasSize(1)).body("sources[0].source", equalTo("local"))
                .body("sources[0].outcome", equalTo("ok")).body("sources[0].count", equalTo(1))
                .body("sources[0].truncated", equalTo(false)).body("sources[0].reason", nullValue());
    }

    @Test
    void mergesThisRegistrysAgentsWithAPeersAndNeverSendsItTheCallersCredentials() throws Exception {
        String skill = "merge-" + UUID.randomUUID();
        card("local-card", skill, "public");
        StubPeer peer = stub((request, response) -> StubPeer.publicAgents(response, 2, "remote-a", "remote-b"));
        registerPeer("stub-ok", peer.url());

        given().header("Authorization", "Bearer the-callers-token").header("Cookie", "session=secret")
                .queryParam("skill", skill).get(SEARCH_PATH).then().statusCode(200)
                .body("agents.source", contains("local", "stub-ok", "stub-ok"))
                .body("agents.artifactId", contains("local-card", "remote-a", "remote-b"))
                .body("sources.source", contains("local", "stub-ok"))
                .body("sources.outcome", contains("ok", "ok"))
                .body("sources.count", contains(1, 2))
                .body("sources.truncated", contains(false, false));

        assertEquals(1, peer.requests().size());
        StubPeer.Seen seen = peer.requests().get(0);
        assertEquals("/.well-known/agents", seen.path());
        assertTrue(seen.query().contains("publicOnly=true"), seen.query());
        assertTrue(seen.query().contains("skill=" + skill), seen.query());
        assertNull(seen.header("Authorization"));
        assertNull(seen.header("Cookie"));
    }

    @Test
    void everySourceHasItsOwnOutcomeAndOneHungPeerDoesNotHoldUpTheResponse() throws Exception {
        String skill = "outcomes-" + UUID.randomUUID();
        card("local-card", skill, "public");

        registerPeer("a-ok", stub((request, response) -> StubPeer.publicAgents(response, 1, "remote")).url());
        registerPeer("b-legacy", stub((request, response) -> StubPeer.json(response,
                "{\"count\":1,\"agents\":[{\"groupId\":\"g\",\"artifactId\":\"not-public\"}]}")).url());
        registerPeer("c-nosearch", stub((request, response) -> response.setStatusCode(404).end()).url());
        registerPeer("d-denied", stub((request, response) -> response.setStatusCode(403).end()).url());
        registerPeer("e-broken", stub((request, response) -> response.setStatusCode(500).end()).url());
        registerPeer("f-moved", stub((request, response) -> response.setStatusCode(301)
                .putHeader("Location", "http://127.0.0.1:1/").end()).url());
        registerPeer("g-garbled", stub((request, response) -> StubPeer.json(response, "<html>")).url());
        registerPeer("h-hung", stub((request, response) -> {
        }).url());
        StubPeer closed = StubPeer.start(vertx, (request, response) -> response.end());
        int closedPort = closed.port();
        closed.close();
        registerPeer("i-closed", "http://127.0.0.1:" + closedPort);

        long start = System.nanoTime();
        ValidatableResponse response = search(skill);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        response.body("agents.source", contains("local", "a-ok"))
                .body("sources.source", contains("local", "a-ok", "b-legacy", "c-nosearch", "d-denied", "e-broken",
                        "f-moved", "g-garbled", "h-hung", "i-closed"))
                .body("sources.outcome", contains("ok", "ok", "unsupported", "unsupported", "failed", "failed",
                        "failed", "failed", "failed", "failed"))
                .body("sources.findAll { it.reason != null }.collect { it.source + ':' + it.reason }",
                        contains("d-denied:unauthorized", "e-broken:peer_error", "f-moved:peer_error",
                                "g-garbled:invalid_response", "h-hung:timeout", "i-closed:unreachable"));
        // The hung peer is cut off at the per-peer timeout, well inside the deadline.
        assertTrue(elapsedMs < 2500, "The response took " + elapsedMs + " ms.");
    }

    @Test
    void theRegistryAsItsOwnPeerReturnsOnlyItsPublicCardsAndDoesNotRecurse() throws Exception {
        String skill = "self-" + UUID.randomUUID();
        card("public-card", skill, "public");
        card("entitled-card", skill, "entitled");
        card("unlabelled-card", skill, null);
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        registerPeer("self", "http://localhost:" + port);

        // Authentication is off, so this registry returns all three to its own caller, but what it
        // returns to a peer is public only, and a peer's answer is its own local search: asking
        // itself does not send it round again.
        search(skill).body("sources.source", contains("local", "self"))
                .body("sources.count", contains(3, 1))
                .body("agents.findAll { it.source == 'local' }.artifactId",
                        org.hamcrest.Matchers.containsInAnyOrder("public-card", "entitled-card", "unlabelled-card"))
                .body("agents.findAll { it.source == 'self' }.artifactId", contains("public-card"));
    }

    @Test
    void perSourceLimitBoundsEverySourceAndSaysWhenItTruncated() throws Exception {
        String skill = "limit-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            card("card-" + i, skill, "public");
        }
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        registerPeer("self", "http://localhost:" + port);
        StubPeer peer = stub((request, response) -> StubPeer.publicAgents(response, 5, "r1", "r2"));
        registerPeer("stub", peer.url());

        given().queryParam("skill", skill).queryParam("perSourceLimit", 2).get(SEARCH_PATH).then().statusCode(200)
                .body("agents", hasSize(6))
                .body("sources.source", contains("local", "self", "stub"))
                .body("sources.count", contains(2, 2, 2))
                .body("sources.truncated", contains(true, true, true));
        assertTrue(peer.requests().get(0).query().contains("limit=2"), peer.requests().get(0).query());
    }

    @Test
    void perSourceLimitIsBoundedByTheServer() throws Exception {
        StubPeer peer = stub((request, response) -> StubPeer.publicAgents(response, 0));
        registerPeer("stub", peer.url());

        given().queryParam("perSourceLimit", 100000).get(SEARCH_PATH).then().statusCode(200);
        given().queryParam("perSourceLimit", 0).get(SEARCH_PATH).then().statusCode(200);
        given().get(SEARCH_PATH).then().statusCode(200);

        List<String> queries = peer.requests().stream().map(StubPeer.Seen::query).toList();
        assertTrue(queries.get(0).contains("limit=100&"), queries.get(0));
        assertTrue(queries.get(1).contains("limit=1&"), queries.get(1));
        assertTrue(queries.get(2).contains("limit=20&"), queries.get(2));
    }

    @Test
    void disabledPeersAreNotSearched() throws Exception {
        StubPeer peer = stub((request, response) -> StubPeer.publicAgents(response, 0));
        given().contentType(ContentType.JSON)
                .body(Map.of("peerId", "off", "url", peer.url(), "enabled", false)).post(PEERS_PATH).then()
                .statusCode(204);

        given().get(SEARCH_PATH).then().statusCode(200).body("sources.source", contains("local"));
        assertTrue(peer.requests().isEmpty());
    }

    @Test
    void aPeerWhoseCircuitOpensIsSkippedUntilItRecovers() throws Exception {
        StubPeer broken = stub((request, response) -> response.setStatusCode(503).end());
        registerPeer("flaky", broken.url());

        for (int i = 0; i < 4; i++) {
            given().get(SEARCH_PATH).then().statusCode(200).body("sources[1].reason", equalTo("peer_error"));
        }
        given().get(SEARCH_PATH).then().statusCode(200).body("sources[1].outcome", equalTo("failed"))
                .body("sources[1].reason", equalTo("circuit_open"))
                .body("agents.findAll { it.source == 'flaky' }", empty());
        assertEquals(4, broken.requests().size(), "An open circuit must not reach the peer.");
    }

    @Test
    void theLocalSearchStillWorksWhenEveryPeerFails() throws Exception {
        String skill = "mine-" + UUID.randomUUID();
        card("local-card", skill, "public");
        registerPeer("down", stub((request, response) -> response.setStatusCode(502).end()).url());

        search(skill).body("agents.source", contains("local")).body("sources.outcome", contains("ok", "failed"))
                .body("sources[0].count", equalTo(1));
    }
}
