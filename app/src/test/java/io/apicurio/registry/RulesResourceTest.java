/*
 * Copyright 2019 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry;

import io.apicurio.registry.ccompat.rest.RestConstants;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
public class RulesResourceTest extends AbstractResourceTestBase {

    @Test
    public void testGlobalRulesEndpoint() {
        given()
            .when().contentType(RestConstants.JSON).get("/rules")
            .then()
            .statusCode(200)
            .body(anything());
    }
    
    @Test
    public void testGlobalRules() {
        // Add a global rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDATION);
        rule.setConfig("syntax-validation-config");
        given()
            .when().contentType(RestConstants.JSON).body(rule).post("/rules")
            .then()
            .statusCode(204)
            .body(anything());
        
        // Try to add the rule again - should get a 409
        given()
            .when().contentType(RestConstants.JSON).body(rule).post("/rules")
            .then()
            .statusCode(409)
            .body("code", equalTo(409))
            .body("message", equalTo("A rule named 'VALIDATION' already exists."));
        
        // Add another global rule
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("compatibility-config");
        given()
            .when().contentType(RestConstants.JSON).body(rule).post("/rules")
            .then()
            .statusCode(204)
            .body(anything());

        // Get the list of rules (should be 2 of them)
        given()
            .when().get("/rules")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("[0]", anyOf(equalTo("VALIDATION"), equalTo("COMPATIBILITY")))
            .body("[1]", anyOf(equalTo("VALIDATION"), equalTo("COMPATIBILITY")))
            .body("[2]", nullValue());
        
        // Get a single rule by name
        given()
            .when().get("/rules/COMPATIBILITY")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("type", equalTo("COMPATIBILITY"))
            .body("config", equalTo("compatibility-config"));

        // Update a rule's config
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("updated-configuration");
        given()
            .when().contentType(RestConstants.JSON).body(rule).put("/rules/COMPATIBILITY")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("type", equalTo("COMPATIBILITY"))
            .body("config", equalTo("updated-configuration"));

        // Get a single (updated) rule by name
        given()
            .when().get("/rules/COMPATIBILITY")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("type", equalTo("COMPATIBILITY"))
            .body("config", equalTo("updated-configuration"));

        // Try to update a rule's config for a rule that doesn't exist.
//        rule.setType("RuleDoesNotExist");
//        rule.setConfig("rdne-config");
//        given()
//            .when().contentType(RestConstants.JSON).body(rule).put("/rules/RuleDoesNotExist")
//            .then()
//            .statusCode(404)
//            .contentType(ContentType.JSON)
//            .body("code", equalTo(404))
//            .body("message", equalTo("No rule named 'RuleDoesNotExist' was found."));

        // Delete a rule
        given()
            .when().delete("/rules/COMPATIBILITY")
            .then()
            .statusCode(204)
            .body(anything());

        // Get a single (deleted) rule by name (should fail with a 404)
        given()
            .when().get("/rules/COMPATIBILITY")
            .then()
            .statusCode(404)
            .contentType(ContentType.JSON)
            .body("code", equalTo(404))
            .body("message", equalTo("No rule named 'COMPATIBILITY' was found."));

        // Get the list of rules (should be 1 of them)
        given()
            .when().get("/rules")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("[0]", anyOf(equalTo("VALIDATION"), equalTo("COMPATIBILITY")))
            .body("[1]", nullValue());

        // Delete all rules
        given()
            .when().delete("/rules")
            .then()
            .statusCode(204);

        // Get the list of rules (no rules now)
        given()
            .when().get("/rules")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("[0]", nullValue());

    }

}
