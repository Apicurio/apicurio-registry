/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.noprofile.rest.v1;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v1.beans.Rule;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests registry via its jax-rs interface.  This test performs more realistic
 * usage scenarios than the more unit-test focused {@link AdminResourceTest}
 * and {@link GroupsResourceTest}.
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
public class FullApiTest extends AbstractResourceTestBase {

    @Test
    public void testGlobalRuleApplicationOpenAPI() throws Exception {
        String artifactType = ArtifactType.OPENAPI;
        String artifactContent = resourceToString("openapi-invalid-syntax.json");

        // First, create an artifact without the rule installed.  Should work.
        createArtifact("testGlobalRuleApplicationOpenAPI/API", artifactType, artifactContent);

        // Add a global rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig(ValidityLevel.SYNTAX_ONLY.name());
        given()
            .when()
                .contentType(CT_JSON).body(rule)
                .post("/registry/v1/rules")
            .then()
                .statusCode(204)
                .body(anything());

        // Get the global rule (make sure it was created)
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


        // Try to create an artifact that is not valid - now it should fail.
        String artifactId = "testGlobalRuleApplicationOpenAPI/InvalidAPI";
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType)
                .body(artifactContent)
                .post("/registry/v1/artifacts")
            .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", equalTo("Syntax violation for OpenAPI artifact."));

    }

    @Test
    public void testGlobalRuleApplicationProtobuf() throws Exception {
        String artifactType = ArtifactType.PROTOBUF;
        String artifactContent = resourceToString("protobuf-invalid-syntax.proto");

        // First, create an artifact without the rule installed.  Should work.
        createArtifact("testGlobalRuleApplicationProtobuf/API", artifactType, artifactContent);

        // Add a global rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig(ValidityLevel.SYNTAX_ONLY.name());
        given()
            .when()
                .contentType(CT_JSON).body(rule)
                .post("/registry/v1/rules")
            .then()
                .statusCode(204)
                .body(anything());

        // Get the global rule (make sure it was created)
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

        // Try to create an artifact that is not valid - now it should fail.
        String artifactId = "testGlobalRuleApplicationProtobuf/InvalidAPI";
        given()
            .config(RestAssured.config().encoderConfig(new EncoderConfig().encodeContentTypeAs(CT_PROTO, ContentType.TEXT)))
            .when()
                .contentType(CT_PROTO)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType)
                .body(artifactContent)
                .post("/registry/v1/artifacts")
            .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", equalTo("Syntax violation for Protobuf artifact."));

    }

    @Test
    public void testV1CompatibilityPath() throws Exception {
        // Add a global rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        given()
            .baseUri("http://localhost:" + testPort + "/api")
            .when()
                .contentType(CT_JSON).body(rule)
                .post("/rules")
            .then()
                .statusCode(204)
                .body(anything());

        // Verify the rule was added.
        TestUtils.retry(() -> {
            given()
                    .baseUri("http://localhost:" + testPort + "/api")
                .when()
                    .get("/rules/VALIDITY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("VALIDITY"))
                    .body("config", equalTo("FULL"));
        });

        // Delete all rules
        given()
                .baseUri("http://localhost:" + testPort + "/api")
            .when()
                .delete("/rules")
            .then()
                .statusCode(204);

        // Get the list of rules (no rules now)
        TestUtils.retry(() -> {
            given()
                    .baseUri("http://localhost:" + testPort + "/api")
                .when()
                    .get("/rules")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("[0]", nullValue());
        });

        // Get the other (deleted) rule by name (should fail with a 404)
        given()
                .baseUri("http://localhost:" + testPort + "/api")
            .when()
                .get("/rules/VALIDITY")
            .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error_code", equalTo(404))
                .body("message", equalTo("No rule named 'VALIDITY' was found."));


    }

}
