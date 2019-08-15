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

import org.junit.jupiter.api.Test;

import io.apicurio.registry.ccompat.rest.RestConstants;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
public class ArtifactsResourceTest {

    @Test
    public void testCreateArtifact() {
        // Add an artifact
        String artifactContent = "{\r\n" + 
                "    \"openapi\": \"3.0.2\",\r\n" + 
                "    \"info\": {\r\n" + 
                "        \"title\": \"Empty API\",\r\n" + 
                "        \"version\": \"1.0.0\",\r\n" + 
                "        \"description\": \"An example API design using OpenAPI.\"\r\n" + 
                "    }\r\n" + 
                "}";
        
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
        // Add an artifact
        String artifactContent = "{\r\n" + 
                "    \"openapi\": \"3.0.2\",\r\n" + 
                "    \"info\": {\r\n" + 
                "        \"title\": \"Empty API\",\r\n" + 
                "        \"version\": \"1.0.0\",\r\n" + 
                "        \"description\": \"An example API design using OpenAPI.\"\r\n" + 
                "    }\r\n" + 
                "}";
        
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
        String artifactContent = "{\r\n" + 
                "    \"openapi\": \"3.0.2\",\r\n" + 
                "    \"info\": {\r\n" + 
                "        \"title\": \"Empty API\",\r\n" + 
                "        \"version\": \"1.0.0\",\r\n" + 
                "        \"description\": \"An example API design using OpenAPI.\"\r\n" + 
                "    }\r\n" + 
                "}";
        String updatedArtifactContent = "{\r\n" + 
                "    \"openapi\": \"3.0.2\",\r\n" + 
                "    \"info\": {\r\n" + 
                "        \"title\": \"Empty API (Updated)\",\r\n" + 
                "        \"version\": \"1.0.0\",\r\n" + 
                "        \"description\": \"An UPDATED example API design using OpenAPI.\"\r\n" + 
                "    }\r\n" + 
                "}";
        
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
                .header("X-Registry-ArtifactId", "testUpdateArtifact/EmptyAPI")
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
}
