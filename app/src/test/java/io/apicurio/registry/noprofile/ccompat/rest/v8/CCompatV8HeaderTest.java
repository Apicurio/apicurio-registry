package io.apicurio.registry.noprofile.ccompat.rest.v8;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.rest.ContentTypes;
import io.apicurio.registry.ccompat.rest.v8.beans.RegisterSchemaRequest;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Tests for the v8-specific X-Confluent-Accept-Unknown-Properties header.
 * This header enables forward compatibility by ignoring unknown properties in request bodies.
 */
@QuarkusTest
public class CCompatV8HeaderTest extends AbstractResourceTestBase {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testRegisterSchemaWithUnknownPropertiesHeader() throws Exception {
        var subject = TestUtils.generateSubject();
        var schema = "{\"type\" : \"string\"}";
        var schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schema);

        // Register a schema with X-Confluent-Accept-Unknown-Properties header set to true
        given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .header("X-Confluent-Accept-Unknown-Properties", "true")
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v8/subjects/{subject}/versions", subject)
                .then()
                .statusCode(200)
                .body("id", notNullValue());
    }

    @Test
    public void testRegisterSchemaWithoutUnknownPropertiesHeader() throws Exception {
        var subject = TestUtils.generateSubject();
        var schema = "{\"type\" : \"string\"}";
        var schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schema);

        // Register a schema without X-Confluent-Accept-Unknown-Properties header
        given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v8/subjects/{subject}/versions", subject)
                .then()
                .statusCode(200)
                .body("id", notNullValue());
    }

    @Test
    public void testLookupSchemaWithUnknownPropertiesHeader() throws Exception {
        var subject = TestUtils.generateSubject();
        var schema = "{\"type\" : \"string\"}";
        var schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schema);

        // First register a schema
        given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v8/subjects/{subject}/versions", subject)
                .then()
                .statusCode(200);

        // Lookup with X-Confluent-Accept-Unknown-Properties header
        given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .header("X-Confluent-Accept-Unknown-Properties", "true")
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v8/subjects/{subject}", subject)
                .then()
                .statusCode(200)
                .body("schema", notNullValue());
    }

    @Test
    public void testCompatibilityCheckWithUnknownPropertiesHeader() throws Exception {
        var subject = TestUtils.generateSubject();
        var schema = "{\"type\": \"record\", \"name\": \"Test\", \"fields\": [{\"name\": \"f1\", \"type\": \"string\"}]}";
        var schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schema);
        schemaContent.setSchemaType("AVRO");

        // Register initial schema
        given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v8/subjects/{subject}/versions", subject)
                .then()
                .statusCode(200);

        // Check compatibility with header
        var newSchema = "{\"type\": \"record\", \"name\": \"Test\", \"fields\": [{\"name\": \"f1\", \"type\": \"string\"}, {\"name\": \"f2\", \"type\": \"string\", \"default\": \"test\"}]}";
        var newSchemaContent = new RegisterSchemaRequest();
        newSchemaContent.setSchema(newSchema);
        newSchemaContent.setSchemaType("AVRO");

        given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .header("X-Confluent-Accept-Unknown-Properties", "true")
                .body(objectMapper.writeValueAsString(newSchemaContent))
                .post("/ccompat/v8/compatibility/subjects/{subject}/versions/1", subject)
                .then()
                .statusCode(200)
                .body("is_compatible", notNullValue());
    }
}
