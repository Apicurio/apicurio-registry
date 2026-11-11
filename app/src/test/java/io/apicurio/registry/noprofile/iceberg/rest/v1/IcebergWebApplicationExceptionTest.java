package io.apicurio.registry.noprofile.iceberg.rest.v1;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * An exception raised on an Iceberg path keeps its status instead of falling back to 500. The paths are
 * served by {@link IcebergBadRequestResource}, which the shared Iceberg profile enables.
 */
@QuarkusTest
@TestProfile(IcebergExperimentalFeaturesProfile.class)
public class IcebergWebApplicationExceptionTest extends AbstractResourceTestBase {

    @Test
    public void webApplicationExceptionFromAResourceMethodKeepsItsStatus() {
        given().when().get("/iceberg/test/bad-request")
                .then()
                .statusCode(400)
                .body("error.code", equalTo(400))
                .body("error.type", equalTo("BadRequestException"))
                .body("error.message", equalTo("Intentional 400 from an Iceberg resource method."));
    }

    @Test
    public void unmatchedMethodKeepsItsStatusAndAllowHeader() {
        // The framework, not a resource method, raises this one: the path has no POST method.
        given().when().post("/iceberg/test/bad-request")
                .then()
                .statusCode(405)
                .header("Allow", containsString("GET"))
                .body("error.code", equalTo(405))
                .body("error.type", equalTo("NotAllowedException"));
    }
}
