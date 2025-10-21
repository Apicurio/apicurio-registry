package io.apicurio.registry.noprofile.ccompat.rest.v7;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Tests for the format query parameter in the Confluent Compatibility API.
 */
@QuarkusTest
public class FormatParameterTest extends AbstractResourceTestBase {

    private static final String AVRO_SCHEMA_WITH_REFERENCE = "{\n"
            + "  \"type\": \"record\",\n" + "  \"name\": \"TestRecord\",\n"
            + "  \"namespace\": \"com.example\",\n" + "  \"fields\": [\n" + "    {\n"
            + "      \"name\": \"user\",\n" + "      \"type\": \"User\"\n" + "    }\n" + "  ]\n"
            + "}";

    private static final String AVRO_USER_SCHEMA = "{\n" + "  \"type\": \"record\",\n"
            + "  \"name\": \"User\",\n" + "  \"namespace\": \"com.example\",\n" + "  \"fields\": [\n"
            + "    {\n" + "      \"name\": \"name\",\n" + "      \"type\": \"string\"\n" + "    },\n"
            + "    {\n" + "      \"name\": \"age\",\n" + "      \"type\": \"int\"\n" + "    }\n"
            + "  ]\n" + "}";

    private static final String AVRO_SIMPLE_SCHEMA = "{\n" + "  \"type\": \"record\",\n"
            + "  \"name\": \"Simple\",\n" + "  \"namespace\": \"com.example\",\n" + "  \"fields\": [\n"
            + "    {\n" + "      \"name\": \"field1\",\n" + "      \"type\": \"string\"\n" + "    }\n"
            + "  ]\n" + "}";

    private static final String PROTOBUF_SCHEMA = "syntax = \"proto3\";\n" + "package test;\n" + "\n"
            + "message TestMessage {\n" + "  string name = 1;\n" + "  int32 age = 2;\n" + "}";

    private static final String PROTOBUF_SCHEMA_WITH_EXTENSIONS = "syntax = \"proto2\";\n"
            + "package test;\n" + "\n" + "message TestMessage {\n" + "  optional string name = 1;\n"
            + "  optional int32 age = 2;\n" + "}\n" + "\n" + "extend TestMessage {\n"
            + "  optional string extension_field = 100;\n" + "}";

    private static final String JSON_SCHEMA = "{\n" + "  \"type\": \"object\",\n" + "  \"properties\": {\n"
            + "    \"name\": { \"type\": \"string\" },\n" + "    \"age\": { \"type\": \"integer\" }\n"
            + "  }\n" + "}";

    /**
     * Test that AVRO schemas can be retrieved with the "resolved" format.
     */
    @Test
    public void testAvroResolvedFormat() throws Exception {
        String subject = "avro-resolved-test";

        // Register the User schema first
        String userSubject = "avro-user-schema";
        given().when().contentType(ContentType.JSON).body("{ \"schema\": \"" + escapeJson(AVRO_USER_SCHEMA)
                + "\", \"schemaType\": \"AVRO\" }").post("/ccompat/v7/subjects/{subject}/versions", userSubject)
                .then().statusCode(200);

        // Register the schema with reference to User
        String requestBody = "{\n" + "  \"schema\": \"" + escapeJson(AVRO_SCHEMA_WITH_REFERENCE) + "\",\n"
                + "  \"schemaType\": \"AVRO\",\n" + "  \"references\": [\n" + "    {\n"
                + "      \"name\": \"com.example.User\",\n" + "      \"subject\": \"" + userSubject + "\",\n"
                + "      \"version\": 1\n" + "    }\n" + "  ]\n" + "}";

        String schemaIdResponse = given().when().contentType(ContentType.JSON).body(requestBody)
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200).body("id",
                        notNullValue()).extract().path("id").toString();

        // Get the schema without format parameter - should return the original schema
        given().when().get("/ccompat/v7/schemas/ids/{id}", schemaIdResponse).then().statusCode(200)
                .body("schema", containsString("User"));

