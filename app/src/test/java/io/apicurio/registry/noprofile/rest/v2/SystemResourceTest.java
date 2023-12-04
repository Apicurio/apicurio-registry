package io.apicurio.registry.noprofile.rest.v2;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;


@QuarkusTest
public class SystemResourceTest extends AbstractResourceTestBase {

    @Test
    public void testSystemInformation() {
        given()
            .when()
                .contentType(CT_JSON)
                .get("/registry/v2/system/info")
            .then()
                .statusCode(200)
                .body("name", notNullValue())
                .body("description", equalTo("High performance, runtime registry for schemas and API designs."))
                .body("version", notNullValue())
                .body("builtOn", notNullValue())
                ;
    }

}
