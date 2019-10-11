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
package io.apicurio.tests.utils;

import io.apicurio.registry.ccompat.rest.RestConstants;
import io.apicurio.registry.types.RuleType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

import static io.restassured.RestAssured.given;

public class HttpUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpUtils.class);

    private HttpUtils() {
        // All static methods
    }

    public static Response getArtifact(String artifactId) {
        return getArtifact(artifactId, "", 200);
    }

    public static Response getArtifact(String artifactId, int returnCode) {
        return getArtifact(artifactId, "", returnCode);
    }

    public static Response getArtifact(String artifactId, String version, int returnCode) {
        return getRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/" + version, returnCode);
    }

    public static Response getArtifactSpecificVersion(String artifactId, String version) {
        return getArtifactSpecificVersion(artifactId, version, 200);
    }

    public static Response getArtifactSpecificVersion(String artifactId, String version, int returnCode) {
        return getArtifact(artifactId, "versions/" + version, returnCode);
    }

    public static Response listArtifactVersions(String artifactId) {
        return listArtifactVersions(artifactId, 200);
    }

    public static Response listArtifactVersions(String artifactId, int returnCode) {
        return getRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/versions", returnCode);
    }

    public static Response createArtifact(String artifact) {
        return createArtifact(artifact, 200);
    }

    public static Response createArtifact(String artifact, int returnCode) {
        return postRequest(RestConstants.JSON, artifact, "/artifacts", returnCode);
    }

    public static Response createArtifactNewVersion(String artifactId, String artifact, int returnCode) {
        return postRequest(RestConstants.JSON, artifact, "/artifacts/" + artifactId + "/versions", returnCode);
    }

    public static Response updateArtifact(String artifactId, String artifact) {
        return updateArtifact(artifactId, artifact, 200);
    }

    public static Response updateArtifact(String artifactId, String artifact, int returnCode) {
        return putRequest(RestConstants.JSON, artifact, "/artifacts/" + artifactId, returnCode);
    }

    public static Response deleteArtifact(String artifactId) {
        return deleteArtifact(artifactId, 204);
    }

    public static Response deleteArtifact(String artifactId, int returnCode) {
        return deleteRequest(RestConstants.JSON, "/artifacts/" + artifactId, returnCode);
    }

    public static Response deleteArtifactVersion(String artifactId, String version) {
        return deleteArtifactVersion(artifactId, version, 204);
    }

    public static Response deleteArtifactVersion(String artifactId, String version, int returnCode) {
        return deleteRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/versions/" + version, returnCode);
    }

    public static Response createGlobalRule(String rule) {
        return createGlobalRule(rule, 204);
    }

    public static Response createGlobalRule(String rule, int returnCode) {
        return rulesPostRequest(RestConstants.JSON, rule, "/rules", returnCode);
    }

    public static Response getGlobalRule(RuleType ruleType) {
        return getGlobalRule(ruleType, 200);
    }

    public static Response getGlobalRule(RuleType ruleType, int returnCode) {
        return rulesGetRequest(RestConstants.JSON, "/rules/" + ruleType, returnCode);
    }

    public static Response updateGlobalRule(RuleType ruleType, String rule) {
        return updateGlobalRule(ruleType, rule, 200);
    }

    public static Response updateGlobalRule(RuleType ruleType, String rule, int returnCode) {
        return rulesPutRequest(RestConstants.JSON, rule,  "/rules/" + ruleType, returnCode);
    }

    public static Response deleteGlobalRule(RuleType ruleType) {
        return deleteGlobalRule(ruleType, 204);
    }

    public static Response deleteGlobalRule(RuleType ruleType, int returnCode) {
        return rulesDeleteRequest(RestConstants.JSON, "/rules/" + ruleType, returnCode);
    }

    public static Response listGlobalRules() {
        return getRequest(RestConstants.JSON, "/rules", 200);
    }

    public static Response deleteAllGlobalRules() {
        return deleteRequest(RestConstants.JSON, "/rules", 204);
    }

    public static Response createArtifactRule(String artifactId, String rule) {
        return createArtifactRule(artifactId, rule, 204);
    }

    public static Response createArtifactRule(String artifactId, String rule, int returnCode) {
        return rulesPostRequest(RestConstants.JSON, rule, "/artifacts/" + artifactId + "/rules", returnCode);
    }

    public static Response getArtifactRule(String artifactId, RuleType ruleType) {
        return getArtifactRule(artifactId, ruleType, 200);
    }

    public static Response getArtifactRule(String artifactId, RuleType ruleType, int returnCode) {
        return rulesGetRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/rules/" + ruleType, returnCode);
    }

    public static Response updateArtifactRule(String artifactId, RuleType ruleType, String rule) {
        return updateArtifactRule(artifactId, ruleType, rule, 200);
    }

    public static Response updateArtifactRule(String artifactId, RuleType ruleType, String rule, int returnCode) {
        return rulesPutRequest(RestConstants.JSON, rule, "/artifacts/" + artifactId + "/rules/" + ruleType, returnCode);
    }

    public static Response deleteArtifactString(String artifactId, RuleType ruleType) {
        return deleteArtifactRule(artifactId, ruleType, 200);
    }

    public static Response deleteArtifactRule(String artifactId, RuleType ruleType, int returnCode) {
        return rulesDeleteRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/rules/" + ruleType, returnCode);
    }

    public static Response listArtifactRules(String artifactId) {
        return getRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/rules", 200);
    }

    public static Response deleteAllGlobalRules(String artifactId) {
        return deleteRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/rules", 204);
    }

    public static Response getArtifactMetadata(String artifactId) {
        return getArtifactMetadata(artifactId, 200);
    }

    public static Response getArtifactMetadata(String artifactId, int returnCode) {
        return getRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/meta", returnCode);
    }

    public static Response getArtifactVersionMetadata(String artifactId, String version) {
        return getArtifactVersionMetadata(artifactId, version, 200);
    }

    public static Response getArtifactVersionMetadata(String artifactId, String version, int returnCode) {
        return getRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/versions/" + version + "/meta", returnCode);
    }

    public static Response updateArtifactMetadata(String artifactId, String metadata) {
        return updateArtifactMetadata(artifactId, metadata, 204);
    }

    public static Response updateArtifactMetadata(String artifactId, String metadata, int returnCode) {
        return putRequest(RestConstants.JSON, metadata, "/artifacts/" + artifactId + "/meta", returnCode);
    }

    public static Response updateArtifactVersionMetadata(String artifactId, String version, String metadata) {
        return updateArtifactVersionMetadata(artifactId, version, metadata, 204);
    }

    public static Response updateArtifactVersionMetadata(String artifactId, String version, String metadata, int returnCode) {
        return putRequest(RestConstants.JSON, metadata, "/artifacts/" + artifactId + "/versions/" + version + "/meta", returnCode);
    }

    public static Response deleteArtifactVersionMetadata(String artifactId, String version) {
        return deleteArtifactVersionMetadata(artifactId, version, 204);
    }

    public static Response deleteArtifactVersionMetadata(String artifactId, String version, int returnCode) {
        return deleteRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/versions/" + version + "/meta", returnCode);
    }

    private static Response getRequest(String contentType, String endpoint, int returnCode) {
        return given()
            .when()
                .contentType(contentType)
                .get(endpoint)
            .then()
                .statusCode(returnCode)
                .extract()
                .response();
    }

    private static Response postRequest(String contentType, String body, String endpoint, int returnCode) {
        return given()
            .when()
                .contentType(contentType)
                .body(body)
                .post(endpoint)
            .then()
                .statusCode(returnCode)
                .extract()
                .response();
    }

    private static Response putRequest(String contentType, String body, String endpoint, int returnCode) {
        return given()
            .when()
                .contentType(contentType)
                .body(body)
                .put(endpoint)
            .then()
                .statusCode(returnCode)
                .extract()
                .response();
    }

    private static Response deleteRequest(String contentType, String endpoint, int returnCode) {
        return given()
            .when()
                .contentType(contentType)
                .delete(endpoint)
            .then()
                .statusCode(returnCode)
                .extract()
                .response();
    }

    private static Response rulesPostRequest(String contentType, String rule, String endpoint, int returnCode) {
        return given()
            .when()
                .contentType(contentType)
                .body(rule)
                .post(endpoint)
            .then()
                .statusCode(returnCode)
                .extract()
                .response();
    }

    private static Response rulesGetRequest(String contentType, String endpoint, int returnCode) {
        return given()
            .when()
                .contentType(contentType)
                .get(endpoint)
            .then()
                .statusCode(returnCode)
                .extract()
                .response();
    }

    private static Response rulesPutRequest(String contentType, String rule, String endpoint, int returnCode) {
        return given()
            .when()
                .contentType(contentType)
                .body(rule)
                .put(endpoint)
            .then()
                .statusCode(returnCode)
                .extract()
                .response();
    }

    private static Response rulesDeleteRequest(String contentType, String endpoint, int returnCode) {
        return given()
            .when()
                .contentType(contentType)
                .delete(endpoint)
            .then()
                .statusCode(returnCode)
                .extract()
                .response();
    }

    public static HashMap<String, String> getFieldsFromResponse(JsonPath jsonPath) {
        return (HashMap<String, String>) jsonPath.getList("fields").get(0);
    }
}
