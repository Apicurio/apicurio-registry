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

package io.apicurio.tests.smokeTests;

import io.apicurio.registry.ccompat.rest.RestConstants;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.types.RuleType;
import io.apicurio.tests.BaseIT;
import io.quarkus.test.junit.SubstrateTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.equalTo;

@SubstrateTest
public class RulesResourceIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(RulesResourceIT.class);

    @Test
    public void testGlobalRules() {
        LOGGER.info("Adding rule {} with config {}", RuleType.VALIDITY, "syntax-validation-config");

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("syntax-validation-config");

        given()
            .when()
                .contentType(RestConstants.JSON).body(rule).post("/rules")
            .then()
                .statusCode(204)
                .body(anything())
            .log().all();

        LOGGER.info("Adding rule {} with config {}", RuleType.COMPATIBILITY, "syntax-validation-config");

        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("compatibility-config");

        given()
            .when()
                .contentType(RestConstants.JSON).body(rule).post("/rules")
            .then()
                .statusCode(204)
                .body(anything())
            .log().all();

        LOGGER.info("Getting 2 rules: {}, {} ", RuleType.VALIDITY, RuleType.COMPATIBILITY);

        given()
            .when()
                .get("/rules")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("[0]", equalTo("VALIDATION"))
                .body("[1]", equalTo("COMPATIBILITY"))
                .body("[2]", nullValue())
            .log().all();
    }
}
