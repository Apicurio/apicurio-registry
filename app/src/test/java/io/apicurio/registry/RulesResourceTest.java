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

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.ccompat.rest.RestConstants;
import io.apicurio.registry.rest.beans.Rule;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
public class RulesResourceTest {

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
        rule.setName("SyntaxValidationRule");
        rule.setConfig("");
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
            .body("message", equalTo("A rule named 'SyntaxValidationRule' already exists."));
    }

}
