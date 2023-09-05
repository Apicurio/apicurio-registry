package io.apicurio.registry.utils.converter.avro;

import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.kafka.connect.data.Schema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
        GenericRecord outputRecord = new GenericRecordBuilder(avroSchema).set("someprop", 42).build();
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
                + "       \"default\": \"\\u0000\""
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
    public void testSanitizeName() {
        Assertions.assertEquals("Foo", AvroData.sanitizeName("Foo"));
        Assertions.assertEquals("Foo_Bar", AvroData.sanitizeName("Foo Bar"));
        Assertions.assertEquals("Foo_Bar", AvroData.sanitizeName("Foo.Bar"));
        Assertions.assertEquals("_123Foo", AvroData.sanitizeName("123Foo"));
        Assertions.assertEquals("_Baz_Bam_", AvroData.sanitizeName("#Baz Bam "));

        Assertions.assertEquals("my.namespace", AvroData.sanitizeNamespace("my.namespace"));
        Assertions.assertEquals("_.my.namespace", AvroData.sanitizeNamespace(".my.namespace"));
        Assertions.assertEquals("my_namespace_is_great", AvroData.sanitizeNamespace("my namespace is great"));
        Assertions.assertEquals("my_namespace.is_great", AvroData.sanitizeNamespace("my_namespace.is_great"));
        Assertions.assertEquals("my_namespace.is_great_", AvroData.sanitizeNamespace("my_namespace.is_great!"));
        Assertions.assertEquals("my_namespace.is_great_", AvroData.sanitizeNamespace("my_namespace.is_great "));
    }
    
}
