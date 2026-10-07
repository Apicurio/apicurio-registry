package io.apicurio.registry.federation;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.NewPeer;
import io.apicurio.registry.rest.client.models.Peer;
import io.apicurio.registry.rest.client.models.PeerSearchResults;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.UpdatePeer;
import io.apicurio.registry.rest.v3.beans.UpdateConfigurationProperty;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Peer management API behaviour shared by every storage variant that accepts writes.
 */
public abstract class AbstractPeersResourceTest extends AbstractResourceTestBase {

    static final String PEERS_PATH = "/registry/v3/admin/peers";
    static final String PEER_PATH = PEERS_PATH + "/{peerId}";
    private static final String READ_ONLY_PROPERTY_PATH =
            "/registry/v3/admin/config/properties/apicurio.storage.read-only.enabled";

    @AfterEach
    void deleteAllPeers() {
        List<String> peerIds = given().when().queryParam("limit", 1000).get(PEERS_PATH)
                .then().statusCode(200).extract().jsonPath().getList("peers.peerId", String.class);
        for (String peerId : peerIds) {
            given().when().pathParam("peerId", peerId).delete(PEER_PATH).then().statusCode(204);
        }
    }

    static Map<String, Object> newPeer(String peerId, String url) {
        Map<String, Object> peer = new HashMap<>();
        peer.put("peerId", peerId);
        peer.put("url", url);
        return peer;
    }

    static ValidatableResponse createPeer(Map<String, Object> body) {
        return given().when().contentType(ContentType.JSON).body(body).post(PEERS_PATH).then();
    }

    static void assertProblem(ValidatableResponse response, int status, String name, String title) {
        response.statusCode(status)
                .contentType(ContentType.JSON)
                .body("status", equalTo(status))
                .body("name", equalTo(name))
                .body("title", equalTo(title));
    }

    @Test
    void createGetUpdateDeleteRoundTrip() {
        Map<String, Object> body = newPeer("eu-registry", "https://registry.eu.example.com");
        body.put("name", "EU registry");
        body.put("description", "Registry serving the EU region.");
        body.put("enabled", true);
        body.put("credentialSecretRef", "eu-registry");
        createPeer(body).statusCode(204);

        given().when().pathParam("peerId", "eu-registry").get(PEER_PATH).then()
                .statusCode(200)
                .body("peerId", equalTo("eu-registry"))
                .body("url", equalTo("https://registry.eu.example.com"))
                .body("name", equalTo("EU registry"))
                .body("description", equalTo("Registry serving the EU region."))
                .body("enabled", equalTo(true))
                .body("credentialSecretRef", equalTo("eu-registry"));

        Map<String, Object> update = new HashMap<>();
        update.put("url", "https://registry-2.eu.example.com/registry");
        update.put("name", "EU registry 2");
        update.put("description", "Moved.");
        update.put("enabled", false);
        update.put("credentialSecretRef", "eu-registry-2");
        given().when().contentType(ContentType.JSON).body(update).pathParam("peerId", "eu-registry")
                .put(PEER_PATH).then().statusCode(204);

        given().when().pathParam("peerId", "eu-registry").get(PEER_PATH).then()
                .statusCode(200)
                .body("peerId", equalTo("eu-registry"))
                .body("url", equalTo("https://registry-2.eu.example.com/registry"))
                .body("name", equalTo("EU registry 2"))
                .body("description", equalTo("Moved."))
                .body("enabled", equalTo(false))
                .body("credentialSecretRef", equalTo("eu-registry-2"));

        given().when().pathParam("peerId", "eu-registry").delete(PEER_PATH).then().statusCode(204);

        assertProblem(given().when().pathParam("peerId", "eu-registry").get(PEER_PATH).then(), 404,
                "PeerNotFoundException", "No peer registry with id 'eu-registry' was found.");
    }

