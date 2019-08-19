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
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.List;

import org.hamcrest.CustomMatcher;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.ccompat.rest.RestConstants;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
public class ArtifactsResourceTest {

    private static final String EMPTY_API_CONTENT = "{\r\n" + 
            "    \"openapi\": \"3.0.2\",\r\n" + 
            "    \"info\": {\r\n" + 
            "        \"title\": \"Empty API\",\r\n" + 
            "        \"version\": \"1.0.0\",\r\n" + 
            "        \"description\": \"An example API design using OpenAPI.\"\r\n" + 
            "    }\r\n" + 
            "}";

    @Test
    public void testCreateArtifact() {
        String artifactContent = EMPTY_API_CONTENT;
        
        // Create OpenAPI artifact - indicate the type via a header param
        given()
            .when()
                .contentType(RestConstants.JSON)
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI/1")
                .header("X-Registry-ArtifactType", "openapi")
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo("testCreateArtifact/EmptyAPI/1"))
                .body("type", equalTo("openapi"));

        // Create OpenAPI artifact - indicate the type via the content-type
        given()
            .when()
                .contentType(RestConstants.JSON + "; artifactType=openapi")
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI/2")
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo("testCreateArtifact/EmptyAPI/2"))
                .body("type", equalTo("openapi"));

        // Try to create the same artifact ID (should fail)
        given()
            .when()
                .contentType(RestConstants.JSON + "; artifactType=openapi")
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI/2")
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(409)
                .body("code", equalTo(409))
                .body("message", equalTo("An artifact with ID 'testCreateArtifact/EmptyAPI/2' already exists."));

        // Try to create an artifact with an invalid artifact type
        given()
            .when()
                .contentType(RestConstants.JSON + "; artifactType=INVALID_ARTIFACT_TYPE")
                .header("X-Registry-ArtifactId", "testCreateArtifact/InvalidAPI")
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(400);
    }

    @Test
    public void testGetArtifact() {
        String artifactContent = EMPTY_API_CONTENT;
        
        // Create OpenAPI artifact
        given()
            .when()
                .contentType(RestConstants.JSON)
                .header("X-Registry-ArtifactId", "testGetArtifact/EmptyAPI")
                .header("X-Registry-ArtifactType", "openapi")
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo("testGetArtifact/EmptyAPI"))
                .body("type", equalTo("openapi"));
        
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
                .body("code", equalTo(404))
                .body("message", equalTo("No artifact with ID 'testGetArtifact/MissingAPI' was found."));
    }

    @Test
    public void testUpdateArtifact() {
        String artifactContent = EMPTY_API_CONTENT;
        String updatedArtifactContent = EMPTY_API_CONTENT.replace("Empty API", "Empty API (Updated)");
        
        // Create OpenAPI artifact
        given()
            .when()
                .contentType(RestConstants.JSON)
                .header("X-Registry-ArtifactId", "testUpdateArtifact/EmptyAPI")
                .header("X-Registry-ArtifactType", "openapi")
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo("testUpdateArtifact/EmptyAPI"))
                .body("type", equalTo("openapi"));

        // Update OpenAPI artifact
        given()
            .when()
                .contentType(RestConstants.JSON)
                .header("X-Registry-ArtifactType", "openapi")
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI")
                .body(updatedArtifactContent)
                .put("/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .body("id", equalTo("testUpdateArtifact/EmptyAPI"))
                .body("type", equalTo("openapi"));

        // Get the artifact content (should be the updated content)
        given()
            .when()
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI")
                .get("/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API (Updated)"));
    }
    
    @Test
    public void testDeleteArtifact() {
        String artifactContent = EMPTY_API_CONTENT;
        
        // Create OpenAPI artifact
        given()
            .when()
                .contentType(RestConstants.JSON)
                .header("X-Registry-ArtifactId", "testDeleteArtifact/EmptyAPI")
                .header("X-Registry-ArtifactType", "openapi")
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo("testDeleteArtifact/EmptyAPI"))
                .body("type", equalTo("openapi"));

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
                .body("code", equalTo(404))
                .body("message", equalTo("No artifact with ID 'testDeleteArtifact/EmptyAPI' was found."));
    
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testListArtifactVersions() {
        String artifactContent = EMPTY_API_CONTENT;
        
        // Create an artifact
        given()
            .when()
                .contentType(RestConstants.JSON)
                .header("X-Registry-ArtifactId", "testListArtifactVersions/EmptyAPI")
                .header("X-Registry-ArtifactType", "openapi")
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo("testListArtifactVersions/EmptyAPI"))
                .body("type", equalTo("openapi"));

        // Update the artifact 5 times
        for (int idx = 0; idx < 5; idx++) {
            given()
                .when()
                    .contentType(RestConstants.JSON)
                    .header("X-Registry-ArtifactType", "openapi")
                    .pathParam("artifactId", "testListArtifactVersions/EmptyAPI")
                    .body(artifactContent.replace("Empty API", "Empty API (Update " + idx + ")"))
                    .put("/artifacts/{artifactId}")
                .then()
                    .statusCode(200)
                    .body("id", equalTo("testListArtifactVersions/EmptyAPI"))
                    .body("type", equalTo("openapi"));
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
    }
    
    @Test
    public void testCreateArtifactVersion() {
        String artifactContent = EMPTY_API_CONTENT;
        String updatedArtifactContent = EMPTY_API_CONTENT.replace("Empty API", "Empty API (Updated)");
        
        // Create OpenAPI artifact
        given()
            .when()
                .contentType(RestConstants.JSON)
                .header("X-Registry-ArtifactId", "testCreateArtifactVersion/EmptyAPI")
                .header("X-Registry-ArtifactType", "openapi")
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo("testCreateArtifactVersion/EmptyAPI"))
                .body("type", equalTo("openapi"));

        // Create a new version of the artifact
        given()
            .when()
                .contentType(RestConstants.JSON)
                .header("X-Registry-ArtifactType", "openapi")
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI")
                .body(updatedArtifactContent)
                .post("/artifacts/{artifactId}/versions")
            .then()
                .statusCode(200)
                .body("version", equalTo(2))
                .body("type", equalTo("openapi"));

        // Get the artifact content (should be the updated content)
        given()
            .when()
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI")
                .get("/artifacts/{artifactId}")
            .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API (Updated)"));
    }
    
    @Test
    public void testGetArtifactVersion() {
        String artifactContent = EMPTY_API_CONTENT;
        
        // Create an artifact
        given()
            .when()
                .contentType(RestConstants.JSON)
                .header("X-Registry-ArtifactId", "testGetArtifactVersion/EmptyAPI")
                .header("X-Registry-ArtifactType", "openapi")
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo("testGetArtifactVersion/EmptyAPI"))
                .body("type", equalTo("openapi"));

        // Update the artifact 5 times
        List<Integer> versions = new ArrayList<>();
        for (int idx = 0; idx < 5; idx++) {
            Integer version = given()
                .when()
                    .contentType(RestConstants.JSON)
                    .header("X-Registry-ArtifactType", "openapi")
                    .pathParam("artifactId", "testGetArtifactVersion/EmptyAPI")
                    .body(artifactContent.replace("Empty API", "Empty API (Update " + idx + ")"))
                    .put("/artifacts/{artifactId}")
                .then()
                    .statusCode(200)
                    .body("id", equalTo("testGetArtifactVersion/EmptyAPI"))
                    .body("type", equalTo("openapi"))
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
    }

    @Test
    public void testDeleteArtifactVersion() {
        String artifactContent = EMPTY_API_CONTENT;
        
        // Create an artifact
        given()
            .when()
                .contentType(RestConstants.JSON)
                .header("X-Registry-ArtifactId", "testDeleteArtifactVersion/EmptyAPI")
                .header("X-Registry-ArtifactType", "openapi")
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo("testDeleteArtifactVersion/EmptyAPI"))
                .body("type", equalTo("openapi"));

        // Update the artifact 5 times
        List<Integer> versions = new ArrayList<>();
        for (int idx = 0; idx < 5; idx++) {
            Integer version = given()
                .when()
                    .contentType(RestConstants.JSON)
                    .header("X-Registry-ArtifactType", "openapi")
                    .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI")
                    .body(artifactContent.replace("Empty API", "Empty API (Update " + idx + ")"))
                    .put("/artifacts/{artifactId}")
                .then()
                    .statusCode(200)
                    .body("id", equalTo("testDeleteArtifactVersion/EmptyAPI"))
                    .body("type", equalTo("openapi"))
                .extract().body().path("version");
            System.out.println("Update.  Created version: " + version);
            versions.add(version);
        }
        
        // Delete odd artifact versions
        for (int idx = 0; idx < 5; idx++) {
            if (idx % 2 == 0) {
                continue;
            }
            
            Integer version = versions.get(idx);
            System.out.println("Deleting version: " + version);
            given()
                .when()
                    .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI")
                    .pathParam("version", version)
                    .delete("/artifacts/{artifactId}/versions/{version}")
                .then()
                    .statusCode(204);
        }
        
        // Check that the correct versions were deleted
        for (int idx = 0; idx < 5; idx++) {
            Integer version = versions.get(idx);
            int expectedCode;
            // Even indexes are still there, odd were deleted
            if (idx % 2 == 0) {
                expectedCode = 200;
            } else {
                expectedCode = 404;
            }
            System.out.println("Checking version: " + version);
            given()
                .when()
                    .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI")
                    .pathParam("version", version)
                    .get("/artifacts/{artifactId}/versions/{version}")
                .then()
                    .statusCode(expectedCode);
        }

        // Now try to delete a version that doesn't exist.
        given()
            .when()
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI")
                .pathParam("version", 12345)
                .get("/artifacts/{artifactId}/versions/{version}")
            .then()
                .statusCode(404);

        // Try to delete a version of an artifact where the artifact doesn't exist
        given()
            .when()
                .pathParam("artifactId", "testGetArtifactVersion/MissingAPI")
                .pathParam("version", 1)
                .get("/artifacts/{artifactId}/versions/{version}")
            .then()
                .statusCode(404);
    }


}
