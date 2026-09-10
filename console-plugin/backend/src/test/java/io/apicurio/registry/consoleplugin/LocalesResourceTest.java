package io.apicurio.registry.consoleplugin;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class LocalesResourceTest {

    @Test
    void testValidLocale() {
        given()
                .queryParam("lng", "en")
                .queryParam("ns", "plugin__apicurio-registry")
                .when().get("/locales/resource.json")
                .then()
                .statusCode(200)
                .body("test", is("value"));
    }

    @Test
    void testMissingParams() {
        given()
                .when().get("/locales/resource.json")
                .then()
                .statusCode(400);
    }

    @Test
    void testMissingLanguage() {
        given()
                .queryParam("ns", "plugin__apicurio-registry")
                .when().get("/locales/resource.json")
                .then()
                .statusCode(400);
    }

    @Test
    void testPathTraversal() {
        given()
                .queryParam("lng", "../../../etc")
                .queryParam("ns", "passwd")
                .when().get("/locales/resource.json")
                .then()
                .statusCode(400);
    }

    @Test
    void testPathTraversalInNamespace() {
        given()
                .queryParam("lng", "en")
                .queryParam("ns", "../../secret")
                .when().get("/locales/resource.json")
                .then()
                .statusCode(400);
    }

    @Test
    void testNonExistentLocale() {
        given()
                .queryParam("lng", "xx")
                .queryParam("ns", "nonexistent")
                .when().get("/locales/resource.json")
                .then()
                .statusCode(404);
    }
}
