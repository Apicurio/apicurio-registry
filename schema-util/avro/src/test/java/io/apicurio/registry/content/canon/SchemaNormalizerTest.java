package io.apicurio.registry.content.canon;

import io.apicurio.registry.avro.content.canon.EnhancedAvroContentCanonicalizer;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.apache.avro.Schema;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SchemaNormalizerTest {

    private TypedContent toTypedContent(String content) {
        return TypedContent.create(ContentHandle.create(content), ContentTypes.APPLICATION_JSON);
    }

    @Test
    void parseSchema_SchemasWithOptionalAttributesInRoot_Equal() {
        // prepare
        String schemaStr = """
                {
                  "type": "record",
                  "name": "schemaName",
                  "namespace": "com.example.client.example.schema",
                  "fields": []
                }""";
        String schemaWithOptionalStr = """
                {
                  "type": "record",
                  "name": "schemaName",
                  "doc": "some description",
                  "namespace": "com.example.client.example.schema",
                  "fields": []
                }"""; // optional attribute "doc"

        // act
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent schema = canonicalizer.canonicalize(toTypedContent(schemaStr), new HashMap<>());
        TypedContent schemaWithOptional = canonicalizer.canonicalize(toTypedContent(schemaWithOptionalStr),
                new HashMap<>());

        // assert
        assertEquals(schema.getContent().content(), schemaWithOptional.getContent().content());
        assertEquals(Schema.parseJsonToObject(schema.getContent().content()),
                Schema.parseJsonToObject(schemaWithOptional.getContent().content()));
    }

    @Test
    void parseSchema_SchemaWithNamespaceInNameAndInNamespaceField_Equal() {
        // prepare
        String schemaStr = """
                {
                  "type": "record",
                  "name": "com.example.client.example.schema.schemaName",
                  "doc": "some description",
                  "fields": []
                }""";
        String schemaWithNamespaceFieldStr = """
                {
                  "type": "record",
                  "name": "schemaName",
                  "doc": "some description",
                  "namespace": "com.example.client.example.schema",
                  "fields": []
                }""";

        // act
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent schema = canonicalizer.canonicalize(toTypedContent(schemaStr), new HashMap<>());
        TypedContent schemaWithNamespaceField = canonicalizer
                .canonicalize(toTypedContent(schemaWithNamespaceFieldStr), new HashMap<>());

        // assert
        assertEquals(schema.getContent().content(), schemaWithNamespaceField.getContent().content());
        assertEquals(Schema.parseJsonToObject(schema.getContent().content()),
                Schema.parseJsonToObject(schemaWithNamespaceField.getContent().content()));
    }

    @Test
    void parseSchema_SchemaWithDifferentNamespaceInNameAndInNamespaceField_NotEqual() {
        // prepare
        String schemaStr = """
                {
                  "type": "record",
                  "name": "com.different.client.example.schema.schemaName",
                  "doc": "some description",
                  "fields": []
                }""";
        String schemaWithNamespaceFieldStr = """
                {
                  "type": "record",
                  "name": "schemaName",
                  "doc": "some description",
                  "namespace": "com.example.client.example.schema",
                  "fields": []
                }""";

        // act
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent schema = canonicalizer.canonicalize(toTypedContent(schemaStr), new HashMap<>());
        TypedContent schemaWithNamespaceField = canonicalizer
                .canonicalize(toTypedContent(schemaWithNamespaceFieldStr), new HashMap<>());

        // assert
        assertNotEquals(schema.getContent().content(), schemaWithNamespaceField.getContent().content());
        assertNotEquals(Schema.parseJsonToObject(schema.getContent().content()),
                Schema.parseJsonToObject(schemaWithNamespaceField.getContent().content()));
    }

    @Test
    void parseSchema_SchemasWithDifferenceAttributesOrderInRoot_Equal() {
        // prepare
        String schemaStr = """
                {
                  "type": "record",
                  "name": "schemaName",
                  "doc": "some description",
                  "namespace": "com.example.client.example.schema",
                  "fields": []
                }""";
        // reverse order of keys
        String schemaWithDifferenceAttributesOrderStr = """
                {
                  "fields": [],
                  "namespace": "com.example.client.example.schema",
                  "doc": "some description",
                  "name": "schemaName",
                  "type": "record"
                }""";

        // act
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent schema = canonicalizer.canonicalize(toTypedContent(schemaStr), new HashMap<>());
        TypedContent schemaWithDifferenceAttributesOrder = canonicalizer
                .canonicalize(toTypedContent(schemaWithDifferenceAttributesOrderStr), new HashMap<>());

        // assert
        assertEquals(schema.getContent().content(),
                schemaWithDifferenceAttributesOrder.getContent().content());
        assertEquals(Schema.parseJsonToObject(schema.getContent().content()),
                Schema.parseJsonToObject(schemaWithDifferenceAttributesOrder.getContent().content()));
    }

    @Test
    void parseSchema_SchemasWithOptionalAttributesInField_Equal() {
        // prepare — without 'doc' attribute
        String schemaStr = """
                {
                  "type": "record",
                  "name": "schemaName",
                  "namespace": "com.example.client.example.schema",
                  "fields": [
                    {
                      "name": "timestamp",
                      "type": "long"
                    }]
                }""";

        // added optional 'doc' field
        String schemaWithOptionalAttributesInFieldStr = """
                {
                  "type": "record",
                  "name": "schemaName",
                  "namespace": "com.example.client.example.schema",
                  "fields": [
                    {
                      "name": "timestamp",
                      "type": "long",
                      "doc": "Timestamp of the event"
                    }]
                }""";

        // act
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent schema = canonicalizer.canonicalize(toTypedContent(schemaStr), new HashMap<>());
        TypedContent schemaWithOptionalAttributesInField = canonicalizer
                .canonicalize(toTypedContent(schemaWithOptionalAttributesInFieldStr), new HashMap<>());

        // assert
        assertEquals(schema.getContent().content(),
                schemaWithOptionalAttributesInField.getContent().content());
        assertEquals(Schema.parseJsonToObject(schema.getContent().content()),
                Schema.parseJsonToObject(schemaWithOptionalAttributesInField.getContent().content()));
    }

    @Test
    void parseSchema_SchemasWithDifferenceAttributesOrderInField_Equal() {
        // prepare — `name` 1st `type` 2nd
        String schemaStr = """
                {
                  "type": "record",
                  "name": "schemaName",
                  "namespace": "com.example.client.example.schema",
                  "fields": [
                    {
                      "name": "message",
                      "type": "string"
                    }]
                }""";

        // `type` 1st `name` 2nd
        String schemasWithDifferenceAttributesOrderInFieldStr = """
                {
                  "type": "record",
                  "name": "schemaName",
                  "namespace": "com.example.client.example.schema",
                  "fields": [
                    {
                      "type": "string",
                      "name": "message"
                    }]
                }""";

        // Act
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent schema = canonicalizer.canonicalize(toTypedContent(schemaStr), new HashMap<>());
        TypedContent schemasWithDifferenceAttributesOrderInField = canonicalizer.canonicalize(
                toTypedContent(schemasWithDifferenceAttributesOrderInFieldStr), new HashMap<>());

        // Assert
        assertEquals(schema.getContent().content(),
                schemasWithDifferenceAttributesOrderInField.getContent().content());
        assertEquals(Schema.parseJsonToObject(schema.getContent().content()),
                Schema.parseJsonToObject(schemasWithDifferenceAttributesOrderInField.getContent().content()));
    }

    @Test
    void parseSchema_SchemasWithFieldsInDifferentOrder_NotEqual() {
        // prepare
        String schemaStr = """
                {
                  "type": "record",
                  "name": "schemaName",
                  "namespace": "com.example.client.example.schema",
                  "fields": [
                    {
                      "name": "message",
                      "type": "string"
                    },
                    {
                      "name": "sender",
                      "type": "string"
                    }]
                }""";

        String schemaWithFieldsInDifferentOrderStr = """
                {
                  "type": "record",
                  "name": "schemaName",
                  "namespace": "com.example.client.example.schema",
                  "fields": [
                    {
                      "name": "sender",
                      "type": "string"
                    },
                    {
                      "name": "message",
                      "type": "string"
                    }]
                }""";

        // Act
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent schema = canonicalizer.canonicalize(toTypedContent(schemaStr), new HashMap<>());
        TypedContent schemaWithFieldsInDifferentOrder = canonicalizer
                .canonicalize(toTypedContent(schemaWithFieldsInDifferentOrderStr), new HashMap<>());

        // Assert
        assertNotEquals(schema.getContent().content(),
                schemaWithFieldsInDifferentOrder.getContent().content());
        assertNotEquals(Schema.parseJsonToObject(schema.getContent().content()),
                Schema.parseJsonToObject(schemaWithFieldsInDifferentOrder.getContent().content()));
    }

    @Test
    void parseSchema_NestedSchemasWithDifferenceAttributesOrderInField_Equal() {
        // nested field: `name` 1st `type` 2nd
        String nestedSchemaStr = """
                {
                  "type": "record",
                  "name": "Schema",
                  "namespace": "com.example.client.example.schema",
                  "fields": [
                    {
                      "name": "name",
                      "type": "string"
                    },
                    {
                      "name": "innerSchema",
                      "type": {
                        "type": "record",
                        "name": "NestedSchema",
                        "namespace": "com.example.client.example.schema",
                        "fields": [
                          {
                            "name": "innerName",
                            "type": "string"
                          }
                        ]
                      }
                    }
                  ]
                }""";

        // nested field: `type` 1st `name` 2nd
        String schemaWithDifferenceAttributesOrderInNestedSchemaStr = """
                {
                  "type": "record",
                  "name": "Schema",
                  "namespace": "com.example.client.example.schema",
                  "fields": [
                    {
                      "name": "name",
                      "type": "string"
                    },
                    {
                      "name": "innerSchema",
                      "type": {
                        "type": "record",
                        "name": "NestedSchema",
                        "namespace": "com.example.client.example.schema",
                        "fields": [
                          {
                            "type": "string",
                            "name": "innerName"
                          }
                        ]
                      }
                    }
                  ]
                }""";

        // Act
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent nestedSchema = canonicalizer.canonicalize(toTypedContent(nestedSchemaStr),
                new HashMap<>());
        TypedContent schemaWithDifferenceAttributesOrderInNestedSchema = canonicalizer.canonicalize(
                toTypedContent(schemaWithDifferenceAttributesOrderInNestedSchemaStr), new HashMap<>());

        // Assert
        assertEquals(nestedSchema.getContent().content(),
                schemaWithDifferenceAttributesOrderInNestedSchema.getContent().content());
        assertEquals(Schema.parseJsonToObject(nestedSchema.getContent().content()), Schema
                .parseJsonToObject(schemaWithDifferenceAttributesOrderInNestedSchema.getContent().content()));
    }

    @Test
    void parseSchema_NestedSchemasOfSameType() throws Exception {
        final List<String> schemas = new ArrayList<>();
        final List<TypedContent> normalizedSchemas = new ArrayList<>();
        // given a schema that has a field referencing its own type
        schemas.add(getSchemaFromResource("avro/simple/schema-with-same-nested-schema.avsc"));
        schemas.add(getSchemaFromResource("avro/simple/schema-with-same-nested-schema2.avsc"));
        schemas.add(getSchemaFromResource("avro/simple/schema-self-ref-array-item.avsc"));
        schemas.add(getSchemaFromResource("avro/simple/schema-self-ref-union.avsc"));

        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();

        schemas.forEach(schema -> {
            TypedContent avroSchema = canonicalizer.canonicalize(toTypedContent(schema), new HashMap<>());
            // the schema should be parsed without infinite recursion
            assertNotNull(avroSchema);
            normalizedSchemas.add(avroSchema);
        });

        // and the parsed schema should still be the same
        assertEquals(normalizedSchemas.get(0).getContent().content(),
                normalizedSchemas.get(1).getContent().content());
        assertEquals(Schema.parseJsonToObject(normalizedSchemas.get(0).getContent().content()),
                Schema.parseJsonToObject(normalizedSchemas.get(1).getContent().content()));
    }

    /**
     * Test for issue #7023: Stackoverflow error with normalized self referencing AVRO schema.
     * This test verifies that a self-referencing schema with a map type does not cause
     * infinite recursion during normalization.
     */
    @Test
    void parseSchema_selfReferencingMapSchema() throws Exception {
        // given a schema that has a map field whose values reference the same type
        final String schemaWithSelfRefMap = getSchemaFromResource("avro/simple/schema-self-ref-map.avsc");
        assertNotNull(schemaWithSelfRefMap);

        // the schema should be parsed without infinite recursion
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        final TypedContent parsed = canonicalizer.canonicalize(toTypedContent(schemaWithSelfRefMap),
                new HashMap<>());

        // verify the result is not null and contains the expected structure
        assertNotNull(parsed);
        assertNotNull(parsed.getContent());
        assertNotNull(parsed.getContent().content());
    }

    @Test
    void parseSchema_unionOfNullAndSelf() throws Exception {
        // given a schema containing a union of null and its own type
        final String schemaWithNullUnion = getSchemaFromResource("avro/advanced/schema-with-null-union.avsc");
        assertNotNull(schemaWithNullUnion);

        // the schema should be parsed with a non-null result
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        final TypedContent parsed = canonicalizer.canonicalize(toTypedContent(schemaWithNullUnion),
                new HashMap<>());

        assertNotNull(parsed);
    }

    @Test
    void parseSchema_withJavaType() throws Exception {
        // given a schema containing a java type and its own type
        final String schemaWithJavaType = getSchemaFromResource("avro/advanced/schema-with-java-type.avsc");
        assertNotNull(schemaWithJavaType);

        // the schema should be parsed with a non-null result
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        final TypedContent parsed = canonicalizer.canonicalize(toTypedContent(schemaWithJavaType),
                new HashMap<>());

        assertNotNull(parsed);
    }

    @Test
    void parseSchema_withLogicalType() throws Exception {
        // given a schema containing a custom date type with logicalType
        final String schemaWithCustomType = getSchemaFromResource(
                "avro/advanced/schema-with-logicaltype.avsc");
        assertNotNull(schemaWithCustomType);

        // the schema should be parsed with a non-null result
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        final TypedContent parsed = canonicalizer.canonicalize(toTypedContent(schemaWithCustomType),
                new HashMap<>());

        assertNotNull(parsed);
    }

    @Test
    void parseSchema_withNestedEnumAndDefault() throws Exception {
        // given a schema containing a custom date type with logicalType
        final String schemaWithCustomType = getSchemaFromResource(
                "avro/advanced/schema-deeply-nested-enum-default.avsc");
        assertNotNull(schemaWithCustomType);

        // the schema should be parsed with a non-null result
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        final TypedContent parsed = canonicalizer.canonicalize(toTypedContent(schemaWithCustomType),
                new HashMap<>());

        assertNotNull(parsed);
    }

    /**
     * Reproducer: schemas differing only in connect.parameters values must produce different
     * canonical forms. Debezium uses connect.parameters to track allowed enum values
     * (e.g. io.debezium.data.Enum with "allowed" parameter).
     */
    @Test
    void parseSchema_SchemasWithDifferentConnectParameters_NotEqual() {
        String schemaV1 = """
                {
                  "type": "record",
                  "name": "Value",
                  "namespace": "com.example.dbserver1.public.shipments",
                  "fields": [
                    {
                      "name": "status",
                      "type": {
                        "type": "string",
                        "connect.parameters": {
                          "allowed": "station,post_office"
                        },
                        "connect.name": "io.debezium.data.Enum"
                      }
                    }
                  ]
                }""";

        String schemaV2 = """
                {
                  "type": "record",
                  "name": "Value",
                  "namespace": "com.example.dbserver1.public.shipments",
                  "fields": [
                    {
                      "name": "status",
                      "type": {
                        "type": "string",
                        "connect.parameters": {
                          "allowed": "station,post_office,plane"
                        },
                        "connect.name": "io.debezium.data.Enum"
                      }
                    }
                  ]
                }""";

        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent canonicalV1 = canonicalizer.canonicalize(toTypedContent(schemaV1), new HashMap<>());
        TypedContent canonicalV2 = canonicalizer.canonicalize(toTypedContent(schemaV2), new HashMap<>());

        assertNotEquals(canonicalV1.getContent().content(), canonicalV2.getContent().content(),
                "Schemas with different connect.parameters values must have different canonical forms");
    }

    /**
     * Reproducer: schemas differing only in field-level custom properties must produce
     * different canonical forms.
     */
    @Test
    void parseSchema_SchemasWithDifferentFieldLevelCustomProperties_NotEqual() {
        String schemaV1 = """
                {
                  "type": "record",
                  "name": "Value",
                  "namespace": "com.example",
                  "fields": [
                    {
                      "name": "status",
                      "type": "string",
                      "custom.property": "value1"
                    }
                  ]
                }""";

        String schemaV2 = """
                {
                  "type": "record",
                  "name": "Value",
                  "namespace": "com.example",
                  "fields": [
                    {
                      "name": "status",
                      "type": "string",
                      "custom.property": "value2"
                    }
                  ]
                }""";

        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent canonicalV1 = canonicalizer.canonicalize(toTypedContent(schemaV1), new HashMap<>());
        TypedContent canonicalV2 = canonicalizer.canonicalize(toTypedContent(schemaV2), new HashMap<>());

        assertNotEquals(canonicalV1.getContent().content(), canonicalV2.getContent().content(),
                "Schemas with different field-level custom properties must have different canonical forms");
    }

    /**
     * Reproducer: schemas differing only in a field's default value must produce different
     * canonical forms.
     */
    @Test
    void parseSchema_SchemasWithDifferentDefaultValues_NotEqual() {
        String schemaV1 = """
                {
                  "type": "record",
                  "name": "Value",
                  "namespace": "com.example",
                  "fields": [
                    {
                      "name": "shard",
                      "type": "int",
                      "default": 0
                    }
                  ]
                }""";

        String schemaV2 = """
                {
                  "type": "record",
                  "name": "Value",
                  "namespace": "com.example",
                  "fields": [
                    {
                      "name": "shard",
                      "type": "int",
                      "default": 1
                    }
                  ]
                }""";

        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent canonicalV1 = canonicalizer.canonicalize(toTypedContent(schemaV1), new HashMap<>());
        TypedContent canonicalV2 = canonicalizer.canonicalize(toTypedContent(schemaV2), new HashMap<>());

        assertNotEquals(canonicalV1.getContent().content(), canonicalV2.getContent().content(),
                "Schemas with different default values must have different canonical forms");
    }

    /**
     * Reproducer: schemas differing only in a string field's default value must produce
     * different canonical forms.
     */
    @Test
    void parseSchema_SchemasWithDifferentStringDefaultValues_NotEqual() {
        String schemaV1 = """
                {
                  "type": "record",
                  "name": "Value",
                  "namespace": "com.example",
                  "fields": [
                    {
                      "name": "region",
                      "type": "string",
                      "default": "us-east-1"
                    }
                  ]
                }""";

        String schemaV2 = """
                {
                  "type": "record",
                  "name": "Value",
                  "namespace": "com.example",
                  "fields": [
                    {
                      "name": "region",
                      "type": "string",
                      "default": "eu-west-1"
                    }
                  ]
                }""";

        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent canonicalV1 = canonicalizer.canonicalize(toTypedContent(schemaV1), new HashMap<>());
        TypedContent canonicalV2 = canonicalizer.canonicalize(toTypedContent(schemaV2), new HashMap<>());

        assertNotEquals(canonicalV1.getContent().content(), canonicalV2.getContent().content(),
                "Schemas with different string default values must have different canonical forms");
    }

    /**
     * Reproducer: Debezium-style nullable field with connect.parameters in a union.
     * This is the exact pattern Debezium uses for nullable enum columns.
     */
    @Test
    void parseSchema_DebeziumNullableEnumWithDifferentAllowedValues_NotEqual() {
        String schemaV1 = """
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
                }""";

        String schemaV2 = """
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
                }""";

        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent canonicalV1 = canonicalizer.canonicalize(toTypedContent(schemaV1), new HashMap<>());
        TypedContent canonicalV2 = canonicalizer.canonicalize(toTypedContent(schemaV2), new HashMap<>());

        assertNotEquals(canonicalV1.getContent().content(), canonicalV2.getContent().content(),
                "Debezium nullable enum schemas with different 'allowed' values must have different canonical forms");
    }

    /**
     * Reproducer: connect.parameters with multiple keys where only one value changes.
     */
    @Test
    void parseSchema_SchemasWithDifferentConnectParameterSubset_NotEqual() {
        String schemaV1 = """
                {
                  "type": "record",
                  "name": "Value",
                  "namespace": "com.example",
                  "fields": [
                    {
                      "name": "amount",
                      "type": {
                        "type": "bytes",
                        "connect.parameters": {
                          "scale": "2",
                          "connect.decimal.precision": "10"
                        },
                        "connect.name": "org.apache.kafka.connect.data.Decimal",
                        "logicalType": "decimal",
                        "precision": 10,
                        "scale": 2
                      }
                    }
                  ]
                }""";

        String schemaV2 = """
                {
                  "type": "record",
                  "name": "Value",
                  "namespace": "com.example",
                  "fields": [
                    {
                      "name": "amount",
                      "type": {
                        "type": "bytes",
                        "connect.parameters": {
                          "scale": "4",
                          "connect.decimal.precision": "10"
                        },
                        "connect.name": "org.apache.kafka.connect.data.Decimal",
                        "logicalType": "decimal",
                        "precision": 10,
                        "scale": 4
                      }
                    }
                  ]
                }""";

        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent canonicalV1 = canonicalizer.canonicalize(toTypedContent(schemaV1), new HashMap<>());
        TypedContent canonicalV2 = canonicalizer.canonicalize(toTypedContent(schemaV2), new HashMap<>());

        assertNotEquals(canonicalV1.getContent().content(), canonicalV2.getContent().content(),
                "Schemas with different connect.parameters scale values must have different canonical forms");
    }

    @Test
    void parseSchema_NullableUnionWithAndWithoutDefaultNull_Equal() {
        // Schema without "default": null on a nullable union field
        String schemaWithoutDefault = """
                {
                  "type": "record",
                  "name": "Application",
                  "namespace": "nl.example.test",
                  "fields": [
                    {"name": "name", "type": "string"},
                    {"name": "version", "type": ["null", "string"]}
                  ]
                }""";

        // Same schema WITH explicit "default": null
        String schemaWithDefault = """
                {
                  "type": "record",
                  "name": "Application",
                  "namespace": "nl.example.test",
                  "fields": [
                    {"name": "name", "type": "string"},
                    {"name": "version", "type": ["null", "string"], "default": null}
                  ]
                }""";

        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent normalized1 = canonicalizer.canonicalize(toTypedContent(schemaWithoutDefault),
                new HashMap<>());
        TypedContent normalized2 = canonicalizer.canonicalize(toTypedContent(schemaWithDefault),
                new HashMap<>());

        assertEquals(normalized1.getContent().content(), normalized2.getContent().content());
    }

    @Test
    void parseSchema_MapWithSelfReferencingRecord_NormalizesWithoutError() {
        // Schema with a map whose value type references the enclosing record
        String schema = """
                {
                  "type": "record",
                  "name": "TreeNode",
                  "namespace": "com.example",
                  "fields": [
                    {"name": "value", "type": "string"},
                    {"name": "children", "type": {"type": "map", "values": "TreeNode"}}
                  ]
                }""";

        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent normalized = canonicalizer.canonicalize(toTypedContent(schema), new HashMap<>());

        assertNotNull(normalized);
        // Verify it round-trips through Avro parser
        Schema parsed = new Schema.Parser().parse(normalized.getContent().content());
        assertEquals("TreeNode", parsed.getName());
    }

    @Test
    void parseSchema_NullableUnionDifferentOrder_NotEqual() {
        // ["null", "string"] no default — fix injects implicit "default": null
        String schemaNullFirst = """
                {
                  "type": "record",
                  "name": "Application",
                  "namespace": "nl.example.test",
                  "fields": [
                    {"name": "version", "type": ["null", "string"]}
                  ]
                }""";

        // ["string", "null"] no default — first type is not null, fix must NOT trigger
        String schemaStringFirst = """
                {
                  "type": "record",
                  "name": "Application",
                  "namespace": "nl.example.test",
                  "fields": [
                    {"name": "version", "type": ["string", "null"]}
                  ]
                }""";

        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent canonicalNullFirst = canonicalizer.canonicalize(toTypedContent(schemaNullFirst),
                new HashMap<>());
        TypedContent canonicalStringFirst = canonicalizer.canonicalize(toTypedContent(schemaStringFirst),
                new HashMap<>());

        assertNotEquals(canonicalNullFirst.getContent().content(),
                canonicalStringFirst.getContent().content(),
                "Unions with different member order must have different canonical forms");
    }

    @Test
    void parseSchema_UnionStartingWithStringThenNull_NoDefaultInjected() {
        // ["string", "null"] no default — fix's "first type is null" guard must prevent injection
        String schema = """
                {
                  "type": "record",
                  "name": "Application",
                  "namespace": "nl.example.test",
                  "fields": [
                    {"name": "version", "type": ["string", "null"]}
                  ]
                }""";

        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent canonical = canonicalizer.canonicalize(toTypedContent(schema), new HashMap<>());

        Schema parsed = new Schema.Parser().parse(canonical.getContent().content());
        Schema.Field versionField = parsed.getField("version");
        assertNotNull(versionField);
        assertFalse(versionField.hasDefaultValue(),
                "Field must not have a default injected when the union does not start with null");
    }

    @Test
    void parseSchema_NestedRecordWithNullableUnion_Equal() {
        // Outer record contains a nested record whose field uses ["null", "string"] with no default
        String schemaWithoutDefault = """
                {
                  "type": "record",
                  "name": "Outer",
                  "namespace": "nl.example.test",
                  "fields": [
                    {"name": "inner", "type": {
                      "type": "record", "name": "Inner", "fields": [
                        {"name": "version", "type": ["null", "string"]}
                      ]
                    }}
                  ]
                }""";

        // Same structure but the nested field has explicit "default": null
        String schemaWithDefault = """
                {
                  "type": "record",
                  "name": "Outer",
                  "namespace": "nl.example.test",
                  "fields": [
                    {"name": "inner", "type": {
                      "type": "record", "name": "Inner", "fields": [
                        {"name": "version", "type": ["null", "string"], "default": null}
                      ]
                    }}
                  ]
                }""";

        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent normalized1 = canonicalizer.canonicalize(toTypedContent(schemaWithoutDefault),
                new HashMap<>());
        TypedContent normalized2 = canonicalizer.canonicalize(toTypedContent(schemaWithDefault),
                new HashMap<>());

        assertEquals(normalized1.getContent().content(), normalized2.getContent().content(),
                "Nullable union default normalization must apply recursively to nested records");
    }

    @Test
    void parseSchema_ThreeWayUnionStartingWithNull_Equal() {
        // ["null", "string", "int"] no default
        String schemaWithoutDefault = """
                {
                  "type": "record",
                  "name": "Application",
                  "namespace": "nl.example.test",
                  "fields": [
                    {"name": "value", "type": ["null", "string", "int"]}
                  ]
                }""";

        // ["null", "string", "int"] with explicit "default": null
        String schemaWithDefault = """
                {
                  "type": "record",
                  "name": "Application",
                  "namespace": "nl.example.test",
                  "fields": [
                    {"name": "value", "type": ["null", "string", "int"], "default": null}
                  ]
                }""";

        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        TypedContent normalized1 = canonicalizer.canonicalize(toTypedContent(schemaWithoutDefault),
                new HashMap<>());
        TypedContent normalized2 = canonicalizer.canonicalize(toTypedContent(schemaWithDefault),
                new HashMap<>());

        assertEquals(normalized1.getContent().content(), normalized2.getContent().content(),
                "Unions with more than two members starting with null must still be normalized");
    }

    private String getSchemaFromResource(String resourcePath) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        URL resourceURL = classLoader.getResource(resourcePath);
        File file = new File(resourceURL.getFile());
        return FileUtils.readFileToString(file, "UTF-8");
    }
}