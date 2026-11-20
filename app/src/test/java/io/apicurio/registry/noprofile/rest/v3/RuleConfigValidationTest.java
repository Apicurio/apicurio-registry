package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
public class RuleConfigValidationTest extends AbstractResourceTestBase {

    private static final String GROUP = "RuleConfigValidationTest";
    private static final String BOGUS_COMPATIBILITY = "{\"ruleType\":\"COMPATIBILITY\",\"config\":\"BOGUS\"}";

    @Test
    public void testInvalidRuleConfigIsRejected() throws Exception {
        String artifactId = "invalid-rule-config";
        createArtifact(GROUP, artifactId, ArtifactType.JSON, "{\"type\":\"object\"}",
                ContentTypes.APPLICATION_JSON);

        given().when().contentType(CT_JSON).body(BOGUS_COMPATIBILITY).pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(400);

        given().when().contentType(CT_JSON).body("{\"ruleType\":\"VALIDITY\",\"config\":\"FULL\"}")
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(204);
        given().when().contentType(CT_JSON).body("{\"config\":\"BOGUS\"}").pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/VALIDITY").then()
                .statusCode(400);
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/VALIDITY").then()
                .statusCode(200).body("config", equalTo("FULL"));

        given().when().contentType(CT_JSON).body("{\"ruleType\":\"INTEGRITY\",\"config\":\"REFS_EXIST,BOGUS\"}")
                .pathParam("groupId", GROUP)
                .post("/registry/v3/groups/{groupId}/rules").then().statusCode(400);

        given().when().contentType(CT_JSON).body(BOGUS_COMPATIBILITY).post("/registry/v3/admin/rules").then()
                .statusCode(400);
    }

    @Test
    public void testInvalidGovernanceLevelIsRejected() throws Exception {
        String group = GROUP + "-governance";
        String artifactId = "invalid-governance-level";
        createArtifact(group, artifactId, ArtifactType.JSON, "{\"type\":\"object\"}",
                ContentTypes.APPLICATION_JSON);

        // Artifact rule: an invalid level is rejected on create and update, and the stored level is kept
        given().when().contentType(CT_JSON).body("{\"ruleType\":\"GOVERNANCE\",\"config\":\"STRICT\"}")
                .pathParam("groupId", group).pathParam("artifactId", artifactId)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(400);
        given().when().contentType(CT_JSON).body("{\"ruleType\":\"GOVERNANCE\",\"config\":\"BASIC\"}")
                .pathParam("groupId", group).pathParam("artifactId", artifactId)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(204);
        given().when().contentType(CT_JSON).body("{\"config\":\"STRICT\"}").pathParam("groupId", group)
                .pathParam("artifactId", artifactId)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/GOVERNANCE").then()
                .statusCode(400);
        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules/GOVERNANCE").then()
                .statusCode(200).body("config", equalTo("BASIC"));

        // Group rule
        given().when().contentType(CT_JSON).body("{\"ruleType\":\"GOVERNANCE\",\"config\":\"FULL\"}")
                .pathParam("groupId", group).post("/registry/v3/groups/{groupId}/rules").then().statusCode(204);
        given().when().contentType(CT_JSON).body("{\"config\":\"STRICT\"}").pathParam("groupId", group)
                .put("/registry/v3/groups/{groupId}/rules/GOVERNANCE").then().statusCode(400);
        given().when().pathParam("groupId", group).get("/registry/v3/groups/{groupId}/rules/GOVERNANCE").then()
                .statusCode(200).body("config", equalTo("FULL"));

        // Global rule: rejected, so nothing is stored that would affect other tests
        given().when().contentType(CT_JSON).body("{\"ruleType\":\"GOVERNANCE\",\"config\":\"STRICT\"}")
                .post("/registry/v3/admin/rules").then().statusCode(400);
    }
}
