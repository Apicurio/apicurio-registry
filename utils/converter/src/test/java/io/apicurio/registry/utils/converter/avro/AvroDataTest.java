package io.apicurio.registry.utils.converter.avro;

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
}
