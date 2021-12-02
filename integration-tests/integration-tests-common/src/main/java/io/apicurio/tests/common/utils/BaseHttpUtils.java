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

package io.apicurio.tests.common.utils;

import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

import java.net.URL;

public class BaseHttpUtils {

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

    public static Response getRequest(String contentType, URL endpoint, int returnCode) {
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

    public static Response postRequest(String contentType, String body, URL endpoint, int returnCode) {
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

    public static Response putRequest(String contentType, String body, URL endpoint, int returnCode) {
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

    public static Response rulesPostRequest(String contentType, String rule, String endpoint, int returnCode) {
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

    public static Response rulesPostRequest(String contentType, String rule, URL endpoint, int returnCode) {
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

    public static Response rulesGetRequest(String contentType, String endpoint, int returnCode) {
        return given()
            .when()
                .contentType(contentType)
                .get(endpoint)
            .then()
                .statusCode(returnCode)
                .extract()
                .response();
    }

    public static Response rulesPutRequest(String contentType, String rule, String endpoint, int returnCode) {
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

    public static Response rulesDeleteRequest(String contentType, String endpoint, int returnCode) {
        return given()
            .when()
                .contentType(contentType)
                .delete(endpoint)
            .then()
                .statusCode(returnCode)
                .extract()
                .response();
    }

    public static Response artifactPostRequest(String artifactId, String contentType, String body, String endpoint, int returnCode) {
        return given()
            .when()
                .header("X-Registry-Artifactid", artifactId)
                .contentType(contentType)
                .body(body)
                .post(endpoint)
            .then()
                .statusCode(returnCode)
                .extract()
                .response();
    }

}