        // Get the schema with format=resolved - should inline the User schema
        given().when().queryParam("format", "resolved").get("/ccompat/v7/schemas/ids/{id}", schemaIdResponse)
                .then().statusCode(200).body("schema", containsString("User"))
                .body("schema", containsString("name")).body("schema", containsString("age"));
    }

    /**
     * Test that providing an invalid format for AVRO returns an error.
     */
    @Test
    public void testAvroInvalidFormat() throws Exception {
        String subject = "avro-invalid-format-test";

        // Register a simple AVRO schema
        String requestBody = "{ \"schema\": \"" + escapeJson(AVRO_SIMPLE_SCHEMA)
                + "\", \"schemaType\": \"AVRO\" }";

        String schemaIdResponse = given().when().contentType(ContentType.JSON).body(requestBody)
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200).body("id",
                        notNullValue()).extract().path("id").toString();

        // Try to get the schema with an invalid format
        given().when().queryParam("format", "invalid_format").get("/ccompat/v7/schemas/ids/{id}",
                schemaIdResponse).then().statusCode(400);
    }

    /**
     * Test that PROTOBUF schemas support the "ignore_extensions" format.
     */
    @Test
    public void testProtobufIgnoreExtensionsFormat() throws Exception {
        String subject = "protobuf-ignore-extensions-test";

        // Register a PROTOBUF schema with extensions
        String requestBody = "{ \"schema\": \"" + escapeJson(PROTOBUF_SCHEMA_WITH_EXTENSIONS)
                + "\", \"schemaType\": \"PROTOBUF\" }";

        String schemaIdResponse = given().when().contentType(ContentType.JSON).body(requestBody)
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200).body("id",
                        notNullValue()).extract().path("id").toString();

        // Get the schema without format parameter
        String originalSchema = given().when().get("/ccompat/v7/schemas/ids/{id}/schema", schemaIdResponse)
                .then().statusCode(200).extract().asString();

        // Get the schema with format=ignore_extensions
        String formattedSchema = given().when().queryParam("format", "ignore_extensions")
                .get("/ccompat/v7/schemas/ids/{id}/schema", schemaIdResponse).then().statusCode(200).extract()
                .asString();

        // Both should be valid schemas
        assert originalSchema != null;
        assert formattedSchema != null;
    }

    /**
     * Test that PROTOBUF schemas support the "serialized" format.
     */
    @Test
    public void testProtobufSerializedFormat() throws Exception {
        String subject = "protobuf-serialized-test";

        // Register a PROTOBUF schema
        String requestBody = "{ \"schema\": \"" + escapeJson(PROTOBUF_SCHEMA)
                + "\", \"schemaType\": \"PROTOBUF\" }";

        String schemaIdResponse = given().when().contentType(ContentType.JSON).body(requestBody)
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200).body("id",
                        notNullValue()).extract().path("id").toString();

        // Get the schema with format=serialized - should return binary data
        given().when().queryParam("format", "serialized").get("/ccompat/v7/schemas/ids/{id}/schema",
                schemaIdResponse).then().statusCode(200);
    }

    /**
     * Test that providing an invalid format for PROTOBUF returns an error.
     */
    @Test
    public void testProtobufInvalidFormat() throws Exception {
        String subject = "protobuf-invalid-format-test";

        // Register a simple PROTOBUF schema
        String requestBody = "{ \"schema\": \"" + escapeJson(PROTOBUF_SCHEMA)
                + "\", \"schemaType\": \"PROTOBUF\" }";

        String schemaIdResponse = given().when().contentType(ContentType.JSON).body(requestBody)
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200).body("id",
                        notNullValue()).extract().path("id").toString();

        // Try to get the schema with an invalid format (resolved is only for AVRO)
        given().when().queryParam("format", "resolved").get("/ccompat/v7/schemas/ids/{id}", schemaIdResponse)
                .then().statusCode(400);
    }

    /**
     * Test that JSON schemas ignore the format parameter.
     */
    @Test
    public void testJsonSchemaIgnoresFormat() throws Exception {
        String subject = "json-format-test";

        // Register a JSON schema
        String requestBody = "{ \"schema\": \"" + escapeJson(JSON_SCHEMA)
                + "\", \"schemaType\": \"JSON\" }";

        String schemaIdResponse = given().when().contentType(ContentType.JSON).body(requestBody)
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200).body("id",
                        notNullValue()).extract().path("id").toString();

        // Get the schema without format parameter
        String originalSchema = given().when().get("/ccompat/v7/schemas/ids/{id}/schema", schemaIdResponse)
                .then().statusCode(200).extract().asString();

        // Get the schema with format parameter - should be the same (format is ignored for JSON)
        String formattedSchema = given().when().queryParam("format", "resolved")
                .get("/ccompat/v7/schemas/ids/{id}/schema", schemaIdResponse).then().statusCode(200).extract()
                .asString();

        // Schemas should be the same
        assert originalSchema.equals(formattedSchema);
    }

    /**
     * Test that the format parameter works with subject version endpoints.
     */
    @Test
    public void testFormatParameterOnSubjectVersionEndpoint() throws Exception {
        String subject = "subject-version-format-test";

        // Register a simple AVRO schema
        String requestBody = "{ \"schema\": \"" + escapeJson(AVRO_SIMPLE_SCHEMA)
                + "\", \"schemaType\": \"AVRO\" }";

        given().when().contentType(ContentType.JSON).body(requestBody)
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200);

        // Get the schema from subject/version endpoint without format
        given().when().get("/ccompat/v7/subjects/{subject}/versions/1", subject).then().statusCode(200)
                .body("schema", notNullValue());

        // Get the schema with format=resolved (should work even without references)
        given().when().queryParam("format", "resolved").get("/ccompat/v7/subjects/{subject}/versions/1",
                subject).then().statusCode(200).body("schema", notNullValue());
    }

    /**
     * Helper method to escape JSON strings for embedding in JSON.
     */
    private String escapeJson(String json) {
        return json.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
