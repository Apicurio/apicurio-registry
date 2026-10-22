package io.apicurio.registry.noprofile.ccompat.rest.v7;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.rest.ContentTypes;
import io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest;
import io.apicurio.registry.ccompat.rest.v7.beans.SchemaId;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Reproducer tests for Debezium regression: schemas that differ only in
 * connect.parameters values or field default values must be registered as
 * separate versions via the Confluent-compatible API.
 */
@QuarkusTest
public class CCompatConnectParametersTest extends AbstractResourceTestBase {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SCHEMA_DEBEZIUM_ENUM_V1 = """
            {
                "type": "record",
                "name": "Value",
                "namespace": "com.example.dbserver1.public.shipments",
                "fields": [
                    {
                        "name": "id",
                        "type": "int"
                    },
                    {
                        "name": "status",
                        "type": [
                            "null",
                            {
                                "type": "string",
                                "connect.parameters": {
                                    "allowed": "station,post_office"
                                },
                                "connect.name": "io.debezium.data.Enum",
                                "connect.version": 1
                            }
                        ],
                        "default": null
                    }
                ]
            }
            """;

    private static final String SCHEMA_DEBEZIUM_ENUM_V2 = """
            {
                "type": "record",
                "name": "Value",
                "namespace": "com.example.dbserver1.public.shipments",
                "fields": [
                    {
                        "name": "id",
                        "type": "int"
                    },
                    {
                        "name": "status",
                        "type": [
                            "null",
                            {
                                "type": "string",
                                "connect.parameters": {
                                    "allowed": "station,post_office,plane"
                                },
                                "connect.name": "io.debezium.data.Enum",
                                "connect.version": 1
                            }
                        ],
                        "default": null
                    }
                ]
            }
            """;

    private static final String SCHEMA_DEFAULT_VALUE_V1 = """
            {
                "type": "record",
                "name": "Value",
                "namespace": "com.example.dbserver1.public.config",
                "fields": [
                    {
                        "name": "id",
                        "type": "int"
                    },
                    {
                        "name": "shard",
                        "type": "int",
                        "default": 0
                    }
                ]
            }
            """;

    private static final String SCHEMA_DEFAULT_VALUE_V2 = """
            {
                "type": "record",
                "name": "Value",
                "namespace": "com.example.dbserver1.public.config",
                "fields": [
                    {
                        "name": "id",
                        "type": "int"
                    },
                    {
                        "name": "shard",
                        "type": "int",
                        "default": 1
                    }
                ]
            }
            """;

    @Test
    public void testRegisterSchemaWithDifferentConnectParameters() throws Exception {
        String subject = "ccompat-connect-params-test-value";

        // Register v1
        var request1 = new RegisterSchemaRequest();
        request1.setSchema(SCHEMA_DEBEZIUM_ENUM_V1);
        SchemaId id1 = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(request1))
                .post("/ccompat/v7/subjects/{subject}/versions", subject)
                .then().statusCode(200)
                .extract().as(SchemaId.class);

        // Register v2 with different connect.parameters
        var request2 = new RegisterSchemaRequest();
        request2.setSchema(SCHEMA_DEBEZIUM_ENUM_V2);
        SchemaId id2 = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(request2))
                .post("/ccompat/v7/subjects/{subject}/versions", subject)
                .then().statusCode(200)
                .extract().as(SchemaId.class);

        assertNotEquals(id1.getId(), id2.getId(),
                "Schemas with different connect.parameters 'allowed' values must get different IDs");

        ConfluentTestUtils.checkNumberOfVersions(buildConfluentClient(),
                2, subject);
    }

    @Test
    public void testRegisterSchemaWithDifferentDefaultValues() throws Exception {
        String subject = "ccompat-default-values-test-value";

        // Register v1
        var request1 = new RegisterSchemaRequest();
        request1.setSchema(SCHEMA_DEFAULT_VALUE_V1);
        SchemaId id1 = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(request1))
                .post("/ccompat/v7/subjects/{subject}/versions", subject)
                .then().statusCode(200)
                .extract().as(SchemaId.class);

        // Register v2 with different default value
        var request2 = new RegisterSchemaRequest();
        request2.setSchema(SCHEMA_DEFAULT_VALUE_V2);
        SchemaId id2 = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(request2))
                .post("/ccompat/v7/subjects/{subject}/versions", subject)
                .then().statusCode(200)
                .extract().as(SchemaId.class);

        assertNotEquals(id1.getId(), id2.getId(),
                "Schemas with different default values must get different IDs");

        ConfluentTestUtils.checkNumberOfVersions(buildConfluentClient(),
                2, subject);
    }

    @Test
    public void testRegisterSchemaWithDifferentConnectParameters_Normalize() throws Exception {
        String subject = "ccompat-connect-params-normalize-test-value";

        // Register v1
        var request1 = new RegisterSchemaRequest();
        request1.setSchema(SCHEMA_DEBEZIUM_ENUM_V1);
        SchemaId id1 = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(request1))
                .post("/ccompat/v7/subjects/{subject}/versions?normalize=true", subject)
                .then().statusCode(200)
                .extract().as(SchemaId.class);

        // Register v2 with different connect.parameters and normalize=true
        var request2 = new RegisterSchemaRequest();
        request2.setSchema(SCHEMA_DEBEZIUM_ENUM_V2);
        SchemaId id2 = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(request2))
                .post("/ccompat/v7/subjects/{subject}/versions?normalize=true", subject)
                .then().statusCode(200)
                .extract().as(SchemaId.class);

        assertNotEquals(id1.getId(), id2.getId(),
                "Schemas with different connect.parameters must get different IDs even with normalize=true");
    }

    @Test
    public void testRegisterSchemaWithDifferentDefaultValues_Normalize() throws Exception {
        String subject = "ccompat-default-values-normalize-test-value";

        // Register v1
        var request1 = new RegisterSchemaRequest();
        request1.setSchema(SCHEMA_DEFAULT_VALUE_V1);
        SchemaId id1 = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(request1))
                .post("/ccompat/v7/subjects/{subject}/versions?normalize=true", subject)
                .then().statusCode(200)
                .extract().as(SchemaId.class);

        // Register v2 with different default value and normalize=true
        var request2 = new RegisterSchemaRequest();
        request2.setSchema(SCHEMA_DEFAULT_VALUE_V2);
        SchemaId id2 = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(request2))
                .post("/ccompat/v7/subjects/{subject}/versions?normalize=true", subject)
                .then().statusCode(200)
                .extract().as(SchemaId.class);

        assertNotEquals(id1.getId(), id2.getId(),
                "Schemas with different default values must get different IDs even with normalize=true");
    }
}
