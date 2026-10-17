package io.apicurio.registry.services.http;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(IcebergWebApplicationExceptionTest.BadRequestProfile.class)
public class IcebergWebApplicationExceptionTest {

    public static class BadRequestProfile implements QuarkusTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(IcebergBadRequestResource.class);
        }
    }

    @Test
    void webApplicationExceptionFromIcebergResourceKeepsItsStatus() {
        given().when().get("/apis/iceberg/test/bad-request")
                .then()
                .statusCode(400)
                .body("error.code", equalTo(400))
                .body("error.type", equalTo("BadRequestException"))
                .body("error.message", equalTo("Intentional 400 from an Iceberg resource method."));
    }
}
