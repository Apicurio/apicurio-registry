/*
 * Copyright 2020 Red Hat
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

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.client.RegistryRestClientFactory;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.util.ServiceInitializer;
import io.apicurio.registry.utils.tests.TestUtils;
import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

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

    protected static String registryUrl;
    protected static RegistryRestClient client;

    @BeforeAll
    protected static void beforeAll() throws Exception {
        registryUrl = "http://localhost:8081/api";
        client = RegistryRestClientFactory.create(registryUrl);
    }

    @BeforeEach
    protected void beforeEach() throws Exception {
        prepareServiceInitializers();
        deleteGlobalRules(0);
    }

    protected void prepareServiceInitializers() {
        RestAssured.baseURI = registryUrl;

        // run all initializers::beforeEach
        initializers.stream().forEach(ServiceInitializer::beforeEach);
    }

    protected void deleteGlobalRules(int expectedDefaultRulesCount) throws Exception {
        // Delete all global rules
        given().when().delete("/rules").then().statusCode(204);
        TestUtils.retry(() -> {
            given().when().get("/rules").then().statusCode(200).body("size()", is(expectedDefaultRulesCount));
        });
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
     * @param artifactId
     * @param version
     * @throws Exception
     */
    protected void waitForVersion(String artifactId, int version) throws Exception {
        TestUtils.retry(() -> {
            given()
                .when()
                    .contentType(CT_JSON)
                    .pathParam("artifactId", artifactId)
                    .pathParam("version", version)
                .get("/artifacts/{artifactId}/versions/{version}/meta")
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

    /**
     * Wait for an artifact's state to change.
     * @param artifactId
     * @param state
     * @throws Exception
     */
    protected void waitForArtifactState(String artifactId, ArtifactState state) throws Exception {
        TestUtils.retry(() -> {
            validateMetaDataResponseState(given()
                .when()
                    .contentType(CT_JSON)
                    .pathParam("artifactId", artifactId)
                .get("/artifacts/{artifactId}/meta")
                .then(), state, false);
        });
    }

    /**
     * Wait for an artifact version's state to change.
     * @param artifactId
     * @param version
     * @param state
     * @throws Exception
     */
    protected void waitForVersionState(String artifactId, int version, ArtifactState state) throws Exception {
        TestUtils.retry(() -> {
            validateMetaDataResponseState(given()
                .when()
                    .contentType(CT_JSON)
                    .pathParam("artifactId", artifactId)
                    .pathParam("version", version)
                .get("/artifacts/{artifactId}/versions/{version}/meta")
                .then(), state, true);
        });
    }

    /**
     * Wait for an artifact version's state to change (by global id).
     * @param globalId
     * @param state
     * @throws Exception
     */
    protected void waitForVersionState(long globalId, ArtifactState state) throws Exception {
        TestUtils.retry(() -> {
            validateMetaDataResponseState(given()
                .when()
                    .contentType(CT_JSON)
                    .pathParam("globalId", globalId)
                .get("/ids/{globalId}/meta")
                .then(), state, true);
        });
    }

    /**
     * Ensures the state of the meta-data response is what we expect.
     * @param response
     * @param state
     */
    protected void validateMetaDataResponseState(ValidatableResponse response, ArtifactState state, boolean version) {
        response.statusCode(200);
        response.body("state", equalTo(state.name()));
    }
}

