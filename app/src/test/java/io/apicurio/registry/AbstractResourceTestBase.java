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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.apicurio.registry.rest.client.AdminClientFactory;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.ContentCreateRequest;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.rest.client.auth.Auth;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.AdminClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.types.ArtifactMediaTypes;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import io.restassured.response.ValidatableResponse;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for all tests that test via the jax-rs layer.
 * @author eric.wittmann@gmail.com
 */
@TestInstance(Lifecycle.PER_CLASS)
public abstract class AbstractResourceTestBase extends AbstractRegistryTestBase {

    public static final String CT_JSON = "application/json";
    protected static final String CT_PROTO = "application/x-protobuf";
    protected static final String CT_YAML = "application/x-yaml";
    protected static final String CT_XML = "application/xml";
    public static final String CT_JSON_EXTENDED = "application/create.extended+json";

    public String registryApiBaseUrl;
    protected String registryV1ApiUrl;
    protected String registryV2ApiUrl;
    protected RegistryClient clientV2;
    protected AdminClient adminClientV2;

    @Inject
    @Current
    RegistryStorage storage;

    @BeforeAll
    protected void beforeAll() throws Exception {
        String serverUrl = "http://localhost:%s/apis";
        registryApiBaseUrl = String.format(serverUrl, testPort);
        registryV1ApiUrl = registryApiBaseUrl + "/registry/v1";
        registryV2ApiUrl = registryApiBaseUrl + "/registry/v2";
        clientV2 = createRestClientV2();
        adminClientV2 = createAdminClientV2();
    }

    @AfterAll
    protected void afterAll() {
        //delete data to
        //storage.deleteAllUserData();
    }

    protected RegistryClient createRestClientV2() {
        return RegistryClientFactory.create(registryV2ApiUrl);
    }

    protected AdminClient createAdminClientV2() {
        return AdminClientFactory.create(registryV2ApiUrl);
    }

    protected RegistryClient createClient(Auth auth) {
        return RegistryClientFactory.create(registryV2ApiUrl, Collections.emptyMap(), auth);
    }

    protected AdminClient createAdminClient(Auth auth) {
        return AdminClientFactory.create(registryV2ApiUrl, Collections.emptyMap(), auth);
    }

    @BeforeEach
    protected void beforeEach() throws Exception {
        setupRestAssured();
        deleteGlobalRules(0);
    }

    protected void setupRestAssured() {
        RestAssured.baseURI = registryApiBaseUrl;
        RestAssured.registerParser(ArtifactMediaTypes.BINARY.toString(), Parser.JSON);
    }

    protected void deleteGlobalRules(int expectedDefaultRulesCount) throws Exception {
        // Delete all global rules
        TestUtils.retry(() -> {
            adminClientV2.deleteAllGlobalRules();
            Assertions.assertEquals(expectedDefaultRulesCount, adminClientV2.listGlobalRules().size());
        });
    }

    protected Integer createArtifact(String artifactId, String artifactType, String artifactContent) throws Exception {
        ValidatableResponse response = given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType)
                .body(artifactContent)
            .post("/registry/v1/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("type", equalTo(artifactType));

        waitForArtifact(artifactId);

        return response.extract().body().path("globalId");
    }


    protected Integer createArtifact(String groupId, String artifactId, String artifactType, String artifactContent) throws Exception {
        ValidatableResponse response = given()
            .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType)
                .body(artifactContent)
            .post("/registry/v2/groups/{groupId}/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("type", equalTo(artifactType));

        waitForArtifact(groupId, artifactId);

        return response.extract().body().path("globalId");
    }

    protected Integer createArtifactWithReferences(String groupId, String artifactId, String artifactType, String artifactContent, List<ArtifactReference> artifactReferences) throws Exception {

        ContentCreateRequest contentCreateRequest = new ContentCreateRequest();
        contentCreateRequest.setContent(artifactContent);
        contentCreateRequest.setReferences(artifactReferences);

        ValidatableResponse response = given()
                .when()
                .contentType(CT_JSON_EXTENDED)
                .pathParam("groupId", groupId)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType)
                .body(contentCreateRequest)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("type", equalTo(artifactType));

        waitForArtifact(groupId, artifactId);