    @Test
    void atMostTheConfiguredNumberOfPeersCanBeEnabled() {
        for (int i = 0; i < 16; i++) {
            createPeer(newPeer("limit-" + i, "https://limit-" + i + ".example.com")).statusCode(204);
        }
        String title = "At most 16 peers can be enabled. Disable or delete a peer first.";
        assertProblem(createPeer(newPeer("limit-16", "https://limit-16.example.com")), 409, "ConflictException",
                title);

        // A disabled peer is not queried, so it does not count, but it cannot be enabled past the limit.
        Map<String, Object> disabled = newPeer("limit-disabled", "https://limit-disabled.example.com");
        disabled.put("enabled", false);
        createPeer(disabled).statusCode(204);
        Map<String, Object> enable = new HashMap<>();
        enable.put("url", "https://limit-disabled.example.com");
        enable.put("enabled", true);
        assertProblem(given().when().contentType(ContentType.JSON).body(enable)
                .pathParam("peerId", "limit-disabled").put(PEER_PATH).then(), 409, "ConflictException", title);

        // An enabled peer can still be edited, and disabling one makes room for another.
        Map<String, Object> edit = new HashMap<>();
        edit.put("url", "https://limit-0-moved.example.com");
        edit.put("enabled", true);
        given().when().contentType(ContentType.JSON).body(edit).pathParam("peerId", "limit-0").put(PEER_PATH)
                .then().statusCode(204);
        edit.put("enabled", false);
        given().when().contentType(ContentType.JSON).body(edit).pathParam("peerId", "limit-0").put(PEER_PATH)
                .then().statusCode(204);
        given().when().contentType(ContentType.JSON).body(enable).pathParam("peerId", "limit-disabled")
                .put(PEER_PATH).then().statusCode(204);
    }

    @Test
    void sdkRoundTrip() throws Exception {
        NewPeer newPeer = new NewPeer();
        newPeer.setPeerId("sdk-peer");
        newPeer.setUrl("https://registry.sdk.example.com");
        newPeer.setName("SDK peer");
        newPeer.setCredentialSecretRef("sdk-peer-token");
        clientV3.admin().peers().post(newPeer);

        Peer peer = clientV3.admin().peers().byPeerId("sdk-peer").get();
        assertEquals("sdk-peer", peer.getPeerId());
        assertEquals("https://registry.sdk.example.com", peer.getUrl());
        assertEquals("SDK peer", peer.getName());
        assertNull(peer.getDescription());
        assertEquals(Boolean.TRUE, peer.getEnabled());
        assertEquals("sdk-peer-token", peer.getCredentialSecretRef());

        UpdatePeer updatePeer = new UpdatePeer();
        updatePeer.setUrl("https://registry.sdk.example.com");
        updatePeer.setEnabled(false);
        clientV3.admin().peers().byPeerId("sdk-peer").put(updatePeer);

        PeerSearchResults results = clientV3.admin().peers().get();
        assertEquals(1, results.getCount());
        assertEquals(1, results.getPeers().size());
        Peer listed = results.getPeers().get(0);
        assertEquals("sdk-peer", listed.getPeerId());
        assertEquals(Boolean.FALSE, listed.getEnabled());
        assertNull(listed.getName());
        assertNull(listed.getCredentialSecretRef());

        ProblemDetails duplicate = assertThrows(ProblemDetails.class,
                () -> clientV3.admin().peers().post(newPeer));
        assertEquals(409, duplicate.getStatus());
        assertEquals("PeerAlreadyExistsException", duplicate.getName());

        clientV3.admin().peers().byPeerId("sdk-peer").delete();

        ProblemDetails missing = assertThrows(ProblemDetails.class,
                () -> clientV3.admin().peers().byPeerId("sdk-peer").get());
        assertEquals(404, missing.getStatus());
        assertEquals("PeerNotFoundException", missing.getName());
    }

    @Test
    void enabledDefaultsToTrueAndDisabledPeersAreListed() {
        createPeer(newPeer("default-enabled", "https://a.example.com")).statusCode(204);
        Map<String, Object> disabled = newPeer("disabled-peer", "https://b.example.com");
        disabled.put("enabled", false);
        createPeer(disabled).statusCode(204);

        given().when().get(PEERS_PATH).then()
                .statusCode(200)
                .body("count", equalTo(2))
                .body("peers.peerId", contains("default-enabled", "disabled-peer"))
                .body("peers.enabled", contains(true, false));
    }

    @Test
    void updateClearsOmittedOptionalFields() {
        Map<String, Object> body = newPeer("full-peer", "https://full.example.com");
        body.put("name", "Full");
        body.put("description", "Every field set.");
        body.put("credentialSecretRef", "full-peer");
        createPeer(body).statusCode(204);

        Map<String, Object> update = new HashMap<>();
        update.put("url", "https://full.example.com");
        update.put("enabled", true);
        given().when().contentType(ContentType.JSON).body(update).pathParam("peerId", "full-peer")
                .put(PEER_PATH).then().statusCode(204);

        given().when().pathParam("peerId", "full-peer").get(PEER_PATH).then()
                .statusCode(200)
                .body("name", nullValue())
                .body("description", nullValue())
                .body("credentialSecretRef", nullValue())
                .body("enabled", equalTo(true));
    }

    @Test
    void updateRequiresEnabled() {
        createPeer(newPeer("needs-enabled", "https://enabled.example.com")).statusCode(204);

        Map<String, Object> update = new HashMap<>();
        update.put("url", "https://enabled.example.com");
        assertProblem(given().when().contentType(ContentType.JSON).body(update)
                        .pathParam("peerId", "needs-enabled").put(PEER_PATH).then(),
                400, "MissingRequiredParameterException", "Request is missing a required parameter: enabled");
    }

