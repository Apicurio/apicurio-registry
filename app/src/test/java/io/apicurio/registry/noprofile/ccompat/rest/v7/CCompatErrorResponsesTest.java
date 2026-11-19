package io.apicurio.registry.noprofile.ccompat.rest.v7;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

/**
 * Error responses of the ccompat API must match Confluent Schema Registry (status, error_code, message).
 */
@QuarkusTest
public class CCompatErrorResponsesTest extends AbstractResourceTestBase {

    private static final String SCHEMA_V1 = "{\"schema\":\"{\\\"type\\\":\\\"record\\\",\\\"name\\\":\\\"R\\\","
            + "\\\"fields\\\":[{\\\"name\\\":\\\"x\\\",\\\"type\\\":\\\"int\\\"}]}\"}";
    private static final String SCHEMA_INCOMPATIBLE = "{\"schema\":\"{\\\"type\\\":\\\"record\\\","
            + "\\\"name\\\":\\\"R\\\",\\\"fields\\\":[{\\\"name\\\":\\\"x\\\",\\\"type\\\":\\\"string\\\"}]}\"}";

    private String registerSubject() {
        String subject = "err-" + UUID.randomUUID();
        given().contentType(CT_JSON).body(SCHEMA_V1).post("/ccompat/v7/subjects/{s}/versions", subject).then()
                .statusCode(200);
        return subject;
    }

    @Test
    public void testSubjectSoftDeleted() {
        String subject = registerSubject();
        given().delete("/ccompat/v7/subjects/{s}", subject).then().statusCode(200);
        given().delete("/ccompat/v7/subjects/{s}", subject).then().statusCode(404)
                .body("error_code", equalTo(40404))
                .body("message", equalTo("Subject '" + subject
                        + "' was soft deleted.Set permanent=true to delete permanently"));
    }

    @Test
    public void testSubjectNotSoftDeleted() {
        String subject = registerSubject();
        given().delete("/ccompat/v7/subjects/{s}?permanent=true", subject).then().statusCode(404)
                .body("error_code", equalTo(40405))
                .body("message", equalTo("Subject '" + subject
                        + "' was not deleted first before being permanently deleted"));
    }

    @Test
    public void testVersionSoftDeleted() {
        String subject = registerSubject();
        given().delete("/ccompat/v7/subjects/{s}/versions/1", subject).then().statusCode(200);
        given().delete("/ccompat/v7/subjects/{s}/versions/1", subject).then().statusCode(404)
                .body("error_code", equalTo(40406))
                .body("message", equalTo("Subject '" + subject
                        + "' Version 1 was soft deleted.Set permanent=true to delete permanently"));
    }

    @Test
    public void testVersionNotSoftDeleted() {
        String subject = registerSubject();
        given().delete("/ccompat/v7/subjects/{s}/versions/1?permanent=true", subject).then().statusCode(404)
                .body("error_code", equalTo(40407))
                .body("message", equalTo("Subject '" + subject
                        + "' Version 1 was not deleted first before being permanently deleted"));
    }

    @Test
    public void testIncompatibleSchemaMessage() {
        String subject = registerSubject();
        given().contentType(CT_JSON).body("{\"compatibility\":\"BACKWARD\"}")
                .put("/ccompat/v7/config/{s}", subject).then().statusCode(200);
        given().contentType(CT_JSON).body(SCHEMA_INCOMPATIBLE)
                .post("/ccompat/v7/subjects/{s}/versions", subject).then().statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", startsWith(
                        "Schema being registered is incompatible with an earlier schema for subject \""
                                + subject + "\", details: Incompatible artifact"))
                .body("message", not(containsString("Exception")));
    }
}
