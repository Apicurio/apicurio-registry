package io.apicurio.registry.consoleplugin;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class RegistryProxyResourceTest {

    @Test
    void testProxyGetReturns502WhenUpstreamUnreachable() {
        given()
                .when().get("/proxy/apis/registry/v3/system/info")
                .then()
                .statusCode(502);
    }

    @Test
    void testProxyPostReturns502WhenUpstreamUnreachable() {
        given()
                .contentType("application/json")
                .body("{}")
                .when().post("/proxy/apis/registry/v3/groups/default/artifacts")
                .then()
                .statusCode(502);
    }

    @Test
    void testProxyDeleteReturns502WhenUpstreamUnreachable() {
        given()
                .when().delete("/proxy/apis/registry/v3/groups/default/artifacts/test")
                .then()
                .statusCode(502);
    }
}
