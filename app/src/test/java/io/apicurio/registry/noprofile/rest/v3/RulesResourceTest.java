package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

@QuarkusTest
public class RulesResourceTest extends AbstractResourceTestBase {

    @Test
    public void testListRuleTypes() {
        given().when().contentType(CT_JSON).get("/registry/v3/rules/types").then().statusCode(200)
                .body("$", hasItems("VALIDITY", "COMPATIBILITY", "INTEGRITY"));
    }

    @Test
    public void testListCompatibilityRuleValues() {
        given().when().contentType(CT_JSON).get("/registry/v3/rules/types/COMPATIBILITY/values").then()
                .statusCode(200)
                .body("ruleType", equalTo("COMPATIBILITY"))
                .body("values", containsInAnyOrder("NONE", "BACKWARD", "BACKWARD_TRANSITIVE", "FORWARD",
                        "FORWARD_TRANSITIVE", "FULL", "FULL_TRANSITIVE"));
    }

    @Test
    public void testListValidityRuleValues() {
        given().when().contentType(CT_JSON).get("/registry/v3/rules/types/VALIDITY/values").then()
                .statusCode(200)
                .body("ruleType", equalTo("VALIDITY"))
                .body("values", containsInAnyOrder("NONE", "SYNTAX_ONLY", "FULL"));
    }

    @Test
    public void testListIntegrityRuleValues() {
        given().when().contentType(CT_JSON).get("/registry/v3/rules/types/INTEGRITY/values").then()
                .statusCode(200)
                .body("ruleType", equalTo("INTEGRITY"))
                .body("values", containsInAnyOrder("NONE", "REFS_EXIST", "ALL_REFS_MAPPED", "NO_DUPLICATES",
                        "NO_CIRCULAR_REFERENCES", "FULL"));
    }

    @Test
    public void testUnknownRuleTypeReturnsBadRequest() {
        given().when().contentType(CT_JSON).get("/registry/v3/rules/types/NOT_A_RULE/values").then()
                .statusCode(400);
    }
}
