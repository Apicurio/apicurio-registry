package io.apicurio.registry.utils.converter.avro;

import io.apicurio.registry.serde.avro.NonRecordContainer;
import org.apache.avro.LogicalTypes;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.kafka.connect.data.Decimal;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void testConnectSchemaEqualsConsidersParameters() {
        Schema schema1 = SchemaBuilder.string()
                .name("io.debezium.data.Enum")
                .version(1)
                .parameter("allowed", "station,post_office")
                .build();

        Schema schema2 = SchemaBuilder.string()
                .name("io.debezium.data.Enum")
                .version(1)
                .parameter("allowed", "station,post_office,plane")
                .build();

        assertNotEquals(schema1, schema2,
                "ConnectSchema.equals() must distinguish schemas with different parameters");
        assertNotEquals(schema1.hashCode(), schema2.hashCode(),
                "ConnectSchema.hashCode() must differ for schemas with different parameters");
    }

    @Test
    void testConnectSchemaEqualsConsidersDefaultValue() {
        Schema schema1 = SchemaBuilder.int32().defaultValue(0).build();
        Schema schema2 = SchemaBuilder.int32().defaultValue(1).build();

        assertNotEquals(schema1, schema2,
                "ConnectSchema.equals() must distinguish schemas with different default values");
    }

    @Test
    void testCacheDistinguishesByParameters() {
        AvroData avroData = new AvroData(5);

        Schema schemaWithParam1 = SchemaBuilder.struct()
                .name("io.debezium.data.VariableScaleDecimal")
                .version(1)
                .field("scale", Schema.INT32_SCHEMA)
                .field("value", Schema.BYTES_SCHEMA)
                .parameter("precision", "10")
                .optional()
                .build();

        Schema schemaWithParam2 = SchemaBuilder.struct()
                .name("io.debezium.data.VariableScaleDecimal")
                .version(1)
                .field("scale", Schema.INT32_SCHEMA)
                .field("value", Schema.BYTES_SCHEMA)
                .parameter("precision", "15")
                .optional()
                .build();

        org.apache.avro.Schema avro1 = avroData.fromConnectSchema(schemaWithParam1);
        org.apache.avro.Schema avro2 = avroData.fromConnectSchema(schemaWithParam2);

        assertNotEquals(avro1, avro2,
                "Avro schemas from Connect schemas with different parameters must differ");

        assertSame(avro1, avroData.fromConnectSchema(schemaWithParam1),
                "Cache must return same instance for identical Connect schema");
        assertSame(avro2, avroData.fromConnectSchema(schemaWithParam2),
                "Cache must return same instance for identical Connect schema");
    }

    @Test
    void testDebeziumEnvelopeWithSameNamedFieldsDifferentParameters() {
        AvroData avroData = new AvroData(5);

        Schema enumField1 = SchemaBuilder.string()
                .name("io.debezium.data.Enum")
                .version(1)
                .parameter("allowed", "station,post_office")
                .optional()
                .build();

        Schema enumField2 = SchemaBuilder.string()
                .name("io.debezium.data.Enum")
                .version(1)
                .parameter("allowed", "station,post_office,plane")
                .optional()
                .build();

        Schema envelope = SchemaBuilder.struct()
                .name("test_server.public.shipments.Envelope")
                .field("before", SchemaBuilder.struct()
                        .name("test_server.public.shipments.Value")
                        .field("id", Schema.INT32_SCHEMA)
                        .field("status", enumField1)
                        .optional()
                        .build())
                .field("after", SchemaBuilder.struct()
                        .name("test_server.public.shipments.Value")
                        .field("id", Schema.INT32_SCHEMA)
                        .field("status", enumField2)
                        .optional()
                        .build())
                .field("op", Schema.STRING_SCHEMA)
                .build();

        org.apache.avro.Schema avroEnvelope = avroData.fromConnectSchema(envelope);

        assertNotNull(avroEnvelope);
        org.apache.avro.Schema beforeAvro = avroEnvelope.getField("before").schema();
        org.apache.avro.Schema afterAvro = avroEnvelope.getField("after").schema();

        org.apache.avro.Schema beforeStatus = unwrapUnion(beforeAvro).getField("status").schema();
        org.apache.avro.Schema afterStatus = unwrapUnion(afterAvro).getField("status").schema();

        Object beforeParams = unwrapUnion(beforeStatus).getObjectProp("connect.parameters");
        Object afterParams = unwrapUnion(afterStatus).getObjectProp("connect.parameters");

        assertNotNull(beforeParams, "before.status must have connect.parameters");
        assertNotNull(afterParams, "after.status must have connect.parameters");
        String beforeStr = beforeParams.toString();
        String afterStr = afterParams.toString();
        assertTrue(beforeStr.contains("station,post_office"),
                "before.status must have original allowed values, got: " + beforeStr);
        assertTrue(afterStr.contains("plane"),
                "after.status must have its own allowed values including 'plane', got: " + afterStr);
        assertNotEquals(beforeStr, afterStr,
                "before and after status fields must have different connect.parameters");
    }

    @Test
    void testDebeziumVariableScaleDecimalFieldsDifferentPrecision() {
        AvroData avroData = new AvroData(5);

        Schema decField1 = SchemaBuilder.struct()
                .name("io.debezium.data.VariableScaleDecimal")
                .version(1)
                .field("scale", Schema.INT32_SCHEMA)
                .field("value", Schema.BYTES_SCHEMA)
                .parameter("precision", "10")
                .optional()
                .build();

        Schema decField2 = SchemaBuilder.struct()
                .name("io.debezium.data.VariableScaleDecimal")
                .version(1)
                .field("scale", Schema.INT32_SCHEMA)
                .field("value", Schema.BYTES_SCHEMA)
                .parameter("precision", "15")
                .optional()
                .build();

        Schema tableSchema = SchemaBuilder.struct()
                .name("test_server.public.measurements.Value")
                .field("id", Schema.INT32_SCHEMA)
                .field("val_f_10", decField1)
                .field("val_f_15", decField2)
                .build();

        org.apache.avro.Schema avroSchema = avroData.fromConnectSchema(tableSchema);

        assertNotNull(avroSchema);
        org.apache.avro.Schema avroF10 = unwrapUnion(avroSchema.getField("val_f_10").schema());
        org.apache.avro.Schema avroF15 = unwrapUnion(avroSchema.getField("val_f_15").schema());

        Object paramsF10 = avroF10.getObjectProp("connect.parameters");
        Object paramsF15 = avroF15.getObjectProp("connect.parameters");

        assertNotNull(paramsF10, "val_f_10 must have connect.parameters");
        assertNotNull(paramsF15, "val_f_15 must have connect.parameters");
        String paramsF10Str = paramsF10.toString();
        String paramsF15Str = paramsF15.toString();
        assertTrue(paramsF10Str.contains("precision") && paramsF10Str.contains("10"),
                "val_f_10 must have precision=10, got: " + paramsF10Str);
        assertTrue(paramsF15Str.contains("precision") && paramsF15Str.contains("15"),
                "val_f_15 must have precision=15, got: " + paramsF15Str);
    }

    private static org.apache.avro.Schema unwrapUnion(org.apache.avro.Schema schema) {
        if (schema.getType() == org.apache.avro.Schema.Type.UNION) {
            for (org.apache.avro.Schema branch : schema.getTypes()) {
                if (branch.getType() != org.apache.avro.Schema.Type.NULL) {
                    return branch;
                }
            }
        }
        return schema;
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
