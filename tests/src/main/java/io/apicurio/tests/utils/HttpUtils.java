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
import io.apicurio.registry.rest.beans.Rule;
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
        return getArtifactSpecificVersion(artifactId, "versions/" + version, 200);
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

    public static Response createGlobalRule(Rule rule) {
        return createGlobalRule(rule, 204);
    }

    public static Response createGlobalRule(Rule rule, int returnCode) {
        return rulesPostRequest(RestConstants.JSON, rule, "/rules", returnCode);
    }

    public static Response getRequest(String contentType, String endpoint, int returnCode) {
        return given()
            .when()
                .contentType(contentType)
                .get(endpoint)
            .then()
                .statusCode(returnCode)
                .extract()
                .response();
    }

    public static Response postRequest(String contentType, String body, String endpoint, int returnCode) {
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

    public static Response putRequest(String contentType, String body, String endpoint, int returnCode) {
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

    public static Response deleteRequest(String contentType, String endpoint, int returnCode) {
        return given()
            .when()
                .contentType(contentType)
                .delete(endpoint)
            .then()
                .statusCode(returnCode)
                .extract()
                .response();
    }

    public static Response rulesPostRequest(String contentType, Rule rule, String endpoint, int returnCode) {
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

    public static HashMap<String, String> getFieldsFromResponse(JsonPath jsonPath) {
        return (HashMap<String, String>) jsonPath.getList("fields").get(0);
    }
}
