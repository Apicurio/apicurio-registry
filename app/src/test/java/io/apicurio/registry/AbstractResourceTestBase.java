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

import static io.apicurio.registry.rest.v2.V2ApiUtil.defaultGroupIdToNull;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import io.apicurio.registry.rest.client.AdminClient;
import io.apicurio.registry.rest.client.AdminClientFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.V2ApiUtil;
import io.apicurio.registry.rest.v2.beans.ArtifactContent;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.types.ArtifactMediaTypes;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.rest.client.auth.Auth;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import io.restassured.response.ValidatableResponse;

/**
 * Abstract base class for all tests that test via the jax-rs layer.
 *
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
    protected String registryV2ApiUrl;
    protected RegistryClient clientV2;
    protected AdminClient adminClientV2;

    @BeforeAll
    protected void beforeAll() throws Exception {
        String serverUrl = "http://localhost:%s/apis";
        registryApiBaseUrl = String.format(serverUrl, testPort);
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
        return createArtifact("default", artifactId, artifactType, artifactContent);
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

        return response.extract().body().path("globalId");
    }

    protected Integer createArtifactWithReferences(String groupId, String artifactId, String artifactType, String artifactContent, List<ArtifactReference> artifactReferences) throws Exception {

        ValidatableResponse response = createArtifactExtendedRaw(groupId, artifactId, artifactType, artifactContent, artifactReferences)
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("type", equalTo(artifactType));

        return response.extract().body().path("globalId");
    }

    protected Integer updateArtifactWithReferences(String groupId, String artifactId, String artifactType, String artifactContent, List<ArtifactReference> artifactReferences) throws Exception {

        ValidatableResponse response = updateArtifactExtendedRaw(groupId, artifactId, artifactType, artifactContent, artifactReferences)
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("type", equalTo(artifactType));

        return response.extract().body().path("globalId");
    }

    protected ValidatableResponse createArtifactExtendedRaw(String groupId, String artifactId, String artifactType, String artifactContent, List<ArtifactReference> artifactReferences) throws Exception {

        ArtifactContent ArtifactContent = new ArtifactContent();
        ArtifactContent.setContent(artifactContent);
        ArtifactContent.setReferences(artifactReferences);

        var request = given()
                .when()
                .contentType(CT_JSON_EXTENDED)
                .pathParam("groupId", groupId);

        if (artifactId != null)
            request.header("X-Registry-ArtifactId", artifactId);
        if (artifactType != null)
            request.header("X-Registry-ArtifactType", artifactType);

        return request
                .body(ArtifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then();
    }

    protected ValidatableResponse updateArtifactExtendedRaw(String groupId, String artifactId, String artifactType, String artifactContent, List<ArtifactReference> artifactReferences) throws Exception {

        ArtifactContent contentCreateRequest = new ArtifactContent();
        contentCreateRequest.setContent(artifactContent);
        contentCreateRequest.setReferences(artifactReferences);

        var request = given()
                .when()
                .contentType(CT_JSON_EXTENDED)
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId);

        if (artifactId != null)
            request.header("X-Registry-ArtifactId", artifactId);
        if (artifactType != null)
            request.header("X-Registry-ArtifactType", artifactType);

        return request
                .body(contentCreateRequest)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then();
    }


    protected Integer createArtifactVersion(String artifactId, String artifactType, String artifactContent) throws Exception {
        return createArtifactVersion("default", artifactId, artifactType, artifactContent);
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

    protected void createArtifactRule(String groupId, String artifactId, RuleType ruleType, String ruleConfig) {
        final Rule rule = new Rule();
        rule.setConfig(ruleConfig);
        rule.setType(ruleType);
        clientV2.createArtifactRule(groupId, artifactId, rule);
    }

    @SuppressWarnings("deprecation")
    protected Rule createGlobalRule(RuleType ruleType, String ruleConfig) {
        final Rule rule = new Rule();
        rule.setConfig(ruleConfig);
        rule.setType(ruleType);
        clientV2.createGlobalRule(rule);

        return rule;
    }

    /**
     * Ensures the state of the meta-data response is what we expect.
     *
     * @param response
     * @param state
     */
    protected void validateMetaDataResponseState(ValidatableResponse response, ArtifactState state, boolean version) {
        response.statusCode(200);
        response.body("state", equalTo(state.name()));
    }

    protected String getRandomValidJsonSchemaContent() {
        return "{\n" +
                "  \"$id\": \"https://example.com/person.schema.json\",\n" +
                "  \"$schema\": \"http://json-schema.org/draft-07/schema#\",\n" +
                "  \"title\": \"Person-" + UUID.randomUUID() + "\",\n" +
                "  \"type\": \"object\",\n" +
                "  \"properties\": {\n" +
                "  }\n" +
                "}";
    }

    protected byte[] concatContentAndReferences(byte[] contentBytes, String references) throws IOException {
        if (references != null) {
            final byte[] referencesBytes = references.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(contentBytes.length + referencesBytes.length);
            outputStream.write(contentBytes);
            outputStream.write(referencesBytes);
            return outputStream.toByteArray();
        } else {
            return contentBytes;
        }
    }

    protected List<ArtifactReferenceDto> toReferenceDtos(List<ArtifactReference> references) {
        if (references == null) {
            references = Collections.emptyList();
        }
        return references.stream()
                .peek(r -> r.setGroupId(defaultGroupIdToNull(r.getGroupId())))
                .map(V2ApiUtil::referenceToDto)
                .collect(Collectors.toList());
    }
}
