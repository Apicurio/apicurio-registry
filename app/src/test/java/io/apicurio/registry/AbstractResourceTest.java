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

import org.junit.jupiter.api.BeforeEach;

import io.apicurio.registry.ccompat.rest.RestConstants;
import io.apicurio.registry.types.ArtifactType;

/**
 * Abstract base class for all tests that test via the jax-rs layer.
 * @author eric.wittmann@gmail.com
 */
public abstract class AbstractResourceTest extends AbstractRegistryTest {
    
    @BeforeEach
    void beforeEach() {
        // Delete all global rules
        given().when().delete("/rules").then().statusCode(204);        
    }
    
    /**
     * Called to create an artifact by invoking the appropriate JAX-RS operation.
     * @param artifactId
     * @param artifactType
     * @param artifactContent
     * @throws Exception
     */
    protected void createArtifact(String artifactId, ArtifactType artifactType, String artifactContent) {
        given()
            .when()
                .contentType(RestConstants.JSON)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType.name())
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("type", equalTo(artifactType.name()));
    }
    
}
