package io.apicurio.tests.utils;

import io.restassured.response.Response;

import java.net.URL;

import static io.restassured.RestAssured.given;

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
