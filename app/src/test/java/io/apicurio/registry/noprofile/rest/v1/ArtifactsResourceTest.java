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
import io.apicurio.registry.rest.v1.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v1.beans.IfExistsType;
import io.apicurio.registry.rest.v1.beans.Rule;
import io.apicurio.registry.rest.v1.beans.VersionMetaData;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.config.EncoderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToObject;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
public class ArtifactsResourceTest extends AbstractResourceTestBase {

    @Test
    public void testCreateArtifact() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact - indicate the type via a header param
        createArtifact("testCreateArtifact/EmptyAPI/1", ArtifactType.OPENAPI, artifactContent);

        // Create OpenAPI artifact - indicate the type via the content-type
        given()
            .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI/2")
                .body(artifactContent)
                .post("/registry/v1/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo("testCreateArtifact/EmptyAPI/2"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // Try to create a duplicate artifact ID (should fail)
        given()
            .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI/1")
                .body(artifactContent)
                .post("/registry/v1/artifacts")
            .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", equalTo("An artifact with ID 'testCreateArtifact/EmptyAPI/1' in group 'null' already exists."));

        // Try to create an artifact with an invalid artifact type
        given()
            .when()
                .contentType(CT_JSON + "; artifactType=INVALID_ARTIFACT_TYPE")
                .header("X-Registry-ArtifactId", "testCreateArtifact/InvalidAPI")
                .body(artifactContent)
                .post("/registry/v1/artifacts")
            .then()
                .statusCode(400);

        // Create OpenAPI artifact - don't provide the artifact type
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI/detect")
                .body(artifactContent)
                .post("/registry/v1/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo("testCreateArtifact/EmptyAPI/detect"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // Create artifact with empty content (should fail)
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyContent")
                .body("")
                .post("/registry/v1/artifacts")
            .then()
                .statusCode(400);
    }

    @Test
    public void testCreateArtifactInvalidSyntax() throws Exception {
        String invalidArtifactContent = resourceToString("openapi-invalid-syntax.json");

        // Add a global rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        given()
                .when()
                .contentType(CT_JSON).body(rule)
                .post("/registry/v1/rules")
                .then()
                .statusCode(204)
                .body(anything());

        // Verify the rule was added.
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

        // Create OpenAPI artifact - invalid syntax
        given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI/invalidSyntax")
                .body(invalidArtifactContent)
                .post("/registry/v1/artifacts")
                .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", equalTo("Syntax violation for OpenAPI artifact."));
    }

    @Test
    public void testGetArtifact() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact
        createArtifact("testGetArtifact/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Get the artifact content
        given()
            .when()
                .pathParam("artifactId", "testGetArtifact/EmptyAPI")
                .get("/registry/v1/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));

        // Try to get artifact content for an artifact that doesn't exist.
        given()
            .when()
                .pathParam("artifactId", "testGetArtifact/MissingAPI")
                .get("/registry/v1/artifacts/{artifactId}")
            .then()
                .statusCode(404)
                .body("error_code", equalTo(404))
                .body("message", equalTo("No artifact with ID 'testGetArtifact/MissingAPI' in group 'null' was found."));
    }

    @Test
    public void testUpdateArtifact() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");

        // Create OpenAPI artifact
        createArtifact("testUpdateArtifact/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Update OpenAPI artifact
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI")
                .body(updatedArtifactContent)
                .put("/registry/v1/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .body("id", equalTo("testUpdateArtifact/EmptyAPI"))
                .body("type", equalTo("OPENAPI"));

        // Get the artifact content (should be the updated content)
        given()
            .when()
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI")
                .get("/registry/v1/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API (Updated)"));

        // Try to update an artifact that doesn't exist.
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("artifactId", "testUpdateArtifact/MissingAPI")
                .body(updatedArtifactContent)
                .put("/registry/v1/artifacts/{artifactId}")
            .then()
                .statusCode(404);

        // Try to update an artifact with empty content
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI")
                .body("")
                .put("/registry/v1/artifacts/{artifactId}")
            .then()
                .statusCode(400);
    }

    @Test
    public void testDeleteArtifact() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact
        createArtifact("testDeleteArtifact/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Make sure we can get the artifact content
        given()
            .when()
                .pathParam("artifactId", "testDeleteArtifact/EmptyAPI")
                .get("/registry/v1/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));

        // Delete the artifact
        given()
            .when()
                .pathParam("artifactId", "testDeleteArtifact/EmptyAPI")
                .delete("/registry/v1/artifacts/{artifactId}")
            .then()
                .statusCode(204);

        // Try to get artifact content for an artifact that doesn't exist.
        TestUtils.retry(() -> {
            given()
                .when()
                    .pathParam("artifactId", "testDeleteArtifact/EmptyAPI")
                    .get("/registry/v1/artifacts/{artifactId}")
                .then()
                    .statusCode(404)
                    .body("error_code", equalTo(404))
                    .body("message", equalTo("No artifact with ID 'testDeleteArtifact/EmptyAPI' in group 'null' was found."));
        });

        // Try to delete an artifact that doesn't exist.
        given()
            .when()
                .pathParam("artifactId", "testDeleteArtifact/MissingAPI")
                .delete("/registry/v1/artifacts/{artifactId}")
            .then()
                .statusCode(404);
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testListArtifactVersions() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create an artifact
        createArtifact("testListArtifactVersions/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Update the artifact 5 times
        for (int idx = 0; idx < 5; idx++) {
            given()
                .when()
                    .contentType(CT_JSON)
                    .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                    .pathParam("artifactId", "testListArtifactVersions/EmptyAPI")
                    .body(artifactContent.replace("Empty API", "Empty API (Update " + idx + ")"))
                    .put("/registry/v1/artifacts/{artifactId}")
                .then()
                    .statusCode(200)
                    .body("id", equalTo("testListArtifactVersions/EmptyAPI"))
                    .body("type", equalTo("OPENAPI"));
        }

        // List the artifact versions
        given()
            .when()
                .pathParam("artifactId", "testListArtifactVersions/EmptyAPI")
                .get("/registry/v1/artifacts/{artifactId}/versions")
            .then()
//                .log().all()
                .statusCode(200)
                // The following custom matcher makes sure that 6 versions are returned
                .body(new CustomMatcher("Unexpected list of artifact versions.") {
                    @Override
                    public boolean matches(Object item) {
                        String val = item.toString();
                        if (val == null) {
                            return false;
                        }
                        if (!val.startsWith("[") || !val.endsWith("]")) {
                            return false;
                        }
                        return val.split(",").length == 6;
                    }
                });

        // Try to list artifact versions for an artifact that doesn't exist.
        given()
            .when()
                .pathParam("artifactId", "testListArtifactVersions/MissingAPI")
                .get("/registry/v1/artifacts/{artifactId}/versions")
            .then()
                .statusCode(404);

    }

    @Test
    public void testCreateArtifactVersion() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");

        // Create OpenAPI artifact
        createArtifact("testCreateArtifactVersion/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Create a new version of the artifact
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", "OPENAPI")
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI")
                .body(updatedArtifactContent)
                .post("/registry/v1/artifacts/{artifactId}/versions")
            .then()
                .statusCode(200)
                .body("version", equalTo(2))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // Get the artifact content (should be the updated content)
        given()
            .when()
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI")
                .get("/registry/v1/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API (Updated)"));

        // Try to create a new version of an artifact that doesn't exist.
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("artifactId", "testCreateArtifactVersion/MissingAPI")
                .body(updatedArtifactContent)
                .post("/registry/v1/artifacts/{artifactId}/versions")
            .then()
                .statusCode(404);

        // Try to create a new version of the artifact with empty content
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI")
                .body("")
                .post("/registry/v1/artifacts/{artifactId}/versions")
            .then()
                .statusCode(400);

    }

    @Test
    public void testCreateArtifactVersionValidityRuleViolation() throws Exception {
        String artifactContent = resourceToString("jsonschema-valid.json");
        String artifactContentInvalidSyntax = resourceToString("jsonschema-invalid.json");
        String artifactId = "testCreateArtifact/ValidityRuleViolation";
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

        // Create a new version of the artifact with invalid syntax
        given()
                .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.JSON)
                .pathParam("artifactId", artifactId)
                .body(artifactContentInvalidSyntax)
                .post("/registry/v1/artifacts/{artifactId}/versions")
                .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", equalTo("Syntax or semantic violation for JSON Schema artifact."));
    }

    @Test
    public void testCreateArtifactVersionCompatibilityRuleViolation() throws Exception {
        String artifactContent = resourceToString("jsonschema-valid.json");
        String artifactContentInvalidSyntax = resourceToString("jsonschema-valid-incompatible.json");
        String artifactId = "testCreateArtifact/ValidJson";
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

        // Create a new version of the artifact with invalid syntax
        given()
                .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.JSON)
                .pathParam("artifactId", artifactId)
                .body(artifactContentInvalidSyntax)
                .post("/registry/v1/artifacts/{artifactId}/versions")
                .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", equalTo("Incompatible artifact: testCreateArtifact/ValidJson [JSON], num " +
                        "of incompatible diffs: {1}, list of diff types: [SUBSCHEMA_TYPE_CHANGED at /properties/age]"))
                .body("causes[0].description", equalTo(DiffType.SUBSCHEMA_TYPE_CHANGED.getDescription()))
                .body("causes[0].context", equalTo("/properties/age"));

    }

    @Test
    public void testGetArtifactVersion() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create an artifact
        createArtifact("testGetArtifactVersion/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Update the artifact 5 times
        List<Integer> versions = new ArrayList<>();
        for (int idx = 0; idx < 5; idx++) {
            Integer version = given()
                .when()
                    .contentType(CT_JSON)
                    .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                    .pathParam("artifactId", "testGetArtifactVersion/EmptyAPI")
                    .body(artifactContent.replace("Empty API", "Empty API (Update " + idx + ")"))
                    .put("/registry/v1/artifacts/{artifactId}")
                .then()
                    .statusCode(200)
                    .body("id", equalTo("testGetArtifactVersion/EmptyAPI"))
                    .body("type", equalTo(ArtifactType.OPENAPI))
                .extract().body().path("version");
            versions.add(version);
        }

        // Now get each version of the artifact
        for (int idx = 0; idx < 5; idx++) {
            Integer version = versions.get(idx);
            String expected = "Empty API (Update " + idx + ")";
            given()
                .when()
                    .pathParam("artifactId", "testGetArtifactVersion/EmptyAPI")
                    .pathParam("version", version)
                    .get("/registry/v1/artifacts/{artifactId}/versions/{version}")
                .then()
                    .statusCode(200)
                    .body("info.title", equalTo(expected));
        }

        // Now get a version that doesn't exist.
        given()
            .when()
                .pathParam("artifactId", "testGetArtifactVersion/EmptyAPI")
                .pathParam("version", 12345)
                .get("/registry/v1/artifacts/{artifactId}/versions/{version}")
            .then()
                .statusCode(404);

        // Now get a version of an artifact that doesn't exist.
        given()
            .when()
                .pathParam("artifactId", "testGetArtifactVersion/MissingAPI")
                .pathParam("version", 1)
                .get("/registry/v1/artifacts/{artifactId}/versions/{version}")
            .then()
                .statusCode(404);
    }

    @Test
    public void testGetArtifactMetaDataByContent() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create an artifact
        createArtifact("testGetArtifactMetaDataByContent/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Update the artifact 5 times
        List<Integer> versions = new ArrayList<>();
        for (int idx = 0; idx < 5; idx++) {
            Integer version = given()
                .when()
                    .contentType(CT_JSON)
                    .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                    .pathParam("artifactId", "testGetArtifactMetaDataByContent/EmptyAPI")
                    .body(artifactContent.replace("Empty API", "Empty API (Update " + idx + ")"))
                    .put("/registry/v1/artifacts/{artifactId}")
                .then()
                    .statusCode(200)
                    .body("id", equalTo("testGetArtifactMetaDataByContent/EmptyAPI"))
                    .body("type", equalTo(ArtifactType.OPENAPI))
                .extract().body().path("version");
            versions.add(version);
        }

        // Get meta-data by content
        String searchContent = artifactContent.replace("Empty API", "Empty API (Update 2)");
        Integer globalId1 = given()
            .when()
                .contentType(CT_JSON)
                .pathParam("artifactId", "testGetArtifactMetaDataByContent/EmptyAPI")
                .body(searchContent)
                .post("/registry/v1/artifacts/{artifactId}/meta")
            .then()
                .statusCode(200)
                .body("type", equalTo(ArtifactType.OPENAPI))
            .extract().body().path("globalId");

        // Now add some extra whitespace/formatting to the content and try again
        searchContent = searchContent.replace("{", "{\n").replace("}", "\n}");
        Integer globalId2 = given()
            .when()
                .contentType(CT_JSON)
                .pathParam("artifactId", "testGetArtifactMetaDataByContent/EmptyAPI")
                .queryParam("canonical", "true")
                .body(searchContent)
                .post("/registry/v1/artifacts/{artifactId}/meta")
            .then()
                .statusCode(200)
                .body("type", equalTo(ArtifactType.OPENAPI))
            .extract().body().path("globalId");

        // Should return the same meta-data
        Assertions.assertEquals(globalId1, globalId2);

        // Try the same (extra whitespace) content but without the "canonical=true" param (should fail with 404)
        searchContent = searchContent.replace("{", "{\n").replace("}", "\n}");
        given()
            .when()
                .contentType(CT_JSON)
                .pathParam("artifactId", "testGetArtifactMetaDataByContent/EmptyAPI")
                .body(searchContent)
                .post("/registry/v1/artifacts/{artifactId}/meta")
            .then()
                .statusCode(404);

        // Get meta-data by empty content (400 error)
        given()
            .when()
                .contentType(CT_JSON)
                .pathParam("artifactId", "testGetArtifactMetaDataByContent/EmptyAPI")
                .body("")
                .post("/registry/v1/artifacts/{artifactId}/meta")
            .then()
                .statusCode(400);

    }

    @Test
    public void testArtifactRules() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String artifactId = "testArtifactRules/EmptyAPI";

        // Create an artifact
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

        // Try to add the rule again - should get a 409
        final Rule finalRule = rule;
        TestUtils.retry(() -> {
            given()
                .when()
                    .contentType(CT_JSON)
                    .body(finalRule)
                    .pathParam("artifactId", artifactId)
                    .post("/registry/v1/artifacts/{artifactId}/rules")
                .then()
                    .statusCode(409)
                    .body("error_code", equalTo(409))
                    .body("message", equalTo("A rule named 'VALIDITY' already exists."));
        });

        // Add another rule
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

        // Get the list of rules (should be 2 of them)
        given()
            .when()
                .pathParam("artifactId", artifactId)
                .get("/registry/v1/artifacts/{artifactId}/rules")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("[0]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY")))
                .body("[1]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY")))
                .body("[2]", nullValue());

        // Update a rule's config
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("FULL");
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .pathParam("artifactId", artifactId)
                .put("/registry/v1/artifacts/{artifactId}/rules/COMPATIBILITY")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("type", equalTo("COMPATIBILITY"))
                .body("config", equalTo("FULL"));

        // Get a single (updated) rule by name
        TestUtils.retry(() -> {
            given()
                .when()
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v1/artifacts/{artifactId}/rules/COMPATIBILITY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("COMPATIBILITY"))
                    .body("config", equalTo("FULL"));
        });

        // Try to update a rule's config for a rule that doesn't exist.
        // TODO test for a rule that doesn't exist
