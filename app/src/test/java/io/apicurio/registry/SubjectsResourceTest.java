package io.apicurio.registry;

import io.apicurio.registry.ccompat.rest.RestConstants;
import io.apicurio.registry.util.H2DatabaseService;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;

@QuarkusTest
public class SubjectsResourceTest {

    private static H2DatabaseService h2ds = new H2DatabaseService();

    @BeforeAll
    public static void beforeAll() throws Exception {
        h2ds.start();
    }

    @AfterAll
    public static void afterAll() {
        h2ds.stop();
    }

    @Test    
    public void testListSubjectsEndpoint() {
        given()
            .when().contentType(RestConstants.JSON).get("/confluent/subjects")
            .then()
            .statusCode(200)
            .body(anything());
    }

}
