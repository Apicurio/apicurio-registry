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

import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.junit.jupiter.api.BeforeEach;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.util.ServiceInitializer;
import io.apicurio.registry.utils.tests.TestUtils;
import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;

/**
 * Abstract base class for all tests that test via the jax-rs layer.
 * @author eric.wittmann@gmail.com
 */
public abstract class AbstractResourceTestBase extends AbstractRegistryTestBase {
    
    protected static final String CT_JSON = "application/json";
    protected static final String CT_PROTO = "application/x-protobuf";
    protected static final String CT_YAML = "application/x-yaml";
    protected static final String CT_XML = "application/xml";

    @Inject
    Instance<ServiceInitializer> initializers;

    @BeforeEach
    protected void beforeEach() {
        RestAssured.baseURI = "http://localhost:8081/api";
        
        // run all initializers::beforeEach
        initializers.stream().forEach(ServiceInitializer::beforeEach);

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
    protected Integer createArtifact(String artifactId, ArtifactType artifactType, String artifactContent) throws Exception {
        ValidatableResponse response = given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType.name())
                .body(artifactContent)
            .post("/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("type", equalTo(artifactType.name()));
        
        waitForArtifact(artifactId);
        
        return response.extract().body().path("globalId");
    }
    
    /**
     * Wait for an artifact to be created.
     * @param artifactId
     * @throws Exception
     */
    protected void waitForArtifact(String artifactId) throws Exception {
        TestUtils.retry(() -> {
            given()
                .when()
                    .contentType(CT_JSON)
                    .pathParam("artifactId", artifactId)
                .get("/artifacts/{artifactId}/meta")
                .then()
                    .statusCode(200);
        });
    }
    
    /**
     * Wait for an artifact version to be created.
     * @param globalId
     * @throws Exception
     */
    protected void waitForGlobalId(long globalId) throws Exception {
        TestUtils.retry(() -> {
            given()
                .when()
                    .contentType(CT_JSON)
                    .pathParam("globalId", globalId)
                .get("/ids/{globalId}/meta")
                .then()
                    .statusCode(200);
        });
    }
}