    @Test
    void listPagesInPeerIdOrderWithTotalCount() {
        for (String peerId : List.of("peer-e", "peer-c", "peer-a", "peer-d", "peer-b")) {
            createPeer(newPeer(peerId, "https://" + peerId + ".example.com")).statusCode(204);
        }

        given().when().queryParam("limit", 2).get(PEERS_PATH).then()
                .statusCode(200)
                .body("count", equalTo(5))
                .body("peers.peerId", contains("peer-a", "peer-b"));

        given().when().queryParam("limit", 2).queryParam("offset", 2).get(PEERS_PATH).then()
                .statusCode(200)
                .body("count", equalTo(5))
                .body("peers.peerId", contains("peer-c", "peer-d"));

        given().when().queryParam("limit", 2).queryParam("offset", 4).get(PEERS_PATH).then()
                .statusCode(200)
                .body("count", equalTo(5))
                .body("peers.peerId", contains("peer-e"));

        given().when().queryParam("offset", 10).get(PEERS_PATH).then()
                .statusCode(200)
                .body("count", equalTo(5))
                .body("peers", empty());
    }

    static Stream<Arguments> invalidNewPeers() {
        String badRef = "Peer credential secret reference is invalid.";
        String linkLocal = "Peer url host must not be a link-local address.";
        return Stream.of(
                Arguments.of(newPeer("EU-Registry", "https://a.example.com"),
                        "Peer id must contain only lowercase letters, digits, '.', '_' or '-' (max 256 characters)."),
                Arguments.of(newPeer("local", "https://a.example.com"), "Peer id 'local' is reserved."),
                Arguments.of(newPeer(null, "https://a.example.com"), "Peer id is required."),
                Arguments.of(newPeer("no-url", null), "Peer url is required."),
                Arguments.of(newPeer("ftp", "ftp://a.example.com"), "Peer url must use https."),
                Arguments.of(newPeer("plain-http", "http://a.example.com"),
                        "Peer url must use https. Plain http requires "
                                + "apicurio.federation.peer.insecure-http.enabled=true."),
                Arguments.of(newPeer("loopback", "https://127.0.0.1:8443"),
                        "Peer url host must not be a loopback address unless "
                                + "apicurio.federation.peer.loopback.enabled=true."),
                Arguments.of(newPeer("localhost", "https://localhost:8443"),
                        "Peer url host must not be a loopback address unless "
                                + "apicurio.federation.peer.loopback.enabled=true."),
                Arguments.of(newPeer("metadata", "https://169.254.169.254"), linkLocal),
                Arguments.of(newPeer("mapped", "https://[::ffff:169.254.169.254]"), linkLocal),
                Arguments.of(newPeer("compatible", "https://[::a9fe:a9fe]"), linkLocal),
                Arguments.of(newPeer("numeric", "https://2852039166"),
                        "Peer url host is not a valid IPv4 address. "
                                + "Use four dotted decimal octets without leading zeros."),
                Arguments.of(newPeer("userinfo", "https://user:secret@a.example.com"),
                        "Peer url must not contain userinfo."),
                Arguments.of(newPeer("fragment", "https://a.example.com/#top"),
                        "Peer url must not contain a fragment."),
                Arguments.of(newPeer("query", "https://a.example.com/?tenant=eu"),
                        "Peer url must not contain a query."),
                Arguments.of(withRef(newPeer("traversal", "https://a.example.com"), "../token"), badRef),
                Arguments.of(withRef(newPeer("projected", "https://a.example.com"), "..data"), badRef));
    }

    private static Map<String, Object> withRef(Map<String, Object> peer, String credentialSecretRef) {
        peer.put("credentialSecretRef", credentialSecretRef);
        return peer;
    }

    @ParameterizedTest
    @MethodSource("invalidNewPeers")
    void createRejectsInvalidPeers(Map<String, Object> body, String expectedTitle) {
        assertProblem(createPeer(body), 400, "InvalidPeerException", expectedTitle);

        given().when().get(PEERS_PATH).then().statusCode(200).body("count", equalTo(0));
    }

