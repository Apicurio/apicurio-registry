package io.apicurio.registry.noprofile.ccompat.rest.v7;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.rest.ContentTypes;
import io.apicurio.registry.ccompat.rest.v7.beans.ConfigUpdateRequest;
import io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest;
import io.apicurio.registry.ccompat.rest.v7.beans.SchemaId;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

@QuarkusTest
public class ConfluentCompatibilityConfigTest extends AbstractResourceTestBase {

    @Test
    public void testGlobalConfig() {
        ConfigUpdateRequest backward = new ConfigUpdateRequest();
        backward.setCompatibility(CompatibilityLevel.BACKWARD.name());
        given().when().contentType(ContentTypes.JSON).body(backward).put("/ccompat/v7/config").then()
                .statusCode(200).body("compatibility", equalTo(CompatibilityLevel.BACKWARD.toString()));

        given().when().get("/ccompat/v7/config").then().statusCode(200).body("compatibilityLevel",
                equalTo(CompatibilityLevel.BACKWARD.toString()));
    }

    @Test
    public void testSubjectConfig() throws Exception {
        ConfigUpdateRequest backward = new ConfigUpdateRequest();
        backward.setCompatibility(CompatibilityLevel.BACKWARD.name());

        var subject = TestUtils.generateSubject();
        var schema = "{\"type\" : \"string\"}";

        var objectMapper = new ObjectMapper();

        var schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schema);

        given().log().all().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200).extract()
                .as(SchemaId.class);

        given().when().contentType(ContentTypes.JSON).body(backward)
                .put("/ccompat/v7/config/{subject}", subject).then().statusCode(200)
                .body("compatibility", equalTo(CompatibilityLevel.BACKWARD.toString()));

        given().when().get("/ccompat/v7/config/{subject}", subject).then().statusCode(200)
                .body("compatibilityLevel", equalTo(CompatibilityLevel.BACKWARD.toString()));
    }

    @Test
    public void testDefaultToGlobalConfig() throws Exception {
        ConfigUpdateRequest backward = new ConfigUpdateRequest();
        backward.setCompatibility(CompatibilityLevel.BACKWARD.name());

        // Set global config to BACKWARD
        given().when().contentType(ContentTypes.JSON).body(backward).put("/ccompat/v7/config").then()
                .statusCode(200).body("compatibility", equalTo(CompatibilityLevel.BACKWARD.toString()));

        // Create a subject
        var subject = TestUtils.generateSubject();
        var schema = "{\"type\" : \"string\"}";
        var objectMapper = new ObjectMapper();
        var schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schema);
        given().log().all().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200).extract()
                .as(SchemaId.class);

        // Query the subject's config with defaultToGlobal=true (should be BACKWARD)
        given().when().queryParam("defaultToGlobal", true).get("/ccompat/v7/config/{subject}", subject).then()
                .statusCode(200).body("compatibilityLevel", equalTo(CompatibilityLevel.BACKWARD.toString()));

        // Query the subject's config with defaultToGlobal=false (should be NONE)
        given().when().queryParam("defaultToGlobal", false).get("/ccompat/v7/config/{subject}", subject)
                .then().statusCode(200)
                .body("compatibilityLevel", equalTo(CompatibilityLevel.NONE.toString()));

        // Query the subject's config with defaultToGlobal not set at all (should be NONE)
        given().when().get("/ccompat/v7/config/{subject}", subject).then().statusCode(200)
                .body("compatibilityLevel", equalTo(CompatibilityLevel.NONE.toString()));
    }

    @Test
    public void testDeleteSubjectConfig() throws Exception {
        ConfigUpdateRequest backward = new ConfigUpdateRequest();
        backward.setCompatibility(CompatibilityLevel.BACKWARD.name());

        var subject = TestUtils.generateSubject();
        var schema = "{\"type\" : \"string\"}";
        var objectMapper = new ObjectMapper();
        var schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schema);
        given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200).extract()
                .as(SchemaId.class);

        // Configure subject compatibility to BACKWARD
        given().when().contentType(ContentTypes.JSON).body(backward)
                .put("/ccompat/v7/config/{subject}", subject).then().statusCode(200)
                .body("compatibility", equalTo(CompatibilityLevel.BACKWARD.toString()));

        // Delete subject compatibility
        given().when().delete("/ccompat/v7/config/{subject}", subject).then().statusCode(200)
                .body("compatibilityLevel", equalTo(CompatibilityLevel.BACKWARD.toString()));

        // Deleting again should fail with 404 and error_code 40408 (SUBJECT_COMPATIBILITY_NOT_CONFIGURED)
        given().when().delete("/ccompat/v7/config/{subject}", subject).then().statusCode(404)
                .body("error_code", equalTo(40408));
    }

    @Test
    public void testDeleteSubjectConfigNone() throws Exception {
        // Set global config to BACKWARD
        ConfigUpdateRequest backward = new ConfigUpdateRequest();
        backward.setCompatibility(CompatibilityLevel.BACKWARD.name());
        given().when().contentType(ContentTypes.JSON).body(backward).put("/ccompat/v7/config").then()
                .statusCode(200).body("compatibility", equalTo(CompatibilityLevel.BACKWARD.toString()));

        var subject = TestUtils.generateSubject();
        var schema = "{\"type\" : \"string\"}";
        var objectMapper = new ObjectMapper();
        var schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schema);
        given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200).extract()
                .as(SchemaId.class);

        // Configure subject compatibility explicitly to NONE
        ConfigUpdateRequest none = new ConfigUpdateRequest();
        none.setCompatibility(CompatibilityLevel.NONE.name());
        given().when().contentType(ContentTypes.JSON).body(none)
                .put("/ccompat/v7/config/{subject}", subject).then().statusCode(200)
                .body("compatibility", equalTo(CompatibilityLevel.NONE.toString()));

        // Verify subject config is NONE
        given().when().get("/ccompat/v7/config/{subject}", subject).then().statusCode(200)
                .body("compatibilityLevel", equalTo(CompatibilityLevel.NONE.toString()));

        // Delete subject compatibility: this must delete the NONE rule and return 200 with NONE
        given().when().delete("/ccompat/v7/config/{subject}", subject).then().statusCode(200)
                .body("compatibilityLevel", equalTo(CompatibilityLevel.NONE.toString()));

        // Verify it reverted to global default (BACKWARD) when defaultToGlobal=true
        given().when().queryParam("defaultToGlobal", true).get("/ccompat/v7/config/{subject}", subject).then()
                .statusCode(200).body("compatibilityLevel", equalTo(CompatibilityLevel.BACKWARD.toString()));

        // Deleting again should fail with 404 and error_code 40408 (SUBJECT_COMPATIBILITY_NOT_CONFIGURED)
        given().when().delete("/ccompat/v7/config/{subject}", subject).then().statusCode(404)
                .body("error_code", equalTo(40408));
    }

    @Test
    public void testDeleteSubjectConfigNonExistentSubject() {
        var nonExistentSubject = TestUtils.generateSubject();
        given().when().delete("/ccompat/v7/config/{subject}", nonExistentSubject).then().statusCode(404)
                .body("error_code", equalTo(40401));
    }
}
