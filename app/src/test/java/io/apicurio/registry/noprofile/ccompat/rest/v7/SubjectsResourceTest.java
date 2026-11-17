package io.apicurio.registry.noprofile.ccompat.rest.v7;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class SubjectsResourceTest extends AbstractResourceTestBase {

    private static final String SCHEMA = "{\"schema\":\"{\\\"type\\\":\\\"record\\\",\\\"name\\\":\\\"R\\\","
            + "\\\"fields\\\":[{\\\"name\\\":\\\"x\\\",\\\"type\\\":\\\"int\\\"}]}\"}";

    @Test
    public void testListSubjectsEndpoint() {
        given().when().contentType(CT_JSON).get("/ccompat/v7/subjects").then().statusCode(200)
                .body(anything());
    }

    @Test
    public void testInvalidOffsetIsNormalized() {
        String subject = "offset-normalize-" + System.nanoTime();
        given().when().contentType(CT_JSON).body(SCHEMA)
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200);

        List<String> firstPage = given().when().get("/ccompat/v7/subjects?offset=0").then()
                .statusCode(200).body("$", hasItem(subject)).extract().jsonPath().getList("$");
        List<String> negative = given().when().get("/ccompat/v7/subjects?offset=-1").then()
                .statusCode(200).extract().jsonPath().getList("$");
        assertEquals(firstPage, negative);
        given().when().get("/ccompat/v7/subjects?offset=2147483648").then().statusCode(200)
                .body("$", empty());

        int schemasFirstPage = given().when().get("/ccompat/v7/schemas?offset=0").then()
                .statusCode(200).extract().jsonPath().getList("$").size();
        given().when().get("/ccompat/v7/schemas?offset=-1").then().statusCode(200)
                .body("size()", equalTo(schemasFirstPage));
        given().when().get("/ccompat/v7/schemas?offset=2147483648").then().statusCode(200)
                .body("$", empty());
        given().when().get("/ccompat/v8/schemas?offset=-1").then().statusCode(200)
                .body("size()", equalTo(schemasFirstPage));
    }
}
