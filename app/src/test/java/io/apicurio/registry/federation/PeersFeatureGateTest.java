package io.apicurio.registry.federation;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.apicurio.registry.federation.AbstractPeersResourceTest.PEERS_PATH;
import static io.apicurio.registry.federation.AbstractPeersResourceTest.PEER_PATH;
import static io.apicurio.registry.federation.AbstractPeersResourceTest.assertProblem;
import static io.restassured.RestAssured.given;

/**
 * With federation left at its default (disabled), every peer management endpoint answers 409.
 */
@QuarkusTest
public class PeersFeatureGateTest extends AbstractResourceTestBase {

    private static final String TITLE = "Federation is not enabled on this registry instance.";

    private static void assertDisabled(ValidatableResponse response) {
        assertProblem(response, 409, "ConflictException", TITLE);
    }

    @Test
    void everyPeerEndpointIsRejectedWhenFederationIsDisabled() {
        Map<String, Object> newPeer = Map.of("peerId", "gate-peer", "url", "https://gate.example.com");
        Map<String, Object> update = Map.of("url", "https://gate.example.com", "enabled", true);

        assertDisabled(given().when().get(PEERS_PATH).then());
        assertDisabled(given().when().contentType(ContentType.JSON).body(newPeer).post(PEERS_PATH).then());
        assertDisabled(given().when().pathParam("peerId", "gate-peer").get(PEER_PATH).then());
        assertDisabled(given().when().contentType(ContentType.JSON).body(update)
                .pathParam("peerId", "gate-peer").put(PEER_PATH).then());
        assertDisabled(given().when().pathParam("peerId", "gate-peer").delete(PEER_PATH).then());
    }
}
