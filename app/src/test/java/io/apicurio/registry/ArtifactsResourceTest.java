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
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.apicurio.registry.rest.beans.IfExistsType;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.config.EncoderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;

/**
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
public class ArtifactsResourceTest extends AbstractResourceTestBase {

    @Test
    public void testCreateArtifact() {
        String artifactContent = resourceToString("openapi-empty.json");
        
        // Create OpenAPI artifact - indicate the type via a header param
        createArtifact("testCreateArtifact/EmptyAPI/1", ArtifactType.OPENAPI, artifactContent);

        // Create OpenAPI artifact - indicate the type via the content-type
        given()
            .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI/2")
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo("testCreateArtifact/EmptyAPI/2"))
                .body("type", equalTo(ArtifactType.OPENAPI.name()));

        // Try to create the same artifact ID (should fail)
        given()
            .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI/2")
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", equalTo("An artifact with ID 'testCreateArtifact/EmptyAPI/2' already exists."));

        // Try to create an artifact with an invalid artifact type
        given()
            .when()
                .contentType(CT_JSON + "; artifactType=INVALID_ARTIFACT_TYPE")
                .header("X-Registry-ArtifactId", "testCreateArtifact/InvalidAPI")
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(400);

        // Create OpenAPI artifact - don't provide the artifact type
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI/detect")
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo("testCreateArtifact/EmptyAPI/detect"))
                .body("type", equalTo(ArtifactType.OPENAPI.name()));
    }

    @Test
    public void testGetArtifact() {
        String artifactContent = resourceToString("openapi-empty.json");
        
        // Create OpenAPI artifact
        createArtifact("testGetArtifact/EmptyAPI", ArtifactType.OPENAPI, artifactContent);
        
        // Get the artifact content
        given()
            .when()
                .pathParam("artifactId", "testGetArtifact/EmptyAPI")
                .get("/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));
        
        // Try to get artifact content for an artifact that doesn't exist.
        given()
            .when()
                .pathParam("artifactId", "testGetArtifact/MissingAPI")
                .get("/artifacts/{artifactId}")
            .then()
                .statusCode(404)
                .body("error_code", equalTo(404))
                .body("message", equalTo("No artifact with ID 'testGetArtifact/MissingAPI' was found."));
    }

    @Test
    public void testUpdateArtifact() {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");
        
        // Create OpenAPI artifact
        createArtifact("testUpdateArtifact/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Update OpenAPI artifact
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI.name())
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI")
                .body(updatedArtifactContent)
                .put("/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .body("id", equalTo("testUpdateArtifact/EmptyAPI"))
                .body("type", equalTo(ArtifactType.OPENAPI.name()));

        // Get the artifact content (should be the updated content)
        given()
            .when()
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI")
                .get("/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API (Updated)"));
        
        // Try to update an artifact that doesn't exist.
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI.name())
                .pathParam("artifactId", "testUpdateArtifact/MissingAPI")
                .body(updatedArtifactContent)
                .put("/artifacts/{artifactId}")
            .then()
                .statusCode(404);
    }
    
    @Test
    public void testDeleteArtifact() {
        String artifactContent = resourceToString("openapi-empty.json");
        
        // Create OpenAPI artifact
        createArtifact("testDeleteArtifact/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Make sure we can get the artifact content
        given()
            .when()
                .pathParam("artifactId", "testDeleteArtifact/EmptyAPI")
                .get("/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));
        
        // Delete the artifact
        given()
            .when()
                .pathParam("artifactId", "testDeleteArtifact/EmptyAPI")
                .delete("/artifacts/{artifactId}")
            .then()
                .statusCode(204);
        
        // Try to get artifact content for an artifact that doesn't exist.
        given()
            .when()
                .pathParam("artifactId", "testDeleteArtifact/EmptyAPI")
                .get("/artifacts/{artifactId}")
            .then()
                .statusCode(404)
                .body("error_code", equalTo(404))
                .body("message", equalTo("No artifact with ID 'testDeleteArtifact/EmptyAPI' was found."));
    
        // Try to delete an artifact that doesn't exist.
        given()
            .when()
                .pathParam("artifactId", "testDeleteArtifact/MissingAPI")
                .delete("/artifacts/{artifactId}")
            .then()
                .statusCode(404);
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testListArtifactVersions() {
        String artifactContent = resourceToString("openapi-empty.json");
        
        // Create an artifact
        createArtifact("testListArtifactVersions/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Update the artifact 5 times
        for (int idx = 0; idx < 5; idx++) {
            given()
                .when()
                    .contentType(CT_JSON)
                    .header("X-Registry-ArtifactType", ArtifactType.OPENAPI.name())
                    .pathParam("artifactId", "testListArtifactVersions/EmptyAPI")
                    .body(artifactContent.replace("Empty API", "Empty API (Update " + idx + ")"))
                    .put("/artifacts/{artifactId}")
                .then()
                    .statusCode(200)
                    .body("id", equalTo("testListArtifactVersions/EmptyAPI"))
                    .body("type", equalTo(ArtifactType.OPENAPI.name()));
        }
        
        // List the artifact versions
        given()
            .when()
                .pathParam("artifactId", "testListArtifactVersions/EmptyAPI")
                .get("/artifacts/{artifactId}/versions")
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
                .get("/artifacts/{artifactId}/versions")
            .then()
                .statusCode(404);

    }
    
    @Test
    public void testCreateArtifactVersion() {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");
        
        // Create OpenAPI artifact
        createArtifact("testCreateArtifactVersion/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Create a new version of the artifact
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI.name())
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI")
                .body(updatedArtifactContent)
                .post("/artifacts/{artifactId}/versions")
            .then()
                .statusCode(200)
                .body("version", equalTo(2))
                .body("type", equalTo(ArtifactType.OPENAPI.name()));

        // Get the artifact content (should be the updated content)
        given()
            .when()
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI")
                .get("/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API (Updated)"));

        // Try to create a new version of an artifact that doesn't exist.
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI.name())
                .pathParam("artifactId", "testCreateArtifactVersion/MissingAPI")
                .body(updatedArtifactContent)
                .post("/artifacts/{artifactId}/versions")
            .then()
                .statusCode(404);
    }
    
    @Test
    public void testGetArtifactVersion() {
        String artifactContent = resourceToString("openapi-empty.json");
        
        // Create an artifact
        createArtifact("testGetArtifactVersion/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Update the artifact 5 times
        List<Integer> versions = new ArrayList<>();
        for (int idx = 0; idx < 5; idx++) {
            Integer version = given()
                .when()
                    .contentType(CT_JSON)
                    .header("X-Registry-ArtifactType", ArtifactType.OPENAPI.name())
                    .pathParam("artifactId", "testGetArtifactVersion/EmptyAPI")
                    .body(artifactContent.replace("Empty API", "Empty API (Update " + idx + ")"))
                    .put("/artifacts/{artifactId}")
                .then()
                    .statusCode(200)
                    .body("id", equalTo("testGetArtifactVersion/EmptyAPI"))
                    .body("type", equalTo(ArtifactType.OPENAPI.name()))
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
                    .get("/artifacts/{artifactId}/versions/{version}")
                .then()
                    .statusCode(200)
                    .body("info.title", equalTo(expected));
        }
        
        // Now get a version that doesn't exist.
        given()
            .when()
                .pathParam("artifactId", "testGetArtifactVersion/EmptyAPI")
                .pathParam("version", 12345)
                .get("/artifacts/{artifactId}/versions/{version}")
            .then()
                .statusCode(404);
        
        // Now get a version of an artifact that doesn't exist.
        given()
            .when()
                .pathParam("artifactId", "testGetArtifactVersion/MissingAPI")
                .pathParam("version", 1)
                .get("/artifacts/{artifactId}/versions/{version}")
            .then()
                .statusCode(404);
    }
    
    @Test
    public void testGetArtifactMetaDataByContent() {
        String artifactContent = resourceToString("openapi-empty.json");
        
        // Create an artifact
        createArtifact("testGetArtifactMetaDataByContent/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Update the artifact 5 times
        List<Integer> versions = new ArrayList<>();
        for (int idx = 0; idx < 5; idx++) {
            Integer version = given()
                .when()
                    .contentType(CT_JSON)
                    .header("X-Registry-ArtifactType", ArtifactType.OPENAPI.name())
                    .pathParam("artifactId", "testGetArtifactMetaDataByContent/EmptyAPI")
                    .body(artifactContent.replace("Empty API", "Empty API (Update " + idx + ")"))
                    .put("/artifacts/{artifactId}")
                .then()
                    .statusCode(200)
                    .body("id", equalTo("testGetArtifactMetaDataByContent/EmptyAPI"))
                    .body("type", equalTo(ArtifactType.OPENAPI.name()))
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
                .post("/artifacts/{artifactId}/meta")
            .then()
                .statusCode(200)
                .body("type", equalTo("OPENAPI"))
            .extract().body().path("globalId");
        
        // Now add some extra whitespace/formatting to the content and try again
        searchContent = searchContent.replace("{", "{\n").replace("}", "\n}");
        Integer globalId2 = given()
            .when()
                .contentType(CT_JSON)
                .pathParam("artifactId", "testGetArtifactMetaDataByContent/EmptyAPI")
                .body(searchContent)
                .post("/artifacts/{artifactId}/meta")
            .then()
                .statusCode(200)
                .body("type", equalTo("OPENAPI"))
            .extract().body().path("globalId");

        // Should return the same meta-data
        Assertions.assertEquals(globalId1, globalId2);
    }

    @Test
    public void testArtifactRules() {
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
                .post("/artifacts/{artifactId}/rules")
            .then()
                .statusCode(204)
                .body(anything());
        
        // Try to add the rule again - should get a 409
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .pathParam("artifactId", artifactId)
                .post("/artifacts/{artifactId}/rules")
            .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", equalTo("A rule named 'VALIDITY' already exists."));
        
        // Add another rule
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("compatibility-config");
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .pathParam("artifactId", artifactId)
                .post("/artifacts/{artifactId}/rules")
            .then()
                .statusCode(204)
                .body(anything());

        // Get the list of rules (should be 2 of them)
        given()
            .when()
                .pathParam("artifactId", artifactId)
                .get("/artifacts/{artifactId}/rules")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("[0]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY")))
                .body("[1]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY")))
                .body("[2]", nullValue());
        
        // Get a single rule by name
        given()
            .when()
                .pathParam("artifactId", artifactId)
                .get("/artifacts/{artifactId}/rules/COMPATIBILITY")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("type", equalTo("COMPATIBILITY"))
                .body("config", equalTo("compatibility-config"));

        // Update a rule's config
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("updated-configuration");
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .pathParam("artifactId", artifactId)
                .put("/artifacts/{artifactId}/rules/COMPATIBILITY")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("type", equalTo("COMPATIBILITY"))
                .body("config", equalTo("updated-configuration"));

        // Get a single (updated) rule by name
        given()
            .when()
                .pathParam("artifactId", artifactId)
                .get("/artifacts/{artifactId}/rules/COMPATIBILITY")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("type", equalTo("COMPATIBILITY"))
                .body("config", equalTo("updated-configuration"));

        // Try to update a rule's config for a rule that doesn't exist.
        // TODO test for a rule that doesn't exist
//        rule.setType("RuleDoesNotExist");
//        rule.setConfig("rdne-config");
//        given()
//            .when()
//                .contentType(CT_JSON)
//                .body(rule)
//                .pathParam("artifactId", artifactId)
//                .put("/artifacts/{artifactId}/rules/RuleDoesNotExist")
//            .then()
//                .statusCode(404)
//                .contentType(ContentType.JSON)
//                .body("error_code", equalTo(404))
//                .body("message", equalTo("No rule named 'RuleDoesNotExist' was found."));

        // Delete a rule
        given()
            .when()
                .pathParam("artifactId", artifactId)
                .delete("/artifacts/{artifactId}/rules/COMPATIBILITY")
            .then()
                .statusCode(204)
                .body(anything());

        // Get a single (deleted) rule by name (should fail with a 404)
        given()
            .when()
                .pathParam("artifactId", artifactId)
                .get("/artifacts/{artifactId}/rules/COMPATIBILITY")
            .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error_code", equalTo(404))
                .body("message", equalTo("No rule named 'COMPATIBILITY' was found."));

        // Get the list of rules (should be 1 of them)
        given()
            .when()
                .pathParam("artifactId", artifactId)
                .get("/artifacts/{artifactId}/rules")
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
                .delete("/artifacts/{artifactId}/rules")
            .then()
                .statusCode(204);

        // Get the list of rules (no rules now)
        given()
            .when()
                .pathParam("artifactId", artifactId)
                .get("/artifacts/{artifactId}/rules")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("[0]", nullValue());

        // Add a rule to an artifact that doesn't exist.
        rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .pathParam("artifactId", "MissingArtifact")
                .post("/artifacts/{artifactId}/rules")
            .then()
                .statusCode(404)
                .body(anything());
    }

    @Test
    public void testArtifactMetaData() {
        String artifactContent = resourceToString("openapi-empty.json");
        
        // Create OpenAPI artifact
        createArtifact("testGetArtifactMetaData/EmptyAPI", ArtifactType.OPENAPI, artifactContent);
        
        // Get the artifact meta-data
        given()
            .when()
                .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .get("/artifacts/{artifactId}/meta")
            .then()
                .statusCode(200)
                .body("id", equalTo("testGetArtifactMetaData/EmptyAPI"))
                .body("version", anything())
                .body("type", equalTo(ArtifactType.OPENAPI.name()))
                .body("createdOn", anything())
                .body("name", equalTo("Empty API"))
                .body("description", equalTo("An example API design using OpenAPI."));
        
        // Try to get artifact meta-data for an artifact that doesn't exist.
        given()
            .when()
                .pathParam("artifactId", "testGetArtifactMetaData/MissingAPI")
                .get("/artifacts/{artifactId}/meta")
            .then()
                .statusCode(404)
                .body("error_code", equalTo(404))
                .body("message", equalTo("No artifact with ID 'testGetArtifactMetaData/MissingAPI' was found."));
        
        // Update the artifact meta-data
        String metaData = "{\"name\": \"Empty API Name\", \"description\": \"Empty API description.\"}";
        given()
            .when()
                .contentType(CT_JSON)
                .body(metaData)
                .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .put("/artifacts/{artifactId}/meta")
            .then()
                .statusCode(204);

        // Get the (updated) artifact meta-data
        given()
            .when()
                .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .get("/artifacts/{artifactId}/meta")
            .then()
                .statusCode(200)
                .body("id", equalTo("testGetArtifactMetaData/EmptyAPI"))
                .body("version", anything())
                .body("name", equalTo("Empty API Name"))
                .body("description", equalTo("Empty API description."));
        
        // Update the artifact content and then make sure the name/description meta-data is still available
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI.name())
                .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .body(updatedArtifactContent)
                .put("/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .body("id", equalTo("testGetArtifactMetaData/EmptyAPI"))
                .body("type", equalTo(ArtifactType.OPENAPI.name()));

        // Verify the artifact meta-data name and description are still set.
        given()
            .when()
                .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .get("/artifacts/{artifactId}/meta")
            .then()
                .statusCode(200)
                .body("id", equalTo("testGetArtifactMetaData/EmptyAPI"))
                .body("version", anything())
                .body("name", equalTo("Empty API (Updated)"))
                .body("description", equalTo("An example API design using OpenAPI."));
        
    }
    
    @Test
    public void testArtifactVersionMetaData() {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent_v2 = artifactContent.replace("Empty API", "Empty API (v2)");
        String updatedArtifactContent_v3 = artifactContent.replace("Empty API", "Empty API (v3)");
        
        // Create OpenAPI artifact
        createArtifact("testArtifactVersionMetaData/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Create a new version of the artifact
        int version2 = given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI.name())
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .body(updatedArtifactContent_v2)
                .post("/artifacts/{artifactId}/versions")
            .then()
                .statusCode(200)
                .body("version", notNullValue())
                .body("type", equalTo(ArtifactType.OPENAPI.name()))
            .extract().body().path("version");

        // Create another new version of the artifact
        int version3 = given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI.name())
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .body(updatedArtifactContent_v3)
                .post("/artifacts/{artifactId}/versions")
            .then()
                .statusCode(200)
                .body("version", notNullValue())
                .body("type", equalTo(ArtifactType.OPENAPI.name()))
            .extract().body().path("version");

        // Get meta-data for v2
        given()
            .when()
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .pathParam("version", version2)
                .get("/artifacts/{artifactId}/versions/{version}/meta")
            .then()
                .statusCode(200)
                .body("version", equalTo(version2))
                .body("type", equalTo(ArtifactType.OPENAPI.name()))
                .body("createdOn", anything())
                .body("name", equalTo("Empty API (v2)"))
                .body("description", equalTo("An example API design using OpenAPI."));

        // Update the version meta-data
        String metaData = "{\"name\": \"Updated Name\", \"description\": \"Updated description.\"}";
        given()
            .when()
                .contentType(CT_JSON)
                .body(metaData)
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .pathParam("version", version2)
                .put("/artifacts/{artifactId}/versions/{version}/meta")
            .then()
                .statusCode(204);

        // Get the (updated) artifact meta-data
        given()
            .when()
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .pathParam("version", version2)
                .get("/artifacts/{artifactId}/versions/{version}/meta")
            .then()
                .statusCode(200)
                .body("version", equalTo(version2))
                .body("type", equalTo(ArtifactType.OPENAPI.name()))
                .body("createdOn", anything())
                .body("name", equalTo("Updated Name"))
                .body("description", equalTo("Updated description."));

        // Get the version meta-data for the version we **didn't** update
        given()
            .when()
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .pathParam("version", version3)
                .get("/artifacts/{artifactId}/versions/{version}/meta")
            .then()
                .statusCode(200)
                .body("version", equalTo(version3))
                .body("type", equalTo(ArtifactType.OPENAPI.name()))
                .body("createdOn", anything())
                .body("name", equalTo("Empty API (v3)"))
                .body("description", equalTo("An example API design using OpenAPI."));

        // Get the version meta-data for a non-existant version
        given()
            .when()
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .pathParam("version", 12345)
                .get("/artifacts/{artifactId}/versions/{version}/meta")
            .then()
                .statusCode(404);

    }

    @Test
    public void testYamlContentType() {
        String artifactId = "testYamlContentType";
        ArtifactType artifactType = ArtifactType.OPENAPI;
        String artifactContent = resourceToString("openapi-empty.yaml");

        // Create OpenAPI artifact (from YAML)
        given()
            .config(RestAssuredConfig.config().encoderConfig(EncoderConfig.encoderConfig().encodeContentTypeAs(CT_YAML, ContentType.TEXT)))
            .when()
                .contentType(CT_YAML)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType.name())
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("name", equalTo("Empty API"))
                .body("description", equalTo("An example API design using OpenAPI."))
                .body("type", equalTo(artifactType.name()));

        // Get the artifact content (should be JSON)
        given()
            .when()
                .pathParam("artifactId", "testYamlContentType")
                .get("/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .header("Content-Type", Matchers.containsString(CT_JSON))
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));
    }


    @Test
    public void testWsdlArtifact() {
        String artifactId = "testWsdlArtifact";
        ArtifactType artifactType = ArtifactType.WSDL;
        String artifactContent = resourceToString("sample.wsdl");

        // Create OpenAPI artifact (from YAML)
        given()
            .config(RestAssuredConfig.config().encoderConfig(EncoderConfig.encoderConfig().encodeContentTypeAs(CT_XML, ContentType.TEXT)))
            .when()
                .contentType(CT_XML)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType.name())
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("type", equalTo(artifactType.name()));

        // Get the artifact content (should be XML)
        given()
            .when()
                .pathParam("artifactId", "testWsdlArtifact")
                .get("/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .header("Content-Type", Matchers.containsString(CT_XML));
    }

    @Test
    public void testCreateAlreadyExistingArtifact() {

        final String artifactId = UUID.randomUUID().toString();
        final String artifactContent = resourceToString("openapi-empty.json");
        final String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");


        // Create OpenAPI artifact - indicate the type via a header param
        createArtifact(artifactId, ArtifactType.OPENAPI, artifactContent);

        // Try to create the same artifact ID (should fail)
        given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", artifactId)
                .body(artifactContent)
                .post("/artifacts")
                .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", equalTo("An artifact with ID '"+artifactId+"' already exists."));

        // Try to create the same artifact ID with Return for if exists (should return same artifact)
        given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", artifactId)
                .queryParam("ifExists", IfExistsType.RETURN)
                .body(artifactContent)
                .post("/artifacts")
                .then()
                .statusCode(200)
                .body("type", equalTo(ArtifactType.OPENAPI.name()))
                .body("version", equalTo(1))
                .body("createdOn", anything())
                .body("name", equalTo("Empty API"))
                .body("description", equalTo("An example API design using OpenAPI."));;

        // Try to create the same artifact ID with Update for if exists (should update the artifact)
        given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", artifactId)
                .queryParam("ifExists", IfExistsType.UPDATE)
                .body(updatedArtifactContent)
                .post("/artifacts")
                .then()
                .statusCode(200)
                .body("type", equalTo(ArtifactType.OPENAPI.name()))
                .body("createdOn", anything())
                .body("version", equalTo(2))
                .body("description", equalTo("An example API design using OpenAPI."));;
    }

    @Test
    public void testDeleteArtifactWithRule() {
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
                .post("/artifacts/{artifactId}/rules")
            .then()
                .statusCode(204)
                .body(anything());
        
        // Get a single rule by name
        given()
            .when()
                .pathParam("artifactId", artifactId)
                .get("/artifacts/{artifactId}/rules/VALIDITY")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("type", equalTo("VALIDITY"))
                .body("config", equalTo("FULL"));

        // Delete the artifact
        given()
            .when()
                .pathParam("artifactId", artifactId)
                .delete("/artifacts/{artifactId}")
            .then()
                .statusCode(204);
        
        // Get a single rule by name (should be 404 because the artifact is gone)
        given()
            .when()
                .pathParam("artifactId", artifactId)
                .get("/artifacts/{artifactId}/rules/VALIDITY")
            .then()
                .statusCode(404);

        // Re-create the artifact
        createArtifact(artifactId, ArtifactType.OPENAPI, artifactContent);

        // Get a single rule by name (should be 404 because the artifact is gone)
        given()
            .when()
                .pathParam("artifactId", artifactId)
                .get("/artifacts/{artifactId}/rules/VALIDITY")
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
                .post("/artifacts/{artifactId}/rules")
            .then()
                .statusCode(204)
                .body(anything());
    }

}
