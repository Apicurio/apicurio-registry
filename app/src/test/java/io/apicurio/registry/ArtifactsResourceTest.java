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
    }

}