//        rule.setType("RuleDoesNotExist");
//        rule.setConfig("rdne-config");
//        given()
//            .when()
//                .contentType(CT_JSON)
//                .body(rule)
//                .pathParam("artifactId", artifactId)
//                .put("/registry/v1/artifacts/{artifactId}/rules/RuleDoesNotExist")
//            .then()
//                .statusCode(404)
//                .contentType(ContentType.JSON)
//                .body("error_code", equalTo(404))
//                .body("message", equalTo("No rule named 'RuleDoesNotExist' was found."));

        // Delete a rule
        given()
            .when()
                .pathParam("artifactId", artifactId)
                .delete("/registry/v1/artifacts/{artifactId}/rules/COMPATIBILITY")
            .then()
                .statusCode(204)
                .body(anything());

        // Get a single (deleted) rule by name (should fail with a 404)
        TestUtils.retry(() -> {
            given()
                .when()
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v1/artifacts/{artifactId}/rules/COMPATIBILITY")
                .then()
                    .statusCode(404)
                    .contentType(ContentType.JSON)
                    .body("error_code", equalTo(404))
                    .body("message", equalTo("No rule named 'COMPATIBILITY' was found."));
        });

        // Get the list of rules (should be 1 of them)
        given()
            .when()
                .pathParam("artifactId", artifactId)
                .get("/registry/v1/artifacts/{artifactId}/rules")
            .then()