        return response.extract().body().path("globalId");
    }

    protected Integer createArtifactVersion(String artifactId, String artifactType, String artifactContent) throws Exception {
        ValidatableResponse response = given()
            .when()
                .contentType(CT_JSON)
                .pathParam("artifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType)
                .body(artifactContent)
            .post("/registry/v1/artifacts/{artifactId}/versions")
            .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("type", equalTo(artifactType));

        return response.extract().body().path("globalId");
    }

    protected Integer createArtifactVersion(String groupId, String artifactId, String artifactType, String artifactContent) throws Exception {
        ValidatableResponse response = given()
            .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType)
                .body(artifactContent)
            .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
            .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("type", equalTo(artifactType));

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
                .get("/registry/v1/artifacts/{artifactId}/meta")
                .then()
                    .statusCode(200);
        });
    }

    /**
     * Wait for an artifact to be created.
     * @param groupId
     * @param artifactId
     * @throws Exception
     */
    protected void waitForArtifact(String groupId, String artifactId) throws Exception {
        TestUtils.retry(() -> {
            given()
                .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", groupId == null ? "default" : groupId)
                    .pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                .then()
                    .statusCode(200);
        });
    }

    /**
     * Wait for an artifact rule to be created.
     * @param ruleType
     * @throws Exception
     */
    protected void waitForArtifactRule(String artifactId, RuleType ruleType) throws Exception {
        TestUtils.retry(() -> {
            given()
                    .when()
                    .contentType(CT_JSON)
                    .pathParam("rule", ruleType.value())
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v1/artifacts/{artifactId}/rules/{rule}")
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
                .get("/registry/v1/artifacts/{artifactId}/versions/{version}/meta")
                .then()
                    .statusCode(200);
        });
    }

    /**
     * Wait for an artifact version to be created.
     * @param groupId
     * @param artifactId
     * @param version
     * @throws Exception
     */
    protected void waitForVersion(String groupId, String artifactId, int version) throws Exception {
        TestUtils.retry(() -> {
            given()
                .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", groupId)
                    .pathParam("artifactId", artifactId)
                    .pathParam("version", version)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}/meta")
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
                .get("/registry/v1/ids/{globalId}/meta")
                .then()
                    .statusCode(200);
        });
    }

    /**
     * Wait for an artifact version to be created.
     * @param contentId
     * @throws Exception
     */
    protected void waitForContentId(long contentId) throws Exception {
        TestUtils.retry(() -> {
            given()
                    .when()
                    .contentType(CT_JSON)
                    .pathParam("contentId", contentId)
                    .get("/registry/v2/ids/contentIds/{contentId}")
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
                .get("/registry/v1/artifacts/{artifactId}/meta")
                .then(), state, false);
        });
    }

    /**
     * Wait for an artifact's state to change.
     * @param groupId
     * @param artifactId
     * @param state
     * @throws Exception
     */
    protected void waitForArtifactState(String groupId, String artifactId, ArtifactState state) throws Exception {
        TestUtils.retry(() -> {
            validateMetaDataResponseState(given()
                .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", groupId)
                    .pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
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
                .get("/registry/v1/artifacts/{artifactId}/versions/{version}/meta")
                .then(), state, true);
        });
    }

    /**
     * Wait for an artifact version's state to change.
     * @param groupId
     * @param artifactId
     * @param version
     * @param state
     * @throws Exception
     */
    protected void waitForVersionState(String groupId, String artifactId, String version, ArtifactState state) throws Exception {
        TestUtils.retry(() -> {
            validateMetaDataResponseState(given()
                .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", groupId)
                    .pathParam("artifactId", artifactId)
                    .pathParam("version", version)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}/meta")
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
                .get("/registry/v1/ids/{globalId}/meta")
                .then(), state, true);
        });
    }

    /**
     * Wait for a global rule to be created.
     * @param ruleType
     * @param ruleConfig
     * @throws Exception
     */
    public void waitForGlobalRule(RuleType ruleType, String ruleConfig) throws Exception {
        // Verify the default global rule exists
        TestUtils.retry(() -> {
            given()
                    .when()
                    .get("/registry/v1/rules/VALIDITY")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo(ruleType.value()))
                    .body("config", equalTo(ruleConfig));
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
