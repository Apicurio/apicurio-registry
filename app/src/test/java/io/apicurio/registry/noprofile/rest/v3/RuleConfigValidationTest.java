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
}
