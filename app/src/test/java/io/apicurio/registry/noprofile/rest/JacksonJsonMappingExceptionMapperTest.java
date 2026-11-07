package io.apicurio.registry.noprofile.rest;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@QuarkusTest
public class JacksonJsonMappingExceptionMapperTest extends AbstractResourceTestBase {

    @Test
    public void testMalformedCCompatBodyUsesConfluentErrorShape() {
        given().contentType(CT_JSON)
                .body("{\"schema\":{\"not\":\"a string\"}}")
                .post("/ccompat/v7/subjects/json-mapping-test/versions")
                .then().statusCode(400)
                .body("error_code", equalTo(400))
                .body("message", containsString("Cannot deserialize value of type"))
                .body(not(containsString("\"detail\"")))
                .body(not(containsString("\"title\"")))
                .body(not(containsString("\"status\"")))
                .body(not(containsString("BadRequestException")));
    }

    @Test
    public void testMalformedV2BodyUsesLegacyErrorShape() {
        given().contentType(CT_JSON)
                .body("{\"labels\":{\"not\":\"an array\"}}")
                .put("/registry/v2/groups/json-mapping-test/artifacts/json-mapping-test/meta")
                .then().statusCode(400)
                .body("error_code", equalTo(400))
                .body("message", containsString("Cannot deserialize value of type"))
                .body("name", equalTo("BadRequestException"))
                .body(not(containsString("\"title\"")))
                .body(not(containsString("\"status\"")));
    }
}
