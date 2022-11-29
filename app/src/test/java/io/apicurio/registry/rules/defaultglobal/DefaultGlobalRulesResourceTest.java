/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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

package io.apicurio.registry.rules.defaultglobal;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v1.beans.Rule;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 */
@QuarkusTest
@TestProfile(DefaultGlobalRulesProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class DefaultGlobalRulesResourceTest extends AbstractResourceTestBase {

    @BeforeEach
    protected void beforeEach() throws Exception {
        setupRestAssured();
        deleteGlobalRules(1);
    }

    @Test
    public void testGlobalRulesEndpoint() {
        given()
            .when()
                .contentType(CT_JSON)
                .get("/registry/v1/rules")
            .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0]", equalTo("VALIDITY"));
    }


    @Test
    public void testDefaultGlobalRules() throws Exception {
        this.createArtifact(this.generateArtifactId(), ArtifactType.JSON, "{}");

        // Verify the default global rule exists
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v1/rules/VALIDITY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("VALIDITY"))
                    .body("config", equalTo("FULL"));
        });

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");

        // Try to add the rule again - should get a 409
        TestUtils.retry(() -> {
            given()
                .when()
                    .contentType(CT_JSON).body(rule)
                    .post("/registry/v1/rules")
                .then()
                    .statusCode(409)
                    .body("error_code", equalTo(409))
                    .body("message", equalTo("A rule named 'VALIDITY' already exists."));
        });

        // Add another global rule
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .post("/registry/v1/rules")
            .then()
                .statusCode(204)
                .body(anything());

        // Get the list of rules (should be 2 of them)
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v1/rules")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("[0]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY")))
                    .body("[1]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY")))
                    .body("[2]", nullValue());
        });

        // Override the default rule's config
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .put("/registry/v1/rules/VALIDITY")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("type", equalTo("VALIDITY"))
                .body("config", equalTo("SYNTAX_ONLY"));

        // Get a single (updated) rule by name
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v1/rules/VALIDITY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("VALIDITY"))
                    .body("config", equalTo("SYNTAX_ONLY"));
        });

        // Delete the updated default rule
        given()
            .when()
                .delete("/registry/v1/rules/VALIDITY")
            .then()
                .statusCode(204)
                .body(anything());

        // Get the default rule by name should now return the default configuration again
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v1/rules/VALIDITY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("VALIDITY"))
                    .body("config", equalTo("FULL"));
        });

        // Get the list of rules (should still be 2 of them)
        TestUtils.retry(() -> {
            given()
                .when()
                .get("/registry/v1/rules")
                .then()
                .log().all()
                .statusCode(200)
                .contentType(ContentType.JSON)
                    .body("[0]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY")))
                    .body("[1]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY")))
                    .body("[2]", nullValue());
        });

        // Delete all rules
        given()
            .when()
                .delete("/registry/v1/rules")
            .then()
                .statusCode(204);

        // Get the list of rules (just the default rule should exist now)
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v1/rules")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("size()", is(1))
                    .body("[0]", equalTo("VALIDITY"));
        });

        // Get the default rule by name should still return the default configuration
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v1/rules/VALIDITY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("VALIDITY"))
                    .body("config", equalTo("FULL"));
        });

        // Delete the global rule by name (should fail with a 409)
        given()
            .when()
                .delete("/registry/v1/rules/VALIDITY")
            .then()
                .statusCode(409)
                .contentType(ContentType.JSON)
                .body("error_code", equalTo(409))
                .body("message", equalTo("Default rule 'VALIDITY' cannot be deleted."));
    }
}
