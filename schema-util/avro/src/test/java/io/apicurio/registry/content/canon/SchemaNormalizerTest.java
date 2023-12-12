package io.apicurio.registry.content.canon;

import io.apicurio.registry.content.ContentHandle;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SchemaNormalizerTest {

    @Test
    void parseSchema_SchemasWithOptionalAttributesInRoot_Equal() {
        // prepare
        String schemaStr = "{\n" + "  \"type\": \"record\",\n" + "  \"name\": \"schemaName\",\n"
                + "  \"namespace\": \"com.example.client.example.schema\",\n" + "  \"fields\": []\n" + "}";
        String schemaWithOptionalStr = "{\n" + "  \"type\": \"record\",\n" + "  \"name\": \"schemaName\",\n"
                + "  \"doc\": \"some description\",\n" + // optional attribute
                "  \"namespace\": \"com.example.client.example.schema\",\n" + "  \"fields\": []\n" + "}";

        // act
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        ContentHandle schema = canonicalizer.canonicalize(ContentHandle.create(schemaStr), new HashMap<>());
        ContentHandle schemaWithOptional = canonicalizer
                .canonicalize(ContentHandle.create(schemaWithOptionalStr), new HashMap<>());

        // assert
        assertEquals(schema.content(), schemaWithOptional.content());
        assertEquals(Schema.parseJsonToObject(schema.content()),
                Schema.parseJsonToObject(schemaWithOptional.content()));
    }

    @Test
    void parseSchema_SchemaWithNamespaceInNameAndInNamespaceField_Equal() {
        // prepare
        String schemaStr = "{\n" + "  \"type\": \"record\",\n"
                + "  \"name\": \"com.example.client.example.schema.schemaName\",\n"
                + "  \"doc\": \"some description\",\n" + "  \"fields\": []\n" + "}";
        String schemaWithNamespaceFieldStr = "{\n" + "  \"type\": \"record\",\n"
                + "  \"name\": \"schemaName\",\n" + "  \"doc\": \"some description\",\n"
                + "  \"namespace\": \"com.example.client.example.schema\",\n" + "  \"fields\": []\n" + "}";

        // act
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        ContentHandle schema = canonicalizer.canonicalize(ContentHandle.create(schemaStr), new HashMap<>());
        ContentHandle schemaWithNamespaceField = canonicalizer
                .canonicalize(ContentHandle.create(schemaWithNamespaceFieldStr), new HashMap<>());

        // assert
        assertEquals(schema.content(), schemaWithNamespaceField.content());
        assertEquals(Schema.parseJsonToObject(schema.content()),
                Schema.parseJsonToObject(schemaWithNamespaceField.content()));
    }

    @Test
    void parseSchema_SchemaWithDifferentNamespaceInNameAndInNamespaceField_NotEqual() {
        // prepare
        String schemaStr = "{\n" + "  \"type\": \"record\",\n"
                + "  \"name\": \"com.different.client.example.schema.schemaName\",\n"
                + "  \"doc\": \"some description\",\n" + "  \"fields\": []\n" + "}";
        String schemaWithNamespaceFieldStr = "{\n" + "  \"type\": \"record\",\n"
                + "  \"name\": \"schemaName\",\n" + "  \"doc\": \"some description\",\n"
                + "  \"namespace\": \"com.example.client.example.schema\",\n" + "  \"fields\": []\n" + "}";

        // act
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        ContentHandle schema = canonicalizer.canonicalize(ContentHandle.create(schemaStr), new HashMap<>());
        ContentHandle schemaWithNamespaceField = canonicalizer
                .canonicalize(ContentHandle.create(schemaWithNamespaceFieldStr), new HashMap<>());

        // assert
        assertNotEquals(schema.content(), schemaWithNamespaceField.content());
        assertNotEquals(Schema.parseJsonToObject(schema.content()),
                Schema.parseJsonToObject(schemaWithNamespaceField.content()));
    }

    @Test
    void parseSchema_SchemasWithDifferenceAttributesOrderInRoot_Equal() {
        // prepare
        String schemaStr = "{\n" + "  \"type\": \"record\",\n" + "  \"name\": \"schemaName\",\n"
                + "  \"doc\": \"some description\",\n"
                + "  \"namespace\": \"com.example.client.example.schema\",\n" + "  \"fields\": []\n" + "}";
        String schemaWithDifferenceAttributesOrderStr = "{\n" + // reverse order of keys
                "  \"fields\": [],\n" + "  \"namespace\": \"com.example.client.example.schema\",\n"
                + "  \"doc\": \"some description\",\n" + "  \"name\": \"schemaName\",\n"
                + "  \"type\": \"record\"\n" + "}";

        // act
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        ContentHandle schema = canonicalizer.canonicalize(ContentHandle.create(schemaStr), new HashMap<>());
        ContentHandle schemaWithDifferenceAttributesOrder = canonicalizer
                .canonicalize(ContentHandle.create(schemaWithDifferenceAttributesOrderStr), new HashMap<>());

        // assert
        assertEquals(schema.content(), schemaWithDifferenceAttributesOrder.content());
        assertEquals(Schema.parseJsonToObject(schema.content()),
                Schema.parseJsonToObject(schemaWithDifferenceAttributesOrder.content()));
    }

    @Test
    void parseSchema_SchemasWithOptionalAttributesInField_Equal() {
        // prepare
        String schemaStr = "{\n" + "  \"type\": \"record\",\n" + "  \"name\": \"schemaName\",\n"
                + "  \"namespace\": \"com.example.client.example.schema\",\n" + "  \"fields\": [\n"
                + "    {\n" + "      \"name\": \"timestamp\",\n" + "      \"type\": \"long\"\n" + // without
                                                                                                  // 'doc'
                                                                                                  // attribute
                "    }]\n" + "}";

        String schemaWithOptionalAttributesInFieldStr = "{\n" + "  \"type\": \"record\",\n"
                + "  \"name\": \"schemaName\",\n"
                + "  \"namespace\": \"com.example.client.example.schema\",\n" + "  \"fields\": [\n"
                + "    {\n" + "      \"name\": \"timestamp\",\n" + "      \"type\": \"long\",\n"
                + "      \"doc\": \"Timestamp of the event\"\n" + // added optional field
                "    }]\n" + "}";

        // act
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        ContentHandle schema = canonicalizer.canonicalize(ContentHandle.create(schemaStr), new HashMap<>());
        ContentHandle schemaWithOptionalAttributesInField = canonicalizer
                .canonicalize(ContentHandle.create(schemaWithOptionalAttributesInFieldStr), new HashMap<>());

        // assert
        assertEquals(schema.content(), schemaWithOptionalAttributesInField.content());
        assertEquals(Schema.parseJsonToObject(schema.content()),
                Schema.parseJsonToObject(schemaWithOptionalAttributesInField.content()));
    }

    @Test
    void parseSchema_SchemasWithDifferenceAttributesOrderInField_Equal() {
        // prepare
        String schemaStr = "{\n" + "  \"type\": \"record\",\n" + "  \"name\": \"schemaName\",\n"
                + "  \"namespace\": \"com.example.client.example.schema\",\n" + "  \"fields\": [\n"
                + "    {\n" + "      \"name\": \"message\",\n" + // `name` 1st `type` 2nd
                "      \"type\": \"string\"\n" + "    }]\n" + "}";

        String schemasWithDifferenceAttributesOrderInFieldStr = "{\n" + "  \"type\": \"record\",\n"
                + "  \"name\": \"schemaName\",\n"
                + "  \"namespace\": \"com.example.client.example.schema\",\n" + "  \"fields\": [\n"
                + "    {\n" + "      \"type\": \"string\",\n" + // `type` 1st `name` 2nd
                "      \"name\": \"message\"\n" + "    }]\n" + "}";

        // Act
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        ContentHandle schema = canonicalizer.canonicalize(ContentHandle.create(schemaStr), new HashMap<>());
        ContentHandle schemasWithDifferenceAttributesOrderInField = canonicalizer.canonicalize(
                ContentHandle.create(schemasWithDifferenceAttributesOrderInFieldStr), new HashMap<>());

        // Assert
        assertEquals(schema.content(), schemasWithDifferenceAttributesOrderInField.content());
        assertEquals(Schema.parseJsonToObject(schema.content()),
                Schema.parseJsonToObject(schemasWithDifferenceAttributesOrderInField.content()));
    }

    @Test
    void parseSchema_SchemasWithFieldsInDifferentOrder_NotEqual() {
        // prepare
        String schemaStr = "{\n" + "  \"type\": \"record\",\n" + "  \"name\": \"schemaName\",\n"
                + "  \"namespace\": \"com.example.client.example.schema\",\n" + "  \"fields\": [\n"
                + "    {\n" + "      \"name\": \"message\",\n" + "      \"type\": \"string\"\n" + "    },\n"
                + "    {\n" + "      \"name\": \"sender\",\n" + "      \"type\": \"string\"\n" + "    }]\n"
                + "}";

        String schemaWithFieldsInDifferentOrderStr = "{\n" + "  \"type\": \"record\",\n"
                + "  \"name\": \"schemaName\",\n"
                + "  \"namespace\": \"com.example.client.example.schema\",\n" + "  \"fields\": [\n"
                + "    {\n" + "      \"name\": \"sender\",\n" + "      \"type\": \"string\"\n" + "    },\n"
                + "    {\n" + "      \"name\": \"message\",\n" + "      \"type\": \"string\"\n" + "    }]\n"
                + "}";

        // Act
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        ContentHandle schema = canonicalizer.canonicalize(ContentHandle.create(schemaStr), new HashMap<>());
        ContentHandle schemaWithFieldsInDifferentOrder = canonicalizer
                .canonicalize(ContentHandle.create(schemaWithFieldsInDifferentOrderStr), new HashMap<>());

        // Assert
        assertNotEquals(schema.content(), schemaWithFieldsInDifferentOrder.content());
        assertNotEquals(Schema.parseJsonToObject(schema.content()),
                Schema.parseJsonToObject(schemaWithFieldsInDifferentOrder.content()));
    }

    @Test
    void parseSchema_NestedSchemasWithDifferenceAttributesOrderInField_Equal() {
        String nestedSchemaStr = "{\n" + "  \"type\": \"record\",\n" + "  \"name\": \"Schema\",\n"
                + "  \"namespace\": \"com.example.client.example.schema\",\n" + "  \"fields\": [\n"
                + "    {\n" + "      \"name\": \"name\",\n" + "      \"type\": \"string\"\n" + "    },\n"
                + "    {\n" + "      \"name\": \"innerSchema\",\n" + "      \"type\": {\n"
                + "        \"type\": \"record\",\n" + "        \"name\": \"NestedSchema\",\n"
                + "        \"namespace\": \"com.example.client.example.schema\",\n"
                + "        \"fields\": [\n" + "          {\n" + "            \"name\": \"innerName\",\n" + // `name`
                                                                                                           // 1st
                                                                                                           // `type`
                                                                                                           // 2nd
                "            \"type\": \"string\"\n" + "          }\n" + "        ]\n" + "      }\n"
                + "    }\n" + "  ]\n" + "}";

        String schemaWithDifferenceAttributesOrderInNestedSchemaStr = "{\n" + "  \"type\": \"record\",\n"
                + "  \"name\": \"Schema\",\n" + "  \"namespace\": \"com.example.client.example.schema\",\n"
                + "  \"fields\": [\n" + "    {\n" + "      \"name\": \"name\",\n"
                + "      \"type\": \"string\"\n" + "    },\n" + "    {\n"
                + "      \"name\": \"innerSchema\",\n" + "      \"type\": {\n"
                + "        \"type\": \"record\",\n" + "        \"name\": \"NestedSchema\",\n"
                + "        \"namespace\": \"com.example.client.example.schema\",\n"
                + "        \"fields\": [\n" + "          {\n" + "            \"type\": \"string\",\n" + // `type`
                                                                                                        // 1st
                                                                                                        // `name`
                                                                                                        // 2nd
                "            \"name\": \"innerName\"\n" + "          }\n" + "        ]\n" + "      }\n"
                + "    }\n" + "  ]\n" + "}";

        // Act
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        ContentHandle nestedSchema = canonicalizer.canonicalize(ContentHandle.create(nestedSchemaStr),
                new HashMap<>());
        ContentHandle schemaWithDifferenceAttributesOrderInNestedSchema = canonicalizer.canonicalize(
                ContentHandle.create(schemaWithDifferenceAttributesOrderInNestedSchemaStr), new HashMap<>());

        // Assert
        assertEquals(nestedSchema.content(), schemaWithDifferenceAttributesOrderInNestedSchema.content());
        assertEquals(Schema.parseJsonToObject(nestedSchema.content()),
                Schema.parseJsonToObject(schemaWithDifferenceAttributesOrderInNestedSchema.content()));
    }

    @Test
    void parseSchema_NestedSchemasOfSameType() throws Exception {
        final List<String> schemas = new ArrayList<>();
        final List<ContentHandle> normalizedSchemas = new ArrayList<>();
        // given a schema that has a field referencing its own type
        schemas.add(getSchemaFromResource("avro/simple/schema-with-same-nested-schema.avsc"));
        schemas.add(getSchemaFromResource("avro/simple/schema-with-same-nested-schema2.avsc"));
        schemas.add(getSchemaFromResource("avro/simple/schema-self-ref-array-item.avsc"));
        schemas.add(getSchemaFromResource("avro/simple/schema-self-ref-union.avsc"));

        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();

        schemas.forEach(schema -> {
            ContentHandle avroSchema = canonicalizer.canonicalize(ContentHandle.create(schema),
                    new HashMap<>());
            // the schema should be parsed without infinite recursion
            assertNotNull(avroSchema);
            normalizedSchemas.add(avroSchema);
        });

        // and the parsed schema should still be the same
        assertEquals(normalizedSchemas.get(0).content(), normalizedSchemas.get(1).content());
        assertEquals(Schema.parseJsonToObject(normalizedSchemas.get(0).content()),
                Schema.parseJsonToObject(normalizedSchemas.get(1).content()));
    }

    @Test
    void parseSchema_unionOfNullAndSelf() throws Exception {
        // given a schema containing a union of null and its own type
        final String schemaWithNullUnion = getSchemaFromResource("avro/advanced/schema-with-null-union.avsc");
        assertNotNull(schemaWithNullUnion);

        // the schema should be parsed with a non-null result
        EnhancedAvroContentCanonicalizer canonicalizer = new EnhancedAvroContentCanonicalizer();
        final ContentHandle parsed = canonicalizer.canonicalize(ContentHandle.create(schemaWithNullUnion),
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
        final ContentHandle parsed = canonicalizer.canonicalize(ContentHandle.create(schemaWithJavaType),
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
        final ContentHandle parsed = canonicalizer.canonicalize(ContentHandle.create(schemaWithCustomType),
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
        final ContentHandle parsed = canonicalizer.canonicalize(ContentHandle.create(schemaWithCustomType),
                new HashMap<>());

        assertNotNull(parsed);
    }

    private String getSchemaFromResource(String resourcePath) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        URL resourceURL = classLoader.getResource(resourcePath);
        File file = new File(resourceURL.getFile());
        return FileUtils.readFileToString(file, "UTF-8");
    }
}