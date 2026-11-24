package io.apicurio.registry.agents.inventory;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Inventory sync is off by default: the endpoint answers 409 and reads nothing.
 */
@QuarkusTest
public class AgentInventoryFeatureGateTest extends AbstractResourceTestBase {

    @Test
    void disabledByDefault() {
        given().contentType(ContentTypes.APPLICATION_JSON)
                .body(Map.of("source", "KUBERNETES_MCP_SERVERS", "namespace", "team-a"))
                .post(registryV3ApiUrl + "/groups/" + TestUtils.generateGroupId() + "/agent-inventory-sync").then()
                .statusCode(409)
                .body("title", equalTo("Syncing agent inventories is not enabled."));
    }
}
