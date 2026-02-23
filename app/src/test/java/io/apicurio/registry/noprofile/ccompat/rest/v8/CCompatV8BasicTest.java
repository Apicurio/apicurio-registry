package io.apicurio.registry.noprofile.ccompat.rest.v8;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.rest.ContentTypes;
import io.apicurio.registry.ccompat.rest.v8.beans.ConfigUpdateRequest;
import io.apicurio.registry.ccompat.rest.v8.beans.ModeUpdateRequest;
import io.apicurio.registry.ccompat.rest.v8.beans.RegisterSchemaRequest;
import io.apicurio.registry.ccompat.rest.v8.beans.SchemaId;
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
 * Basic tests for the ccompat v8 API.
 * Verifies that v8 endpoints work correctly by delegating to v7 implementations.
 */
@QuarkusTest
public class CCompatV8BasicTest extends AbstractResourceTestBase {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== Basic CRUD Operations ==========

    @Test
    public void testRegisterAndGetSchema() throws Exception {
        var subject = TestUtils.generateSubject();
        var schema = "{\"type\" : \"string\"}";
        var schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schema);

        // Register a schema
        SchemaId schemaId = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v8/subjects/{subject}/versions", subject)
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .extract()
                .as(SchemaId.class);

        // Get the schema version
        given().when()
                .get("/ccompat/v8/subjects/{subject}/versions/1", subject)
                .then()
                .statusCode(200)
                .body("subject", equalTo(subject))
                .body("version", equalTo(1))
                .body("schema", notNullValue());

