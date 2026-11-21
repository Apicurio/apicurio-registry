package io.apicurio.registry.agents.inventory;

import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Read-only storage refuses a sync before the inventory source is read.
 */
@QuarkusTest
@TestProfile(AgentInventoryGitOpsTestProfile.class)
class AgentInventoryGitOpsTest {

    private static final String LOCATION = "projects/gitops-test/locations/us-central1";

    @Test
    void refusedBeforeReadingTheSource() {
        GoogleAgentRegistryStub google = GoogleAgentRegistryStub.get();
        google.reset(LOCATION);

        given().contentType(ContentTypes.APPLICATION_JSON)
                .body(Map.of("source", "GOOGLE_AGENT_REGISTRY", "location", LOCATION))
                .post("/apis/registry/v3/groups/agents/agent-inventory-sync").then()
                .statusCode(409)
                .body("title", equalTo("Unsupported write operation. Storage is in read-only mode."));
        assertEquals(List.of(), google.authorizations(LOCATION));
    }
}