//                .log().all()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("[0]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY")))
                .body("[1]", nullValue());

        // Delete all rules
        given()
            .when()
                .pathParam("artifactId", artifactId)
                .delete("/registry/v1/artifacts/{artifactId}/rules")
            .then()
                .statusCode(204);

        // Get the list of rules (no rules now)
        TestUtils.retry(() -> {
            given()
                .when()
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v1/artifacts/{artifactId}/rules")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("[0]", nullValue());
        });

        // Add a rule to an artifact that doesn't exist.
        rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .pathParam("artifactId", "MissingArtifact")
                .post("/registry/v1/artifacts/{artifactId}/rules")
            .then()
                .statusCode(404)
                .body(anything());
    }

    @Test
    public void testArtifactMetaData() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact
        createArtifact("testGetArtifactMetaData/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Get the artifact meta-data
        given()
            .when()
                .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .get("/registry/v1/artifacts/{artifactId}/meta")
            .then()
                .statusCode(200)
                .body("id", equalTo("testGetArtifactMetaData/EmptyAPI"))
                .body("version", anything())
                .body("type", equalTo(ArtifactType.OPENAPI))
                .body("createdOn", anything())
                .body("name", equalTo("Empty API"))
                .body("description", equalTo("An example API design using OpenAPI."))
        .extract()
            .as(ArtifactMetaData.class);

        // Try to get artifact meta-data for an artifact that doesn't exist.
        given()
            .when()
                .pathParam("artifactId", "testGetArtifactMetaData/MissingAPI")
                .get("/registry/v1/artifacts/{artifactId}/meta")
            .then()
                .statusCode(404)
                .body("error_code", equalTo(404))
                .body("message", equalTo("No artifact with ID 'testGetArtifactMetaData/MissingAPI' in group 'null' was found."));

        // Update the artifact meta-data
        String metaData = "{\"name\": \"Empty API Name\", \"description\": \"Empty API description.\", \"labels\":[\"Empty API label 1\",\"Empty API label 2\"], \"properties\":{\"additionalProp1\": \"Empty API additional property\"}}";
        given()
            .when()
                .contentType(CT_JSON)
                .body(metaData)
                .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .put("/registry/v1/artifacts/{artifactId}/meta")
            .then()
                .statusCode(204);


        // Get the (updated) artifact meta-data
        TestUtils.retry(() -> {
            List<String> expectedLabels = Arrays.asList("Empty API label 1", "Empty API label 2");
            Map<String, String> expectedProperties = new HashMap<>();
            expectedProperties.put("additionalProp1", "Empty API additional property");

            int version = given()
                .when()
                    .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                    .get("/registry/v1/artifacts/{artifactId}/meta")
                .then()
                    .statusCode(200)
                    .body("id", equalTo("testGetArtifactMetaData/EmptyAPI"))
                    .body("version", anything())
                    .body("name", equalTo("Empty API Name"))
                    .body("description", equalTo("Empty API description."))
                    .body("labels", equalToObject(expectedLabels))
                    .body("properties", equalToObject(expectedProperties))
                .extract().body().path("version");

            // Make sure the version specific meta-data also returns all the custom meta-data
            given()
                .when()
                    .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                    .pathParam("version", version)
                    .get("/registry/v1/artifacts/{artifactId}/versions/{version}/meta")
                .then()
                    .statusCode(200)
                    .body("name", equalTo("Empty API Name"))
                    .body("description", equalTo("Empty API description."))
                    .body("labels", equalToObject(expectedLabels))
                    .body("properties", equalToObject(expectedProperties))
                .extract().body().path("version");
        });

        // Update the artifact content and then make sure the name/description meta-data is still available
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .body(updatedArtifactContent)
                .put("/registry/v1/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .body("id", equalTo("testGetArtifactMetaData/EmptyAPI"))
                .body("type", equalTo("OPENAPI"));

        // Verify the artifact meta-data name and description are still set.
        given()
            .when()
                .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .get("/registry/v1/artifacts/{artifactId}/meta")
            .then()
                .statusCode(200)
                .body("id", equalTo("testGetArtifactMetaData/EmptyAPI"))
                .body("version", anything())
                .body("name", equalTo("Empty API (Updated)"))
                .body("description", equalTo("An example API design using OpenAPI."));
    }

    @Test
    public void testArtifactVersionMetaData() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent_v2 = artifactContent.replace("Empty API", "Empty API (v2)");
        String updatedArtifactContent_v3 = artifactContent.replace("Empty API", "Empty API (v3)");

        // Create OpenAPI artifact
        createArtifact("testArtifactVersionMetaData/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Create a new version of the artifact
        int version2 = given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .body(updatedArtifactContent_v2)
                .post("/registry/v1/artifacts/{artifactId}/versions")
            .then()
                .statusCode(200)
                .body("version", notNullValue())
                .body("type", equalTo(ArtifactType.OPENAPI))
            .extract().body().path("version");

        // Create another new version of the artifact
        int version3 = given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .body(updatedArtifactContent_v3)
                .post("/registry/v1/artifacts/{artifactId}/versions")
            .then()
                .statusCode(200)
                .body("version", notNullValue())
                .body("type", equalTo(ArtifactType.OPENAPI))
            .extract().body().path("version");

        // Get meta-data for v2
        given()
            .when()
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .pathParam("version", version2)
                .get("/registry/v1/artifacts/{artifactId}/versions/{version}/meta")
            .then()
                .statusCode(200)
                .body("version", equalTo(version2))
                .body("type", equalTo(ArtifactType.OPENAPI))
                .body("createdOn", anything())
                .body("name", equalTo("Empty API (v2)"))
                .body("description", equalTo("An example API design using OpenAPI."))
        .extract()
            .as(VersionMetaData.class);

        // Update the version meta-data
        String metaData = "{\"name\": \"Updated Name\", \"description\": \"Updated description.\"}";
        given()
            .when()
                .contentType(CT_JSON)
                .body(metaData)
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .pathParam("version", version2)
                .put("/registry/v1/artifacts/{artifactId}/versions/{version}/meta")
            .then()
                .statusCode(204);

        // Get the (updated) artifact meta-data
        TestUtils.retry(() -> {
            given()
                .when()
                    .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                    .pathParam("version", version2)
                    .get("/registry/v1/artifacts/{artifactId}/versions/{version}/meta")
                .then()
                    .statusCode(200)
                    .body("version", equalTo(version2))
                    .body("type", equalTo(ArtifactType.OPENAPI))
                    .body("createdOn", anything())
                    .body("name", equalTo("Updated Name"))
                    .body("description", equalTo("Updated description."));
        });

        // Get the version meta-data for the version we **didn't** update
        given()
            .when()
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .pathParam("version", version3)
                .get("/registry/v1/artifacts/{artifactId}/versions/{version}/meta")
            .then()
                .statusCode(200)
                .body("version", equalTo(version3))
                .body("type", equalTo(ArtifactType.OPENAPI))
                .body("createdOn", anything())
                .body("name", equalTo("Empty API (v3)"))
                .body("description", equalTo("An example API design using OpenAPI."));

        // Get the version meta-data for a non-existant version
        given()
            .when()
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .pathParam("version", 12345)
                .get("/registry/v1/artifacts/{artifactId}/versions/{version}/meta")
            .then()
                .statusCode(404);

    }

    @Test
    public void testYamlContentType() throws Exception {
        String artifactId = "testYamlContentType";
        String artifactType = ArtifactType.OPENAPI;
        String artifactContent = resourceToString("openapi-empty.yaml");

        // Create OpenAPI artifact (from YAML)
        given()
            .config(RestAssuredConfig.config().encoderConfig(EncoderConfig.encoderConfig().encodeContentTypeAs(CT_YAML, ContentType.TEXT)))
            .when()
                .contentType(CT_YAML)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType)
                .body(artifactContent)
                .post("/registry/v1/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("name", equalTo("Empty API"))
                .body("description", equalTo("An example API design using OpenAPI."))
                .body("type", equalTo(artifactType));

        this.waitForArtifact(artifactId);

        // Get the artifact content (should be JSON)
        given()
            .when()
                .pathParam("artifactId", "testYamlContentType")
                .get("/registry/v1/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .header("Content-Type", Matchers.containsString(CT_JSON))
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));
    }


    @Test
    public void testWsdlArtifact() throws Exception {
        String artifactId = "testWsdlArtifact";
        String artifactType = ArtifactType.WSDL;
        String artifactContent = resourceToString("sample.wsdl");

        // Create OpenAPI artifact (from YAML)
        given()
            .config(RestAssuredConfig.config().encoderConfig(EncoderConfig.encoderConfig().encodeContentTypeAs(CT_XML, ContentType.TEXT)))
            .when()
                .contentType(CT_XML)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType)
                .body(artifactContent)
                .post("/registry/v1/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("type", equalTo(artifactType));

        this.waitForArtifact(artifactId);

        // Get the artifact content (should be XML)
        given()
            .when()
                .pathParam("artifactId", "testWsdlArtifact")
                .get("/registry/v1/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .header("Content-Type", Matchers.containsString(CT_XML));
    }

    @Test
    public void testCreateAlreadyExistingArtifact() throws Exception {

        final String artifactId = UUID.randomUUID().toString();
        final String artifactContent = resourceToString("openapi-empty.json");
        final String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");
        final String v3ArtifactContent = artifactContent.replace("Empty API", "Empty API (Version 3)");

        // Create OpenAPI artifact - indicate the type via a header param
        Integer globalId1 = createArtifact(artifactId, ArtifactType.OPENAPI, artifactContent);

        // Try to create the same artifact ID (should fail)
        given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", artifactId)
                .body(artifactContent)
                .post("/registry/v1/artifacts")
                .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", equalTo("An artifact with ID '" + artifactId + "' in group 'null' already exists."));

        // Try to create the same artifact ID with Return for if exists (should return same artifact)
        given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", artifactId)
                .queryParam("ifExists", IfExistsType.RETURN)
                .body(artifactContent)
                .post("/registry/v1/artifacts")
                .then()
                .statusCode(200)
                .body("type", equalTo(ArtifactType.OPENAPI))
                .body("version", equalTo(1))
                .body("createdOn", anything())
                .body("name", equalTo("Empty API"))
                .body("description", equalTo("An example API design using OpenAPI."));

        // Try to create the same artifact ID with Update for if exists (should update the artifact)
        ValidatableResponse resp = given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", artifactId)
                .queryParam("ifExists", IfExistsType.UPDATE)
                .body(updatedArtifactContent)
                .post("/registry/v1/artifacts")
                .then()
                .statusCode(200)
                .body("type", equalTo(ArtifactType.OPENAPI))
                .body("createdOn", anything())
                .body("version", equalTo(2))
                .body("description", equalTo("An example API design using OpenAPI."));
        Integer globalId2 = resp.extract().body().path("globalId");

        this.waitForGlobalId(globalId2);

        // Try to create the same artifact ID with ReturnOrUpdate - should return v1 (matching content)
        resp = given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", artifactId)
                .queryParam("ifExists", IfExistsType.RETURN_OR_UPDATE)
                .body(artifactContent)
                .post("/registry/v1/artifacts")
                .then()
                .statusCode(200)
                .body("type", equalTo(ArtifactType.OPENAPI));

        Integer globalId3 = resp.extract().body().path("globalId");

        Assertions.assertEquals(globalId1, globalId3);

        // Try to create the same artifact ID with ReturnOrUpdate and updated content - should create a new version
        resp = given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", artifactId)
                .queryParam("ifExists", IfExistsType.RETURN_OR_UPDATE)
                .body(v3ArtifactContent)
                .post("/registry/v1/artifacts")
                .then()
                .statusCode(200)
                .body("version", equalTo(3))
                .body("type", equalTo(ArtifactType.OPENAPI));
    }

    @Test
    public void testDeleteArtifactWithRule() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String artifactId = "testDeleteArtifactWithRule/EmptyAPI";

        // Create an artifact
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

        // Get a single rule by name
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

        // Delete the artifact
        given()
            .when()
                .pathParam("artifactId", artifactId)
                .delete("/registry/v1/artifacts/{artifactId}")
            .then()
                .statusCode(204);

        // Get a single rule by name (should be 404 because the artifact is gone)
        // Also try to get the artifact itself (should be 404)
        TestUtils.retry(() -> {
            given()
                .when()
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v1/artifacts/{artifactId}/rules/VALIDITY")
                .then()
                    .statusCode(404);
            given()
                .when()
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v1/artifacts/{artifactId}")
                .then()
                    .statusCode(404);
        });

        // Re-create the artifact
        createArtifact(artifactId, ArtifactType.OPENAPI, artifactContent);

        // Get a single rule by name (should be 404 because the artifact is gone)
        given()
            .when()
                .pathParam("artifactId", artifactId)
                .get("/registry/v1/artifacts/{artifactId}/rules/VALIDITY")
            .then()
                .statusCode(404);

        // Add the same rule - should work because the old rule was deleted when the artifact was deleted.
        rule = new Rule();
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
    }

}
