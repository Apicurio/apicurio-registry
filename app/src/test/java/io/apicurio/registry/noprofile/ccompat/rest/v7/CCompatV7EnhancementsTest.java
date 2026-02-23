package io.apicurio.registry.noprofile.ccompat.rest.v7;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.rest.ContentTypes;
import io.apicurio.registry.ccompat.rest.v7.beans.ConfigUpdateRequest;
import io.apicurio.registry.ccompat.rest.v7.beans.ModeUpdateRequest;
import io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest;
import io.apicurio.registry.ccompat.rest.v7.beans.SchemaId;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * Tests for the ccompat v7 API enhancements:
 * - Mode API (GET/PUT global and subject mode)
 * - Exporter API (empty list)
 * - Schemas endpoints (GET /schemas, GET /schemas/ids/{id}/subjects)
 * - Pagination (offset, limit, deletedOnly)
 * - Config DELETE endpoint
 * - Normalize parameter for compatibility
 */
@QuarkusTest
public class CCompatV7EnhancementsTest extends AbstractResourceTestBase {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== Mode API Tests ==========

    @Test
    public void testGetGlobalModeDefault() {
        // Default mode should be READWRITE
        given().when().get("/ccompat/v7/mode").then().statusCode(200).body("mode", equalTo("READWRITE"));
    }

    @Test
    public void testUpdateGlobalMode() {
        ModeUpdateRequest request = new ModeUpdateRequest();
        request.setMode(ModeUpdateRequest.Mode.READONLY);

        // Set global mode to READONLY
        given().when().contentType(ContentTypes.JSON).body(request).put("/ccompat/v7/mode").then()
                .statusCode(200).body("mode", equalTo("READONLY"));

        // Verify the mode was persisted
        given().when().get("/ccompat/v7/mode").then().statusCode(200).body("mode", equalTo("READONLY"));

        // Reset back to READWRITE
        request.setMode(ModeUpdateRequest.Mode.READWRITE);
        given().when().contentType(ContentTypes.JSON).body(request).put("/ccompat/v7/mode").then()
                .statusCode(200).body("mode", equalTo("READWRITE"));
    }

    @Test
    public void testSubjectMode() throws Exception {
        // Create a subject first
        var subject = TestUtils.generateSubject();
        var schema = "{\"type\" : \"string\"}";
        var schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schema);

