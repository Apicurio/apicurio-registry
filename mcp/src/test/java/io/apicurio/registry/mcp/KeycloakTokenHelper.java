package io.apicurio.registry.mcp;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

/**
 * Utility for obtaining OAuth2 access tokens from Keycloak in tests.
 */
final class KeycloakTokenHelper {

    private KeycloakTokenHelper() {
    }

    static String getClientCredentialsToken(String tokenEndpoint, String clientId, String clientSecret) {
        return RestAssured.given()
                .contentType(ContentType.URLENC)
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", clientId)
                .formParam("client_secret", clientSecret)
                .post(tokenEndpoint)
                .then()
                .statusCode(200)
                .extract()
                .path("access_token");
    }
}
