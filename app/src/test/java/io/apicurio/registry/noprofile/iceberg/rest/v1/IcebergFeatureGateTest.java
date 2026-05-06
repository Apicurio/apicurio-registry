package io.apicurio.registry.noprofile.iceberg.rest.v1;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Tests that Iceberg endpoints are blocked when the experimental features gate is disabled (default).
 */
@QuarkusTest
public class IcebergFeatureGateTest extends AbstractResourceTestBase {

    @Test
    public void testGetConfigBlockedWhenExperimentalDisabled() {
        given()
                .when()
                .contentType(CT_JSON)
                .get("/iceberg/v1/config")
                .then()
                .statusCode(404);
    }

    @Test
    public void testListNamespacesBlockedWhenExperimentalDisabled() {
        given()
                .when()
                .contentType(CT_JSON)
                .get("/iceberg/v1/default/namespaces")
                .then()
                .statusCode(404);
    }

    @Test
    public void testListTablesBlockedWhenExperimentalDisabled() {
        given()
                .when()
                .contentType(CT_JSON)
                .get("/iceberg/v1/default/namespaces/test/tables")
                .then()
                .statusCode(404);
    }
}
