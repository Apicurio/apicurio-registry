package io.apicurio.registry.flink.converter;

import org.apache.flink.table.catalog.Column;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.types.logical.LogicalTypeRoot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvroFlinkTypeConverterTest {

    @Test
    void testSimpleRecordSchema() {
        final String avroSchema = "{"
                + "\"type\": \"record\","
                + "\"name\": \"TestRecord\","
                + "\"fields\": ["
                + "  {\"name\": \"id\", \"type\": \"long\"},"
                + "  {\"name\": \"name\", \"type\": \"string\"},"
                + "  {\"name\": \"active\", \"type\": \"boolean\"}"
                + "]"
                + "}";

        final ResolvedSchema resolvedSchema = AvroFlinkTypeConverter.convert(avroSchema);
        assertNotNull(resolvedSchema);
        final List<Column> columns = resolvedSchema.getColumns();
        assertEquals(3, columns.size());
        assertEquals("id", columns.get(0).getName());
        assertEquals(LogicalTypeRoot.BIGINT,
                columns.get(0).getDataType().getLogicalType().getTypeRoot());
        assertEquals("name", columns.get(1).getName());
        assertEquals(LogicalTypeRoot.VARCHAR,
                columns.get(1).getDataType().getLogicalType().getTypeRoot());
        assertEquals("active", columns.get(2).getName());
        assertEquals(LogicalTypeRoot.BOOLEAN,
                columns.get(2).getDataType().getLogicalType().getTypeRoot());
    }

    @Test
    void testPrimitiveTypes() {
        final String avroSchema = "{"
                + "\"type\": \"record\","
                + "\"name\": \"AllTypes\","
                + "\"fields\": ["
                + "  {\"name\": \"intField\", \"type\": \"int\"},"
                + "  {\"name\": \"longField\", \"type\": \"long\"},"
                + "  {\"name\": \"floatField\", \"type\": \"float\"},"
                + "  {\"name\": \"doubleField\", \"type\": \"double\"},"
                + "  {\"name\": \"booleanField\", \"type\": \"boolean\"},"
                + "  {\"name\": \"stringField\", \"type\": \"string\"},"
                + "  {\"name\": \"bytesField\", \"type\": \"bytes\"}"
                + "]"
                + "}";

        final ResolvedSchema resolvedSchema = AvroFlinkTypeConverter.convert(avroSchema);
        final List<Column> columns = resolvedSchema.getColumns();
        assertEquals(7, columns.size());
        assertEquals(LogicalTypeRoot.INTEGER,
                columns.get(0).getDataType().getLogicalType().getTypeRoot());
        assertEquals(LogicalTypeRoot.BIGINT,
                columns.get(1).getDataType().getLogicalType().getTypeRoot());
        assertEquals(LogicalTypeRoot.FLOAT,
                columns.get(2).getDataType().getLogicalType().getTypeRoot());
        assertEquals(LogicalTypeRoot.DOUBLE,
                columns.get(3).getDataType().getLogicalType().getTypeRoot());
        assertEquals(LogicalTypeRoot.BOOLEAN,
                columns.get(4).getDataType().getLogicalType().getTypeRoot());
        assertEquals(LogicalTypeRoot.VARCHAR,
                columns.get(5).getDataType().getLogicalType().getTypeRoot());
        assertEquals(LogicalTypeRoot.VARBINARY,
                columns.get(6).getDataType().getLogicalType().getTypeRoot());
    }

    @Test
    void testNullableField() {
        final String avroSchema = "{"
                + "\"type\": \"record\","
                + "\"name\": \"NullableRecord\","
                + "\"fields\": ["
                + "  {\"name\": \"nullableName\", "
                + "\"type\": [\"null\", \"string\"]}"
                + "]"
                + "}";

        final ResolvedSchema resolvedSchema = AvroFlinkTypeConverter.convert(avroSchema);
        final List<Column> columns = resolvedSchema.getColumns();
        assertEquals(1, columns.size());
        assertEquals("nullableName", columns.get(0).getName());
        assertTrue(columns.get(0).getDataType().getLogicalType().isNullable());
    }

    @Test
    void testArrayType() {
        final String avroSchema = "{"
                + "\"type\": \"record\","
                + "\"name\": \"ArrayRecord\","
                + "\"fields\": ["
                + "  {\"name\": \"tags\", \"type\": "
                + "{\"type\": \"array\", \"items\": \"string\"}}"
                + "]"
                + "}";

        final ResolvedSchema resolvedSchema = AvroFlinkTypeConverter.convert(avroSchema);
        final List<Column> columns = resolvedSchema.getColumns();
        assertEquals(1, columns.size());
        assertEquals("tags", columns.get(0).getName());
        assertEquals(LogicalTypeRoot.ARRAY,
                columns.get(0).getDataType().getLogicalType().getTypeRoot());
    }

    @Test
    void testMapType() {
        final String avroSchema = "{"
                + "\"type\": \"record\","
                + "\"name\": \"MapRecord\","
                + "\"fields\": ["
                + "  {\"name\": \"metadata\", \"type\": "
                + "{\"type\": \"map\", \"values\": \"string\"}}"
                + "]"
                + "}";

        final ResolvedSchema resolvedSchema = AvroFlinkTypeConverter.convert(avroSchema);
        final List<Column> columns = resolvedSchema.getColumns();
        assertEquals(1, columns.size());
        assertEquals("metadata", columns.get(0).getName());
        assertEquals(LogicalTypeRoot.MAP,
                columns.get(0).getDataType().getLogicalType().getTypeRoot());
    }

    @Test
    void testLogicalTypes() {
        final String avroSchema = "{"
                + "\"type\": \"record\","
                + "\"name\": \"LogicalTypesRecord\","
                + "\"fields\": ["
                + "  {\"name\": \"dateField\", \"type\": "
                + "{\"type\": \"int\", \"logicalType\": \"date\"}},"
                + "  {\"name\": \"timestampField\", \"type\": "
                + "{\"type\": \"long\", \"logicalType\": \"timestamp-millis\"}},"
                + "  {\"name\": \"decimalField\", \"type\": "
                + "{\"type\": \"bytes\", \"logicalType\": \"decimal\", "
                + "\"precision\": 10, \"scale\": 2}}"
                + "]"
                + "}";

        final ResolvedSchema resolvedSchema = AvroFlinkTypeConverter.convert(avroSchema);
        final List<Column> columns = resolvedSchema.getColumns();
        assertEquals(3, columns.size());
        assertEquals(LogicalTypeRoot.DATE,
                columns.get(0).getDataType().getLogicalType().getTypeRoot());
        assertEquals(LogicalTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE,
                columns.get(1).getDataType().getLogicalType().getTypeRoot());
        assertEquals(LogicalTypeRoot.DECIMAL,
                columns.get(2).getDataType().getLogicalType().getTypeRoot());
    }

    @Test
    void testNestedRecord() {
        final String avroSchema = "{"
                + "\"type\": \"record\","
                + "\"name\": \"OuterRecord\","
                + "\"fields\": ["
                + "  {\"name\": \"id\", \"type\": \"long\"},"
                + "  {\"name\": \"address\", \"type\": {"
                + "      \"type\": \"record\","
                + "      \"name\": \"Address\","
                + "      \"fields\": ["
                + "        {\"name\": \"street\", \"type\": \"string\"},"
                + "        {\"name\": \"city\", \"type\": \"string\"}"
                + "      ]"
                + "    }"
                + "  }"
                + "]"
                + "}";

        final ResolvedSchema resolvedSchema = AvroFlinkTypeConverter.convert(avroSchema);
        final List<Column> columns = resolvedSchema.getColumns();
        assertEquals(2, columns.size());
        assertEquals("id", columns.get(0).getName());
        assertEquals("address", columns.get(1).getName());
        assertEquals(LogicalTypeRoot.ROW,
                columns.get(1).getDataType().getLogicalType().getTypeRoot());
    }
}
