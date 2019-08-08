package io.apicurio.registry;

import io.apicurio.registry.ccompat.rest.RestConstants;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;

@QuarkusTest
public class SubjectsResourceTest {

    @Test    
    public void testListSubjectsEndpoint() {
        given()
            .when().contentType(RestConstants.JSON).get("/confluent/subjects")
            .then()
            .statusCode(200)
            .body(anything());
    }

}