    @Test
    void updateAppliesTheSameUrlPolicy() {
        createPeer(newPeer("policy-update", "https://policy.example.com")).statusCode(204);

        Map<String, Object> update = new HashMap<>();
        update.put("url", "https://[fe80::1]");
        update.put("enabled", true);
        assertProblem(given().when().contentType(ContentType.JSON).body(update)
                        .pathParam("peerId", "policy-update").put(PEER_PATH).then(),
                400, "InvalidPeerException", "Peer url host must not be a link-local address.");

        given().when().pathParam("peerId", "policy-update").get(PEER_PATH).then()
                .statusCode(200).body("url", equalTo("https://policy.example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "Policy-Peer", "local" })
    void pathPeerIdIsValidated(String peerId) {
        createPeer(newPeer("policy-peer", "https://policy.example.com")).statusCode(204);

        given().when().pathParam("peerId", peerId).get(PEER_PATH).then()
                .statusCode(400).body("name", equalTo("InvalidPeerException"));
        given().when().pathParam("peerId", peerId).delete(PEER_PATH).then()
                .statusCode(400).body("name", equalTo("InvalidPeerException"));
        Map<String, Object> update = new HashMap<>();
        update.put("url", "https://policy.example.com");
        update.put("enabled", false);
        given().when().contentType(ContentType.JSON).body(update).pathParam("peerId", peerId)
                .put(PEER_PATH).then()
                .statusCode(400).body("name", equalTo("InvalidPeerException"));

        // The lowercase peer is untouched: an uppercase path must never address it on any dialect
        given().when().pathParam("peerId", "policy-peer").get(PEER_PATH).then()
                .statusCode(200).body("enabled", equalTo(true));
    }

    @Test
    void unknownPeerIsNotFound() {
        String title = "No peer registry with id 'missing-peer' was found.";
        assertProblem(given().when().pathParam("peerId", "missing-peer").get(PEER_PATH).then(),
                404, "PeerNotFoundException", title);
        assertProblem(given().when().pathParam("peerId", "missing-peer").delete(PEER_PATH).then(),
                404, "PeerNotFoundException", title);
        Map<String, Object> update = new HashMap<>();
        update.put("url", "https://missing.example.com");
        update.put("enabled", true);
        assertProblem(given().when().contentType(ContentType.JSON).body(update)
                        .pathParam("peerId", "missing-peer").put(PEER_PATH).then(),
                404, "PeerNotFoundException", title);
    }

    @Test
    void duplicatePeerIdConflicts() {
        createPeer(newPeer("dup-peer", "https://dup.example.com")).statusCode(204);
        assertProblem(createPeer(newPeer("dup-peer", "https://other.example.com")), 409,
                "PeerAlreadyExistsException", "A peer registry with id 'dup-peer' already exists.");

        given().when().pathParam("peerId", "dup-peer").get(PEER_PATH).then()
                .statusCode(200).body("url", equalTo("https://dup.example.com"));
    }

    @Test
    void readOnlyModeRejectsWritesButServesReads() {
        createPeer(newPeer("ro-peer", "https://ro.example.com")).statusCode(204);
        Map<String, Object> unchanged = new HashMap<>();
        unchanged.put("url", "https://ro.example.com");
        unchanged.put("enabled", true);
        setReadOnly(true);
        try {
            // Re-applying the stored values is idempotent, so probing with it cannot change state
            // while waiting for read-only mode to take effect.
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> given().when()
                    .contentType(ContentType.JSON).body(unchanged).pathParam("peerId", "ro-peer")
                    .put(PEER_PATH).then().statusCode(409));

            String title = "Unsupported write operation. Storage is in read-only mode.";
            assertProblem(createPeer(newPeer("ro-new", "https://ro-new.example.com")), 409,
                    "ReadOnlyStorageException", title);
            Map<String, Object> update = new HashMap<>(unchanged);
            update.put("enabled", false);
            assertProblem(given().when().contentType(ContentType.JSON).body(update)
                            .pathParam("peerId", "ro-peer").put(PEER_PATH).then(),
                    409, "ReadOnlyStorageException", title);
            assertProblem(given().when().pathParam("peerId", "ro-peer").delete(PEER_PATH).then(),
                    409, "ReadOnlyStorageException", title);

            given().when().get(PEERS_PATH).then()
                    .statusCode(200).body("count", equalTo(1)).body("peers.peerId", contains("ro-peer"));
            given().when().pathParam("peerId", "ro-peer").get(PEER_PATH).then()
                    .statusCode(200).body("enabled", equalTo(true));
        } finally {
            setReadOnly(false);
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> given().when()
                .pathParam("peerId", "ro-peer").delete(PEER_PATH).then().statusCode(204));
        given().when().get(PEERS_PATH).then().statusCode(200).body("peers", hasSize(0));
    }

    private static void setReadOnly(boolean readOnly) {
        UpdateConfigurationProperty update = new UpdateConfigurationProperty();
        update.setValue(Boolean.toString(readOnly));
        given().when().contentType(ContentType.JSON).body(update).put(READ_ONLY_PROPERTY_PATH)
                .then().statusCode(204);
    }
}