        // Get the schema by ID
        given().when()
                .get("/ccompat/v8/schemas/ids/{id}", schemaId.getId())
                .then()
                .statusCode(200)
                .body("schema", notNullValue());
    }

    @Test
    public void testGetSubjects() throws Exception {
        var subject = TestUtils.generateSubject();
        var schema = "{\"type\" : \"string\"}";
        var schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schema);

        // Register a schema
        given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v8/subjects/{subject}/versions", subject)
                .then()
                .statusCode(200);

        // Get subjects
        given().when()
                .get("/ccompat/v8/subjects")
                .then()
                .statusCode(200);
    }

    @Test
    public void testGetSchemaTypes() {
        given().when()
                .get("/ccompat/v8/schemas/types")
                .then()
                .statusCode(200)
                .body("$", hasSize(3));  // JSON, PROTOBUF, AVRO
    }

    // ========== Mode API Tests ==========

    @Test
    public void testGetGlobalModeDefault() {
        // Default mode should be READWRITE
        given().when()
                .get("/ccompat/v8/mode")
                .then()
                .statusCode(200)
                .body("mode", equalTo("READWRITE"));
    }

    @Test
    public void testUpdateGlobalMode() {
        ModeUpdateRequest request = new ModeUpdateRequest();
        request.setMode(ModeUpdateRequest.Mode.READONLY);

        // Set global mode to READONLY
        given().when()
                .contentType(ContentTypes.JSON)
                .body(request)
                .put("/ccompat/v8/mode")
                .then()
                .statusCode(200)
                .body("mode", equalTo("READONLY"));

        // Verify the mode was persisted
        given().when()
                .get("/ccompat/v8/mode")
                .then()
                .statusCode(200)
                .body("mode", equalTo("READONLY"));

        // Reset back to READWRITE
        request.setMode(ModeUpdateRequest.Mode.READWRITE);
        given().when()
                .contentType(ContentTypes.JSON)
                .body(request)
                .put("/ccompat/v8/mode")
                .then()
                .statusCode(200)
                .body("mode", equalTo("READWRITE"));
    }

    // ========== Config API Tests ==========

    @Test
    public void testGetAndUpdateGlobalConfig() {
        // Get global config
        given().when()
                .get("/ccompat/v8/config")
                .then()
                .statusCode(200);

        // Update global config
        ConfigUpdateRequest updateRequest = new ConfigUpdateRequest();
        updateRequest.setCompatibility(CompatibilityLevel.BACKWARD.name());

        given().when()
                .contentType(ContentTypes.JSON)
                .body(updateRequest)
                .put("/ccompat/v8/config")
                .then()
                .statusCode(200)
                .body("compatibilityLevel", equalTo(CompatibilityLevel.BACKWARD.name()));

        // Delete global config
        given().when()
                .delete("/ccompat/v8/config")
                .then()
                .statusCode(200);
    }

    // ========== Exporter API Tests ==========

    @Test
    public void testGetExportersReturnsEmptyList() {
        // GET /exporters should return an empty list, not 404
        given().when()
                .get("/ccompat/v8/exporters")
                .then()
                .statusCode(200)
                .body("$", empty());
    }

    // ========== Contexts API Tests ==========

    @Test
    public void testGetContexts() {
        given().when()
                .get("/ccompat/v8/contexts")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0]", equalTo(":.:")); // Default context
    }

    // ========== Compatibility Check Tests ==========

    @Test
    public void testCompatibilityCheck() throws Exception {
        // Create a subject with a schema
        var subject = TestUtils.generateSubject();
        var schema = "{\"type\": \"record\", \"name\": \"Test\", \"fields\": [{\"name\": \"f1\", \"type\": \"string\"}]}";
        var schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schema);
        schemaContent.setSchemaType("AVRO");

        given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v8/subjects/{subject}/versions", subject)
                .then()
                .statusCode(200);

        // Test compatibility check
        var newSchema = "{\"type\": \"record\", \"name\": \"Test\", \"fields\": [{\"name\": \"f1\", \"type\": \"string\"}, {\"name\": \"f2\", \"type\": \"string\", \"default\": \"default\"}]}";
        var newSchemaContent = new RegisterSchemaRequest();
        newSchemaContent.setSchema(newSchema);
        newSchemaContent.setSchemaType("AVRO");

        given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(newSchemaContent))
                .post("/ccompat/v8/compatibility/subjects/{subject}/versions/1", subject)
                .then()
                .statusCode(200)
                .body("is_compatible", notNullValue());
    }

    // ========== Pagination Tests ==========

    @Test
    public void testSubjectsPagination() throws Exception {
        // Create a subject
        var subject = TestUtils.generateSubject();
        var schema = "{\"type\" : \"string\"}";
        var schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schema);

        given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v8/subjects/{subject}/versions", subject)
                .then()
                .statusCode(200);

        // Test with limit
        given().when()
                .queryParam("limit", 2)
                .get("/ccompat/v8/subjects")
                .then()
                .statusCode(200);

        // Test with offset
        given().when()
                .queryParam("offset", 0)
                .get("/ccompat/v8/subjects")
                .then()
                .statusCode(200);
    }

    // ========== Schema Lookup Tests ==========

    @Test
    public void testLookupSchemaByContent() throws Exception {
        var subject = TestUtils.generateSubject();
        var schema = "{\"type\" : \"string\"}";
        var schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schema);

        // Register a schema
        given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v8/subjects/{subject}/versions", subject)
                .then()
                .statusCode(200);

        // Lookup schema by content
        given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v8/subjects/{subject}", subject)
                .then()
                .statusCode(200)
                .body("subject", equalTo(subject))
                .body("schema", notNullValue());
    }

    @Test
    public void testGetSchemasBySchemaId() throws Exception {
        var subject = TestUtils.generateSubject();
        var schema = "{\"type\" : \"string\"}";
        var schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schema);

        // Register a schema
        SchemaId schemaId = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v8/subjects/{subject}/versions", subject)
                .then()
                .statusCode(200)
                .extract()
                .as(SchemaId.class);

        // Get subjects by schema ID
        given().when()
                .get("/ccompat/v8/schemas/ids/{id}/subjects", schemaId.getId())
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)));
    }
}
