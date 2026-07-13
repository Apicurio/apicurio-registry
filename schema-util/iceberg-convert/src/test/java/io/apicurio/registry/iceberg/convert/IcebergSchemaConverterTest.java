package io.apicurio.registry.iceberg.convert;

import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.commons.io.IOUtils;
import org.apache.iceberg.SchemaParser;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IcebergSchemaConverterTest {

    private static String resource(String path) throws IOException {
        try (InputStream in = IcebergSchemaConverterTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "Missing test resource: " + path);
            return IOUtils.toString(in, StandardCharsets.UTF_8);
        }
    }

    /**
     * Unwraps a nullable Avro union ({@code ["null", T]}) to its non-null branch.
     */
    private static Schema nonNull(Schema schema) {
        if (schema.getType() == Schema.Type.UNION) {
            for (Schema member : schema.getTypes()) {
                if (member.getType() != Schema.Type.NULL) {
                    return member;
                }
            }
        }
        return schema;
    }

    @Test
    void avroPrimitivesToIceberg() throws IOException {
        org.apache.iceberg.Schema schema = SchemaParser
                .fromJson(IcebergSchemaConverter.avroToIceberg(resource("avro/all-primitives.avsc")));

        assertEquals(Type.TypeID.STRING, schema.findField("s").type().typeId());
        assertEquals(Type.TypeID.INTEGER, schema.findField("i").type().typeId());
        assertEquals(Type.TypeID.LONG, schema.findField("l").type().typeId());
        assertEquals(Type.TypeID.FLOAT, schema.findField("f").type().typeId());
        assertEquals(Type.TypeID.DOUBLE, schema.findField("d").type().typeId());
        assertEquals(Type.TypeID.BOOLEAN, schema.findField("b").type().typeId());
        assertEquals(Type.TypeID.BINARY, schema.findField("by").type().typeId());
    }

    @Test
    void avroLogicalTypesToIceberg() throws IOException {
        org.apache.iceberg.Schema schema = SchemaParser
                .fromJson(IcebergSchemaConverter.avroToIceberg(resource("avro/logical-types.avsc")));

        assertEquals(Type.TypeID.DATE, schema.findField("theDate").type().typeId());
        assertEquals(Type.TypeID.TIMESTAMP, schema.findField("theTimestamp").type().typeId());

        Type decimal = schema.findField("theDecimal").type();
        assertEquals(Type.TypeID.DECIMAL, decimal.typeId());
        assertEquals(10, ((Types.DecimalType) decimal).precision());
        assertEquals(2, ((Types.DecimalType) decimal).scale());
    }

    @Test
    void avroNestedToIceberg() throws IOException {
        org.apache.iceberg.Schema schema = SchemaParser
                .fromJson(IcebergSchemaConverter.avroToIceberg(resource("avro/nested.avsc")));

        Type address = schema.findField("address").type();
        assertEquals(Type.TypeID.STRUCT, address.typeId());
        assertNotNull(address.asStructType().field("street"));
        assertNotNull(address.asStructType().field("zip"));

        Type tags = schema.findField("tags").type();
        assertEquals(Type.TypeID.LIST, tags.typeId());
        assertEquals(Type.TypeID.STRING, tags.asListType().elementType().typeId());

        Type attributes = schema.findField("attributes").type();
        assertEquals(Type.TypeID.MAP, attributes.typeId());
        assertEquals(Type.TypeID.STRING, attributes.asMapType().keyType().typeId());
        assertEquals(Type.TypeID.LONG, attributes.asMapType().valueType().typeId());
    }

    @Test
    void icebergToAvroPreservesFieldIds() throws IOException {
        Schema avro = new Schema.Parser()
                .parse(IcebergSchemaConverter.icebergToAvro(resource("iceberg/simple.iceberg.json")));

        assertEquals(Schema.Type.RECORD, avro.getType());
        assertEquals(1, avro.getField("id").getObjectProp("field-id"));
        assertEquals(2, avro.getField("name").getObjectProp("field-id"));
        assertEquals(3, avro.getField("amount").getObjectProp("field-id"));

        // required=true -> non-nullable; required=false -> nullable union
        assertEquals(Schema.Type.LONG, avro.getField("id").schema().getType());
        assertEquals(Schema.Type.UNION, avro.getField("name").schema().getType());
    }

    @Test
    void icebergToAvroConvertsLogicalAndComplexTypes() throws IOException {
        Schema avro = new Schema.Parser()
                .parse(IcebergSchemaConverter.icebergToAvro(resource("iceberg/simple.iceberg.json")));

        Schema amount = nonNull(avro.getField("amount").schema());
        assertTrue(amount.getLogicalType() instanceof LogicalTypes.Decimal);
        assertEquals(10, ((LogicalTypes.Decimal) amount.getLogicalType()).getPrecision());
        assertEquals(2, ((LogicalTypes.Decimal) amount.getLogicalType()).getScale());

        Schema created = nonNull(avro.getField("created").schema());
        assertEquals(Schema.Type.LONG, created.getType());
        assertNotNull(created.getLogicalType());
        assertTrue(created.getLogicalType().getName().startsWith("timestamp"));

        Schema tags = nonNull(avro.getField("tags").schema());
        assertEquals(Schema.Type.ARRAY, tags.getType());
        assertEquals(Schema.Type.STRING, tags.getElementType().getType());

        Schema props = nonNull(avro.getField("props").schema());
        assertEquals(Schema.Type.MAP, props.getType());
        assertEquals(Schema.Type.LONG, nonNull(props.getValueType()).getType());
    }

    @Test
    void icebergToAvroUsesCustomRecordName() throws IOException {
        Schema avro = new Schema.Parser()
                .parse(IcebergSchemaConverter.icebergToAvro(resource("iceberg/simple.iceberg.json"), "MyTable"));
        assertEquals("MyTable", avro.getName());
    }

    @Test
    void roundTripAvroToIcebergToAvro() throws IOException {
        String iceberg = IcebergSchemaConverter.avroToIceberg(resource("avro/nested.avsc"));
        Schema avro = new Schema.Parser().parse(IcebergSchemaConverter.icebergToAvro(iceberg));

        assertEquals(Schema.Type.RECORD, avro.getType());
        assertNotNull(avro.getField("id"));
        assertNotNull(avro.getField("address"));
        assertNotNull(avro.getField("tags"));
        assertNotNull(avro.getField("attributes"));
        // Field IDs assigned during Avro->Iceberg survive the round trip back to Avro.
        assertNotNull(avro.getField("id").getObjectProp("field-id"));
    }

    @Test
    void nonRecordAvroIsRejected() {
        assertThrows(IcebergSchemaConversionException.class,
                () -> IcebergSchemaConverter.avroToIceberg("\"string\""));
    }

    @Test
    void polymorphicUnionBecomesStruct() {
        String avro = "{ \"type\": \"record\", \"name\": \"R\", \"fields\": ["
                + "{ \"name\": \"u\", \"type\": [\"int\", \"string\"] } ] }";
        org.apache.iceberg.Schema schema = SchemaParser.fromJson(IcebergSchemaConverter.avroToIceberg(avro));

        // Iceberg has no union type; a polymorphic union is modeled as a tagged-union struct.
        Type u = schema.findField("u").type();
        assertEquals(Type.TypeID.STRUCT, u.typeId());
        assertNotNull(u.asStructType().field("tag"));
    }

    @Test
    void invalidJsonIsRejected() {
        assertThrows(IcebergSchemaConversionException.class,
                () -> IcebergSchemaConverter.icebergToAvro("not json"));
    }
}
