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
import io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.equalTo;

/**
 * Created by aohana
 */
@QuarkusTest
public class TestResourceTest extends AbstractResourceTestBase {

    @Test
    public void testTestArtifactNoViolations() throws Exception {
        String artifactContent = resourceToString("jsonschema-valid.json");
        String artifactContentValid = resourceToString("jsonschema-valid-compatible.json");
        String artifactId = "testCreateArtifact/TestNoViolation";
        createArtifact(artifactId, ArtifactType.JSON, artifactContent);

        // Add a rule
        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .pathParam("artifactId", artifactId)
                .post("/registry/v1/artifacts/{artifactId}/rules")
            .then()
                .statusCode(204)
                .body(anything());

        // Verify the rule was added
        TestUtils.retry(() -> {
            given()
                .when()
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v1/artifacts/{artifactId}/rules/COMPATIBILITY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("COMPATIBILITY"))
                    .body("config", equalTo("BACKWARD"));
        });

        // Test a new version with valid change
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.JSON)
                .pathParam("artifactId", artifactId)
                .body(artifactContentValid)
                .put("/registry/v1/artifacts/{artifactId}/test")
            .then()
                .statusCode(204);
    }

    @Test
    public void testTestArtifactCompatibilityViolation() throws Exception {
        String artifactContent = resourceToString("jsonschema-valid.json");
        String artifactContentIncompatible = resourceToString("jsonschema-valid-incompatible.json");
        String artifactId = "testTestArtifactCompatibilityViolation";
        createArtifact(artifactId, ArtifactType.JSON, artifactContent);

        // Add a rule
        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .pathParam("artifactId", artifactId)
                .post("/registry/v1/artifacts/{artifactId}/rules")
            .then()
                .statusCode(204)
                .body(anything());

        // Verify the rule was added
        TestUtils.retry(() -> {
            given()
                .when()
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v1/artifacts/{artifactId}/rules/COMPATIBILITY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("COMPATIBILITY"))
                    .body("config", equalTo("BACKWARD"));
        });

        // Test a new version with incompatible content
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.JSON)
                .pathParam("artifactId", artifactId)
                .body(artifactContentIncompatible)
                .put("/registry/v1/artifacts/{artifactId}/test")
            .then()
            .log().all()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", equalTo("Incompatible artifact: testTestArtifactCompatibilityViolation " +
                        "[JSON], num of incompatible diffs: {1}, list of diff types: [SUBSCHEMA_TYPE_CHANGED at /properties/age]"))
                .body("causes[0].description", equalTo(DiffType.SUBSCHEMA_TYPE_CHANGED.getDescription()))
                .body("causes[0].context", equalTo("/properties/age"));
    }

    @Test
    public void testTestArtifactValidityViolation() throws Exception {
        String artifactContent = resourceToString("jsonschema-valid.json");
        String artifactContentInvalidSyntax = resourceToString("jsonschema-invalid.json");
        String artifactId = "testTestArtifactValidityViolation";
        createArtifact(artifactId, ArtifactType.JSON, artifactContent);

        // Add a rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .pathParam("artifactId", artifactId)
                .post("/registry/v1/artifacts/{artifactId}/rules")
            .then()
                .statusCode(204)
                .body(anything());

        // Verify the rule was added
        TestUtils.retry(() -> {
            given()
                .when()
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v1/artifacts/{artifactId}/rules/VALIDITY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("VALIDITY"))
                    .body("config", equalTo("FULL"));
        });

        // Test a new version of the artifact with invalid syntax
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.JSON)
                .pathParam("artifactId", artifactId)
                .body(artifactContentInvalidSyntax)
                .put("/registry/v1/artifacts/{artifactId}/test")
            .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", equalTo("Syntax or semantic violation for JSON Schema artifact."));
    }

    @Test
    public void testOpenApiValidityViolation() throws Exception {
        String artifactContent = resourceToString("openapi-valid-syntax.json");
        String artifactContentInvalid = resourceToString("openapi-invalid-singleerror.json");
        String artifactId = "testOpenApiValidityViolation";
        createArtifact(artifactId, ArtifactType.OPENAPI, artifactContent);

        // Add a rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .pathParam("artifactId", artifactId)
                .post("/registry/v1/artifacts/{artifactId}/rules")
            .then()
                .statusCode(204)
                .body(anything());

        // Verify the rule was added
        TestUtils.retry(() -> {
            given()
                .when()
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v1/artifacts/{artifactId}/rules/VALIDITY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("VALIDITY"))
                    .body("config", equalTo("FULL"));
        });

        // Test a new version of the artifact with invalid semantics
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("artifactId", artifactId)
                .body(artifactContentInvalid)
                .put("/registry/v1/artifacts/{artifactId}/test")
            .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", equalTo("The OpenAPI artifact is not semantically valid. 1 problems found."))
                .body("causes[0].description", equalTo("Schema Reference must refer to a valid Schema Definition."))
                .body("causes[0].context", equalTo("/paths[/widgets]/get/responses[200]/content[application/json]/schema/items"));
    }
}
