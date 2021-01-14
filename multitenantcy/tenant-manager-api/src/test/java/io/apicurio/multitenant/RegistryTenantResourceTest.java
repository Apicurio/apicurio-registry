package io.apicurio.multitenant;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class RegistryTenantResourceTest {

    @Test
    public void testListTenants() {
        given()
          .when().get("/api/tenant-manager/v1/tenants")
          .then()
             .statusCode(200)
             .body(is("[]"));
    }

}