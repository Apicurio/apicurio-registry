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

    public static Response getSchema(String schemaId) {
        return getSchema(schemaId, "", 200);
    }

    public static Response getSchemaSpecificVersion(String schemaId, String version) {
        return getSchema(schemaId, "versions/" + version, 200);
    }

    public static Response getSchema(String schemaId, String version, int returnCode) {
        return getRequest(RestConstants.JSON, "/artifacts/" + schemaId + "/" + version, returnCode);
    }

    public static Response listSchemaVersions(String schemaId) {
        return listSchemaVersions(schemaId, 200);
    }

    public static Response listSchemaVersions(String schemaId, int returnCode) {
        return getRequest(RestConstants.JSON, "/artifacts/" + schemaId + "/versions", returnCode);
    }

    public static Response createSchema(String schema) {
        return createSchema(schema, 200);
    }

    public static Response createSchema(String schema, int returnCode) {
        return postRequest(RestConstants.JSON, schema, "/artifacts", returnCode);
    }

    public static Response updateSchema(String schemaId, String schema) {
        return updateSchema(schemaId, schema, 200);
    }

    public static Response updateSchema(String schemaId, String schema, int returnCode) {
        return putRequest(RestConstants.JSON, schema, "/artifacts/" + schemaId, returnCode);
    }

    public static Response deleteSchema(String schemaId) {
        return deleteSchema(schemaId, 200);
    }

    public static Response deleteSchema(String schemaId, int returnCode) {
        return deleteRequest(RestConstants.JSON, "/artifacts/" + schemaId, returnCode);
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
                .put(endpoint)
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
