package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * A GOVERNANCE rule checks the contract metadata of an artifact when a new version is created.
 */
@QuarkusTest
public class GovernanceRuleTest extends AbstractResourceTestBase {

    private static final String GROUP = "GovernanceRuleTest";
    private static final String SCHEMA_V1 = "{\"type\":\"object\"}";
    private static final String SCHEMA_V2 = "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}";

    private void setLabels(String artifactId, Map<String, String> labels) {
        given().when().contentType(CT_JSON).body(Map.of("labels", labels)).pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId).put("/registry/v3/groups/{groupId}/artifacts/{artifactId}")
                .then().statusCode(204);
    }

    private void enableGovernance(String artifactId, String level) {
        given().when().contentType(CT_JSON).body(Map.of("ruleType", "GOVERNANCE", "config", level))
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(204);
    }

    private ValidatableResponse createVersion(String artifactId) {
        return given().when().contentType(CT_JSON)
                .body(Map.of("content", Map.of("content", SCHEMA_V2, "contentType", ContentTypes.APPLICATION_JSON)))
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then();
    }

    @Test
    public void testContractWithoutOwnerIsRejected() throws Exception {
        String artifactId = "orders-no-owner";
        createArtifact(GROUP, artifactId, ArtifactType.JSON, SCHEMA_V1, ContentTypes.APPLICATION_JSON);
        setLabels(artifactId, Map.of("contract.orders.id", "orders", "contract.orders.status", "DRAFT"));
        enableGovernance(artifactId, "BASIC");

        createVersion(artifactId).statusCode(400)
                .body("title", equalTo("Governance rule violations found"))
                .body("causes[0].description", equalTo("Contract owner team is required"))
                .body("causes[0].context", equalTo("contract.orders.owner.team"));
    }

    @Test
    public void testContractWithOwnerIsAccepted() throws Exception {
        String artifactId = "orders-with-owner";
        createArtifact(GROUP, artifactId, ArtifactType.JSON, SCHEMA_V1, ContentTypes.APPLICATION_JSON);
        setLabels(artifactId, Map.of("contract.orders.id", "orders", "contract.orders.status", "DRAFT",
                "contract.orders.owner.team", "payments"));
        enableGovernance(artifactId, "BASIC");

        createVersion(artifactId).statusCode(200).body("version", equalTo("2"));
    }

    @Test
    public void testArtifactWithoutContractMetadataIsNotGoverned() throws Exception {
        String artifactId = "not-a-contract";
        createArtifact(GROUP, artifactId, ArtifactType.JSON, SCHEMA_V1, ContentTypes.APPLICATION_JSON);
        enableGovernance(artifactId, "FULL");

        createVersion(artifactId).statusCode(200).body("version", equalTo("2"));
    }
}
