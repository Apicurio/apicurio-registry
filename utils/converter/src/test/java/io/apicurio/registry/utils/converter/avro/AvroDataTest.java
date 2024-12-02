package io.apicurio.registry.utils.converter.avro;

import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class AvroDataTest {

    @Test
    public void testIntWithConnectDefault() {
        final String s = "{"
                + "  \"type\": \"record\","
                + "  \"name\": \"sample\","
                + "  \"namespace\": \"io.apicurio\","
                + "  \"fields\": ["
                + "    {"
                + "      \"name\": \"prop\","
                + "      \"type\": {"
                + "        \"type\": \"int\","
                + "        \"connect.default\": 42,"
                + "        \"connect.version\": 1"
                + "      }"
                + "    }"
                + "  ]"
                + "}";

        org.apache.avro.Schema avroSchema = new org.apache.avro.Schema.Parser().parse(s);

        AvroData avroData = new AvroData(0);
        Schema schema = avroData.toConnectSchema(avroSchema);

        Assertions.assertEquals(42, schema.field("prop").schema().defaultValue());
    }

    @Test
    public void testLongWithConnectDefault() {
        final String s = "{"
                + "  \"type\": \"record\","
                + "  \"name\": \"sample\","
                + "  \"namespace\": \"io.apicurio\","
                + "  \"fields\": ["
                + "    {"
                + "      \"name\": \"prop\","
                + "      \"type\": {"
                + "        \"type\": \"long\","
                + "        \"connect.default\": 42,"
                + "        \"connect.version\": 1"
                + "      }"
                + "    }"
                + "  ]"
                + "}";

        org.apache.avro.Schema avroSchema = new org.apache.avro.Schema.Parser().parse(s);

        AvroData avroData = new AvroData(0);
        Schema schema = avroData.toConnectSchema(avroSchema);

        Assertions.assertEquals(42L, schema.field("prop").schema().defaultValue());
    }

    @Test
    public void testAvroInt64WithInteger() {
        final String s = "{"
                + "  \"type\": \"record\","
                + "  \"name\": \"sample\","
                + "  \"namespace\": \"io.apicurio\","
                + "  \"fields\": ["
                + "    {"
                + "      \"name\": \"someprop\","
                + "      \"type\": [\"long\",\"null\"]"
                + "    }"
                + "  ]"
                + "}";

        org.apache.avro.Schema avroSchema = new org.apache.avro.Schema.Parser().parse(s);
        GenericRecord outputRecord = new GenericRecordBuilder(avroSchema).set("someprop", (long) 42).build();
        AvroData avroData = new AvroData(0);
        Assertions.assertDoesNotThrow(() -> avroData.toConnectData(avroSchema, outputRecord));
    }

    @Test
    public void testDecimal() {
        final String s = "{"
                + "  \"type\": \"record\","
                + "  \"name\": \"sample\","
                + "  \"namespace\": \"io.apicurio\","
                + "  \"fields\": ["
                + "    {"
                + "      \"name\": \"somedecimal\","
                + "      \"type\": [\n"
                + "          {\n"
                + "            \"type\": \"bytes\",\n"
                + "            \"scale\": 4,\n"
                + "            \"precision\": 4,\n"
                + "            \"connect.version\": 1,\n"
                + "            \"connect.parameters\": {\n"
                + "              \"scale\": \"4\",\n"
                + "              \"connect.decimal.precision\": \"4\"\n"
                + "            },\n"
                + "            \"connect.default\": \"AA==\",\n"
                + "            \"connect.name\": \"org.apache.kafka.connect.data.Decimal\",\n"
                + "            \"logicalType\": \"decimal\"\n"
                + "          },\n"
                + "          \"null\"\n"
                + "       ],\n"
                + "       \"default\": \"AA==\""
                + "    }"
                + "  ],"
                + "\"connect.name\":\"io.apicurio.sample\"\n"
                + "}";

        org.apache.avro.Schema bSchema = new org.apache.avro.Schema.Parser().parse(s);
        AvroData avroData = new AvroData(0);
        org.apache.avro.Schema aSchema = avroData.fromConnectSchema(avroData.toConnectSchema(bSchema));
        Assertions.assertEquals(bSchema.toString(), aSchema.toString());
    }

    @Test
    void testCacheDistinguishesByParameters() {
        AvroData avroData = new AvroData(5);
        // Create two Connect schemas with the same name but different parameters
        Schema schemaWithPrecision10 = createConnectSchema("io.debezium.data.VariableScaleDecimal", "10");
        Schema schemaWithPrecision15 = createConnectSchema("io.debezium.data.VariableScaleDecimal", "15");

        // Generate Avro schemas for both
        org.apache.avro.Schema avroSchema1 = avroData.fromConnectSchema(schemaWithPrecision10);
        org.apache.avro.Schema avroSchema2 = avroData.fromConnectSchema(schemaWithPrecision15);

        // Verify that the two schemas are different
        assertNotEquals(avroSchema1, avroSchema2, "Avro schemas with different parameters should not be equal");

        // Verify that repeated calls with the same schema return the same instance (cache hit)
        org.apache.avro.Schema avroSchema1Again = avroData.fromConnectSchema(schemaWithPrecision10);
        assertSame(avroSchema1, avroSchema1Again, "Repeated calls with the same schema should return the cached instance");

        org.apache.avro.Schema avroSchema2Again = avroData.fromConnectSchema(schemaWithPrecision15);
        assertSame(avroSchema2, avroSchema2Again, "Repeated calls with the same schema should return the cached instance");
    }

    private Schema createConnectSchema(String name, String precision) {
        // Create a struct schema similar to the example provided
        return SchemaBuilder.struct()
                .name(name)
                .version(1)
                .doc("Variable scaled decimal")
                .field("scale", Schema.INT32_SCHEMA)
                .field("value", Schema.BYTES_SCHEMA)
                .parameter("precision", precision)
                .optional()
                .build();
    }
}
