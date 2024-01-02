package io.apicurio.registry.noprofile.ccompat.rest.v7;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class SubjectsResourceTest extends AbstractResourceTestBase {

    @Test
    public void testListSubjectsEndpoint() {
        given()
            .when().contentType(CT_JSON).get("/ccompat/v7/subjects")
            .then()
            .statusCode(200)
            .body(anything());
    }

}
