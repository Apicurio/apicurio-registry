package io.apicurio.registry.utils.converter.avro;

import io.apicurio.registry.serde.avro.NonRecordContainer;
import org.apache.avro.LogicalTypes;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.kafka.connect.data.Decimal;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class AvroDataTest {

    @Test
    public void testIntWithConnectDefault() {
        final String s = "{" + "  \"type\": \"record\"," + "  \"name\": \"sample\","
                + "  \"namespace\": \"io.apicurio\"," + "  \"fields\": [" + "    {"
                + "      \"name\": \"prop\"," + "      \"type\": {" + "        \"type\": \"int\","
                + "        \"connect.default\": 42," + "        \"connect.version\": 1" + "      }" + "    }"
                + "  ]" + "}";

        org.apache.avro.Schema avroSchema = new org.apache.avro.Schema.Parser().parse(s);

        AvroData avroData = new AvroData(0);
        Schema schema = avroData.toConnectSchema(avroSchema);

        Assertions.assertEquals(42, schema.field("prop").schema().defaultValue());
    }

    @Test
    public void testLongWithConnectDefault() {
        final String s = "{" + "  \"type\": \"record\"," + "  \"name\": \"sample\","
                + "  \"namespace\": \"io.apicurio\"," + "  \"fields\": [" + "    {"
                + "      \"name\": \"prop\"," + "      \"type\": {" + "        \"type\": \"long\","
                + "        \"connect.default\": 42," + "        \"connect.version\": 1" + "      }" + "    }"
                + "  ]" + "}";

        org.apache.avro.Schema avroSchema = new org.apache.avro.Schema.Parser().parse(s);

        AvroData avroData = new AvroData(0);
        Schema schema = avroData.toConnectSchema(avroSchema);

        Assertions.assertEquals(42L, schema.field("prop").schema().defaultValue());
    }

    @Test
    public void testAvroInt64WithInteger() {
        final String s = "{" + "  \"type\": \"record\"," + "  \"name\": \"sample\","
                + "  \"namespace\": \"io.apicurio\"," + "  \"fields\": [" + "    {"
                + "      \"name\": \"someprop\"," + "      \"type\": [\"long\",\"null\"]" + "    }" + "  ]"
                + "}";

        org.apache.avro.Schema avroSchema = new org.apache.avro.Schema.Parser().parse(s);
        GenericRecord outputRecord = new GenericRecordBuilder(avroSchema).set("someprop", (long) 42).build();
        AvroData avroData = new AvroData(0);
        Assertions.assertDoesNotThrow(() -> avroData.toConnectData(avroSchema, outputRecord));
    }

    @Test
    public void testDecimal() {
        final String s = "{" + "  \"type\": \"record\"," + "  \"name\": \"sample\","
                + "  \"namespace\": \"io.apicurio\"," + "  \"fields\": [" + "    {"
                + "      \"name\": \"somedecimal\"," + "      \"type\": [\n" + "          {\n"
                + "            \"type\": \"bytes\",\n"
                + "            \"connect.version\": 1,\n"
                + "            \"connect.parameters\": {\n"
                + "                 \"scale\": \"4\",\n"
                + "                 \"connect.decimal.precision\": \"4\"\n"
                + "            },\n"
                + "            \"connect.default\": \"AA==\",\n"
                + "            \"connect.name\": \"org.apache.kafka.connect.data.Decimal\",\n"
                + "            \"logicalType\": \"decimal\",\n"
                + "            \"precision\": 4,\n"
                + "            \"scale\": 4\n"
                + "          },\n" + "          \"null\"\n"
                + "       ],\n" + "       \"default\": \"AA==\"" + "    }" + "  ],"
                + "\"connect.name\":\"io.apicurio.sample\"\n" + "}";

        org.apache.avro.Schema bSchema = new org.apache.avro.Schema.Parser().parse(s);
        AvroData avroData = new AvroData(0);
        org.apache.avro.Schema aSchema = avroData.fromConnectSchema(avroData.toConnectSchema(bSchema));
        Assertions.assertEquals(bSchema.toString(), aSchema.toString());
    }

    @Test
    public void testDecimalWithIncompatibleScale() {
        BigDecimal decimal = BigDecimal.valueOf(0, 2);
        Schema connectSchema = Decimal.builder(2).parameter(AvroData.CONNECT_AVRO_DECIMAL_PRECISION_PROP, "1").build();

        SchemaAndValue connectValue = new SchemaAndValue(connectSchema, decimal);
        AvroData avroData = new AvroData(0);
        //noinspection unchecked
        NonRecordContainer<Object> result = (NonRecordContainer<Object>) avroData.fromConnectData(connectValue.schema(), connectValue.value());

        // no logical type should be set because this decimal is according to avro specification not allowed
        // "Scale must be zero or a positive integer less than or equal to the precision."
        Assertions.assertNull(result.getSchema().getLogicalType());
    }

    @Test
    public void testCustomLogicalTypeConverter() {
        Instant inst = Instant.now();
        long microsSinceEpoch = TimeUnit.SECONDS.toMicros(inst.getEpochSecond()) + TimeUnit.NANOSECONDS.toMicros(inst.getNano());

        String typeSchemaString =
                "{" +
                        "  \"type\" : \"long\"," +
                        "  \"connect.version\" : 1," +
                        "  \"connect.name\" : \"" + CustomKafkaConnectLogicalType.LOGICAL_NAME + "\"," +
                        "  \"logicalType\" : \"timestamp-micros\"" +
                        "}";
        org.apache.avro.Schema expectedAvroSchema = new org.apache.avro.Schema.Parser().parse(typeSchemaString);

        Schema connectSchema = CustomKafkaConnectLogicalType.builder().build();


        SchemaAndValue connectValue = new SchemaAndValue(connectSchema, microsSinceEpoch);
        AvroData avroData = new AvroData(0);
        //noinspection unchecked
        NonRecordContainer<Object> result = (NonRecordContainer<Object>) avroData.fromConnectData(connectValue.schema(), connectValue.value());

        Assertions.assertEquals(expectedAvroSchema, result.getSchema());
        Assertions.assertEquals(LogicalTypes.timestampMicros(), result.getSchema().getLogicalType());
        Assertions.assertEquals(microsSinceEpoch, (long) result.getValue());
    }

}
