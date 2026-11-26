package io.apicurio.registry.agents.inventory;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.agents.a2a.signature.SignatureVerificationProfiles;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

/**
 * Inventory sync creates Agent Cards through the v3 create path, so the signature policy applies to it:
 * in {@code require} mode an unsigned card from the inventory is reported as rejected and not stored.
 */
@QuarkusTest
@TestProfile(AgentInventorySignaturePolicyTest.RequireSignedCards.class)
@WithKubernetesTestServer(crud = false)
public class AgentInventorySignaturePolicyTest extends AbstractResourceTestBase {

    private static final String LOCATION = "projects/signaturepolicy/locations/us-central1";

    public static class RequireSignedCards implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> props = new HashMap<>(AgentInventoryTestProfile.inventoryOverrides());
            props.putAll(SignatureVerificationProfiles.overrides("require", SignatureVerificationProfiles.REQUIRE_JWKS));
            return props;
        }
    }

    @Test
    void unsignedCardFromTheInventoryIsRejected() {
        String group = TestUtils.generateGroupId();
        GoogleAgentRegistryStub.get().reset(LOCATION);
        GoogleAgentRegistryStub.get().pages(LOCATION, "{\"agents\":[{\"name\":\"" + LOCATION
                + "/agents/billing\",\"agentId\":\"billing\",\"displayName\":\"billing\",\"card\":{\"type\":"
                + "\"A2A_AGENT_CARD\",\"content\":{\"name\":\"Billing agent\",\"description\":\"Billing\","
                + "\"version\":\"2.0.0\",\"supportedInterfaces\":[{\"url\":\"https://billing.example.com/a2a\","
                + "\"protocolBinding\":\"JSONRPC\",\"protocolVersion\":\"1.0\"}],\"capabilities\":{},"
                + "\"defaultInputModes\":[\"text\"],\"defaultOutputModes\":[\"text\"],\"skills\":[{\"id\":"
                + "\"bill\",\"name\":\"Bill\",\"description\":\"Billing\",\"tags\":[]}]}}}]}");

        given().contentType(ContentTypes.APPLICATION_JSON)
                .body(Map.of("source", "GOOGLE_AGENT_REGISTRY", "location", LOCATION))
                .post(registryV3ApiUrl + "/groups/" + group + "/agent-inventory-sync").then().statusCode(200)
                .body("items.status", contains("REJECTED"))
                .body("items[0].causes.description",
                        contains("The Agent Card is not signed: no verifiable signature"));

        given().get(registryV3ApiUrl + "/search/artifacts?groupId=" + group).then().body("count", equalTo(0));
    }
}
