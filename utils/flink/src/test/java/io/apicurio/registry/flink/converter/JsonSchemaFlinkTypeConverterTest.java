package io.apicurio.registry.flink.converter;

import org.apache.flink.table.catalog.Column;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.types.logical.LogicalTypeRoot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSchemaFlinkTypeConverterTest {

    @Test
    void testSimpleObjectSchema() {
        final String jsonSchema = "{"
                + "\"type\": \"object\","
                + "\"properties\": {"
                + "  \"id\": {\"type\": \"integer\"},"
                + "  \"name\": {\"type\": \"string\"},"
                + "  \"active\": {\"type\": \"boolean\"}"
                + "},"
                + "\"required\": [\"id\", \"name\"]"
                + "}";

        final ResolvedSchema resolvedSchema = JsonSchemaFlinkTypeConverter.convert(jsonSchema);
        assertNotNull(resolvedSchema);
        final List<Column> columns = resolvedSchema.getColumns();
        assertEquals(3, columns.size());
    }

    @Test
    void testPrimitiveTypes() {
        final String jsonSchema = "{"
                + "\"type\": \"object\","
                + "\"properties\": {"
                + "  \"intField\": {\"type\": \"integer\"},"
                + "  \"numField\": {\"type\": \"number\"},"
                + "  \"boolField\": {\"type\": \"boolean\"},"
                + "  \"strField\": {\"type\": \"string\"}"
                + "},"
                + "\"required\": [\"intField\", \"numField\", "
                + "\"boolField\", \"strField\"]"
                + "}";

        final ResolvedSchema resolvedSchema = JsonSchemaFlinkTypeConverter.convert(jsonSchema);
        final List<Column> columns = resolvedSchema.getColumns();
        assertEquals(4, columns.size());
    }

    @Test
    void testNullableFields() {
        final String jsonSchema = "{"
                + "\"type\": \"object\","
                + "\"properties\": {"
                + "  \"requiredField\": {\"type\": \"string\"},"
                + "  \"optionalField\": {\"type\": \"string\"}"
                + "},"
                + "\"required\": [\"requiredField\"]"
                + "}";

        final ResolvedSchema resolvedSchema = JsonSchemaFlinkTypeConverter.convert(jsonSchema);
        final List<Column> columns = resolvedSchema.getColumns();
        assertEquals(2, columns.size());

        Column optionalColumn = null;
        for (Column c : columns) {
            if (c.getName().equals("optionalField")) {
                optionalColumn = c;
                break;
            }
        }
        assertNotNull(optionalColumn);
        assertTrue(optionalColumn.getDataType().getLogicalType().isNullable());
    }

    @Test
    void testArrayType() {
        final String jsonSchema = "{"
                + "\"type\": \"object\","
                + "\"properties\": {"
                + "  \"tags\": {"
                + "    \"type\": \"array\","
                + "    \"items\": {\"type\": \"string\"}"
                + "  }"
                + "},"
                + "\"required\": [\"tags\"]"
                + "}";

        final ResolvedSchema resolvedSchema = JsonSchemaFlinkTypeConverter.convert(jsonSchema);
        final List<Column> columns = resolvedSchema.getColumns();
        assertEquals(1, columns.size());
        assertEquals("tags", columns.get(0).getName());
        assertEquals(LogicalTypeRoot.ARRAY,
                columns.get(0).getDataType().getLogicalType().getTypeRoot());
    }

    @Test
    void testFormatAnnotations() {
        final String jsonSchema = "{"
                + "\"type\": \"object\","
                + "\"properties\": {"
                + "  \"birthDate\": {\"type\": \"string\", "
                + "\"format\": \"date\"},"
                + "  \"createdAt\": {\"type\": \"string\", "
                + "\"format\": \"date-time\"},"
                + "  \"email\": {\"type\": \"string\", \"format\": \"email\"}"
                + "},"
                + "\"required\": [\"birthDate\", \"createdAt\", \"email\"]"
                + "}";

        final ResolvedSchema resolvedSchema = JsonSchemaFlinkTypeConverter.convert(jsonSchema);
        final List<Column> columns = resolvedSchema.getColumns();
        assertEquals(3, columns.size());

        Column dateColumn = null;
        Column timestampColumn = null;
        for (Column c : columns) {
            if (c.getName().equals("birthDate")) {
                dateColumn = c;
            }
            if (c.getName().equals("createdAt")) {
                timestampColumn = c;
            }
        }
        assertNotNull(dateColumn);
        assertEquals(LogicalTypeRoot.DATE,
                dateColumn.getDataType().getLogicalType().getTypeRoot());
        assertNotNull(timestampColumn);
        assertEquals(LogicalTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE,
                timestampColumn.getDataType().getLogicalType().getTypeRoot());
    }

    @Test
    void testNestedObject() {
        final String jsonSchema = "{"
                + "\"type\": \"object\","
                + "\"properties\": {"
                + "  \"id\": {\"type\": \"integer\"},"
                + "  \"address\": {"
                + "    \"type\": \"object\","
                + "    \"properties\": {"
                + "      \"street\": {\"type\": \"string\"},"
                + "      \"city\": {\"type\": \"string\"}"
                + "    }"
                + "  }"
                + "},"
                + "\"required\": [\"id\", \"address\"]"
                + "}";

        final ResolvedSchema resolvedSchema = JsonSchemaFlinkTypeConverter.convert(jsonSchema);
        final List<Column> columns = resolvedSchema.getColumns();
        assertEquals(2, columns.size());

        Column addressColumn = null;
        for (Column c : columns) {
            if (c.getName().equals("address")) {
                addressColumn = c;
                break;
            }
        }
        assertNotNull(addressColumn);
        assertEquals(LogicalTypeRoot.ROW,
                addressColumn.getDataType().getLogicalType().getTypeRoot());
    }

    @Test
    void testMapTypeFromAdditionalProperties() {
        final String jsonSchema = "{"
                + "\"type\": \"object\","
                + "\"additionalProperties\": {\"type\": \"string\"}"
                + "}";

        final ResolvedSchema resolvedSchema = JsonSchemaFlinkTypeConverter.convert(jsonSchema);
        assertNotNull(resolvedSchema);
    }
}