        given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200);

        // Get subject mode (should fall back to global READWRITE)
        given().when().get("/ccompat/v7/mode/{subject}", subject).then().statusCode(200)
                .body("mode", equalTo("READWRITE"));

        // Set subject-specific mode
        ModeUpdateRequest request = new ModeUpdateRequest();
        request.setMode(ModeUpdateRequest.Mode.READONLY);
        given().when().contentType(ContentTypes.JSON).body(request)
                .put("/ccompat/v7/mode/{subject}", subject).then().statusCode(200)
                .body("mode", equalTo("READONLY"));

        // Verify subject mode is now READONLY
        given().when().get("/ccompat/v7/mode/{subject}", subject).then().statusCode(200)
                .body("mode", equalTo("READONLY"));

        // Global mode should still be READWRITE
        given().when().get("/ccompat/v7/mode").then().statusCode(200).body("mode", equalTo("READWRITE"));
    }

    // ========== Exporter API Tests ==========

    @Test
    public void testGetExportersReturnsEmptyList() {
        // GET /exporters should return an empty list, not 404
        given().when().get("/ccompat/v7/exporters").then().statusCode(200).body("$", empty());
    }

    // ========== Config DELETE Tests ==========

    @Test
    public void testDeleteGlobalConfig() {
        // First set a global config
        ConfigUpdateRequest backward = new ConfigUpdateRequest();
        backward.setCompatibility(CompatibilityLevel.BACKWARD.name());
        given().when().contentType(ContentTypes.JSON).body(backward).put("/ccompat/v7/config").then()
                .statusCode(200);

        // Verify it was set
        given().when().get("/ccompat/v7/config").then().statusCode(200).body("compatibilityLevel",
                equalTo(CompatibilityLevel.BACKWARD.name()));

        // Delete global config
        given().when().delete("/ccompat/v7/config").then().statusCode(200).body("compatibilityLevel",
                notNullValue());

        // After deletion, should return default (NONE or whatever the default is)
        given().when().get("/ccompat/v7/config").then().statusCode(200);
    }

    // ========== Schemas Endpoints Tests ==========

    @Test
    public void testGetSchemas() throws Exception {
        // Create a few schemas first
        for (int i = 0; i < 3; i++) {
            var subject = TestUtils.generateSubject();
            var schema = "{\"type\" : \"string\"}";
            var schemaContent = new RegisterSchemaRequest();
            schemaContent.setSchema(schema);

            given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                    .body(objectMapper.writeValueAsString(schemaContent))
                    .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200);
        }

        // GET /schemas should return a list
        given().when().get("/ccompat/v7/schemas").then().statusCode(200);
    }

    @Test
    public void testGetSubjectsBySchemaId() throws Exception {
        // Create a schema
        var subject = TestUtils.generateSubject();
        var schema = "{\"type\" : \"string\"}";
        var schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schema);

        SchemaId schemaId = given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200).extract()
                .as(SchemaId.class);

        // GET /schemas/ids/{id}/subjects should return the subject
        given().when().get("/ccompat/v7/schemas/ids/{id}/subjects", schemaId.getId()).then().statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)));
    }

    // ========== Pagination Tests ==========

    @Test
    public void testSubjectsPagination() throws Exception {
        // Create 5 subjects
        for (int i = 0; i < 5; i++) {
            var subject = "pagination-test-" + TestUtils.generateSubject();
            var schema = "{\"type\" : \"string\"}";
            var schemaContent = new RegisterSchemaRequest();
            schemaContent.setSchema(schema);

            given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                    .body(objectMapper.writeValueAsString(schemaContent))
                    .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200);
        }

        // Test with limit
        given().when().queryParam("limit", 2).get("/ccompat/v7/subjects").then().statusCode(200);

        // Test with offset
        given().when().queryParam("offset", 1).get("/ccompat/v7/subjects").then().statusCode(200);

        // Test with both offset and limit
        given().when().queryParam("offset", 1).queryParam("limit", 2).get("/ccompat/v7/subjects").then()
                .statusCode(200);
    }

    @Test
    public void testSubjectVersionsPagination() throws Exception {
        // Create a subject with multiple versions
        var subject = TestUtils.generateSubject();

        // Create first version
        var schema1 = "{\"type\" : \"string\"}";
        var schemaContent1 = new RegisterSchemaRequest();
        schemaContent1.setSchema(schema1);
        given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent1))
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200);

        // Create second version (different schema)
        var schema2 = "{\"type\": \"record\", \"name\": \"Test\", \"fields\": [{\"name\": \"f1\", \"type\": \"string\"}]}";
        var schemaContent2 = new RegisterSchemaRequest();
        schemaContent2.setSchema(schema2);
        schemaContent2.setSchemaType("AVRO");
        given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent2))
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200);

        // Test versions endpoint with pagination
        given().when().queryParam("offset", 0).queryParam("limit", 1)
                .get("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200);
    }

    // ========== Normalize Parameter Tests ==========

    @Test
    public void testCompatibilityWithNormalizeParameter() throws Exception {
        // Create a subject with a schema
        var subject = TestUtils.generateSubject();
        var schema = "{\"type\": \"record\", \"name\": \"Test\", \"fields\": [{\"name\": \"f1\", \"type\": \"string\"}]}";
        var schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schema);
        schemaContent.setSchemaType("AVRO");

        given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200);

        // Test compatibility check with normalize=true
        var newSchema = "{\"type\": \"record\", \"name\": \"Test\", \"fields\": [{\"name\": \"f1\", \"type\": \"string\"}, {\"name\": \"f2\", \"type\": \"string\", \"default\": \"default\"}]}";
        var newSchemaContent = new RegisterSchemaRequest();
        newSchemaContent.setSchema(newSchema);
        newSchemaContent.setSchemaType("AVRO");

        // Check compatibility against specific version with normalize
        given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .queryParam("normalize", true)
                .body(objectMapper.writeValueAsString(newSchemaContent))
                .post("/ccompat/v7/compatibility/subjects/{subject}/versions/1", subject).then()
                .statusCode(200).body("is_compatible", notNullValue());

        // Check compatibility against all versions with normalize
        given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .queryParam("normalize", true)
                .body(objectMapper.writeValueAsString(newSchemaContent))
                .post("/ccompat/v7/compatibility/subjects/{subject}/versions", subject).then()
                .statusCode(200).body("is_compatible", notNullValue());
    }
}
