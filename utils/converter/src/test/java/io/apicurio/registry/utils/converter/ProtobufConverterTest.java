package io.apicurio.registry.utils.converter;

import com.google.protobuf.DynamicMessage;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.utils.converter.protobuf.ProtobufData;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.storage.Converter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ProtobufConverter}.
 *
 * <p>Note: these tests exercise {@link ProtobufData} directly (not via the full Kafka Connect
 * serialization pipeline) to avoid a dependency on a running schema registry. The registry-wired
 * path is covered by the integration-tests module. The one exception is the null-tombstone test,
 * which goes through the real {@link SerdeBasedConverter} pipeline — safe without a registry
 * because null values short-circuit before any registry call.
 */
public class ProtobufConverterTest {

    @Test
    public void testConverterIsRegisteredAsServiceProvider() {
        boolean found = false;
        for (Converter converter : ServiceLoader.load(Converter.class)) {
            if (converter instanceof ProtobufConverter) {
                found = true;
                break;
            }
        }

        assertTrue(found, "ProtobufConverter must be registered for Kafka Connect discovery");
    }

    @Test
    public void testBasicStructRoundTrip() {
        ProtobufData protobufData = new ProtobufData();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.converter.BasicStruct")
                .field("id", Schema.INT32_SCHEMA)
                .field("name", Schema.STRING_SCHEMA)
                .field("active", Schema.BOOLEAN_SCHEMA)
                .build();
        Struct struct = new Struct(schema)
                .put("id", 1)
                .put("name", "Turing")
                .put("active", true);

        SchemaAndValue result = protobufData.toConnectData(protobufData.fromConnectData(schema, struct));
        Struct resultStruct = assertInstanceOf(Struct.class, result.value());

        assertEquals(1, resultStruct.getInt32("id"));
        assertEquals("Turing", resultStruct.getString("name"));
        assertEquals(true, resultStruct.getBoolean("active"));
    }

    @Test
    public void testNullOptionalFieldsRoundTripAsNull() {
        ProtobufData protobufData = new ProtobufData();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.converter.Nullable")
                .field("mandatory", Schema.STRING_SCHEMA)
                .field("optional_score", SchemaBuilder.int32().optional().build())
                .build();
        Struct struct = new Struct(schema)
                .put("mandatory", "present")
                .put("optional_score", null);

        SchemaAndValue result = protobufData.toConnectData(protobufData.fromConnectData(schema, struct));
        Struct resultStruct = assertInstanceOf(Struct.class, result.value());

        assertEquals("present", resultStruct.getString("mandatory"));
        assertNull(resultStruct.get("optional_score"),
                "null optional int must round-trip as null, not 0");
    }

    @Test
    public void testSpecialCharacterFieldNamesRoundTrip() {
        ProtobufData protobufData = new ProtobufData();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.converter.SpecialNames")
                .field("user-id", Schema.STRING_SCHEMA)
                .field("event.type", Schema.STRING_SCHEMA)
                .build();
        Struct struct = new Struct(schema)
                .put("user-id", "u99")
                .put("event.type", "click");

        SchemaAndValue result = protobufData.toConnectData(protobufData.fromConnectData(schema, struct));
        Struct resultStruct = assertInstanceOf(Struct.class, result.value(), "round-trip must produce a Struct");

        assertEquals("u99", resultStruct.get("user-id"),
                "'user-id' field must be accessible by its original name after round-trip");
        assertEquals("click", resultStruct.get("event.type"),
                "'event.type' field must be accessible by its original name after round-trip");
    }

    @Test
    public void testInt8AndInt16TypesPreservedOnRoundTrip() {
        ProtobufData protobufData = new ProtobufData();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.converter.NarrowTypes")
                .field("tiny", Schema.INT8_SCHEMA)
                .field("small", Schema.INT16_SCHEMA)
                .build();
        Struct struct = new Struct(schema)
                .put("tiny", (byte) 7)
                .put("small", (short) 300);

        SchemaAndValue result = protobufData.toConnectData(protobufData.fromConnectData(schema, struct));
        Struct resultStruct = assertInstanceOf(Struct.class, result.value());

        assertEquals(Schema.Type.INT8, result.schema().field("tiny").schema().type(),
                "INT8 type must be preserved after round-trip");
        assertEquals(Schema.Type.INT16, result.schema().field("small").schema().type(),
                "INT16 type must be preserved after round-trip");
        assertEquals((byte) 7, ((Number) resultStruct.get("tiny")).byteValue());
        assertEquals((short) 300, ((Number) resultStruct.get("small")).shortValue());
    }

    @Test
    public void testArrayFieldRoundTrip() {
        ProtobufData protobufData = new ProtobufData();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.converter.WithArray")
                .field("tags", SchemaBuilder.array(Schema.STRING_SCHEMA).build())
                .build();
        Struct struct = new Struct(schema).put("tags", List.of("a", "b", "c"));

        SchemaAndValue result = protobufData.toConnectData(protobufData.fromConnectData(schema, struct));
        Struct resultStruct = assertInstanceOf(Struct.class, result.value());

        assertEquals(List.of("a", "b", "c"), resultStruct.getArray("tags"));
    }

    @Test
    public void testMapFieldRoundTrip() {
        ProtobufData protobufData = new ProtobufData();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.converter.WithMap")
                .field("counts", SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.INT64_SCHEMA).build())
                .build();
        Struct struct = new Struct(schema).put("counts", Map.of("errors", 3L, "warnings", 12L));

        SchemaAndValue result = protobufData.toConnectData(protobufData.fromConnectData(schema, struct));
        Struct resultStruct = assertInstanceOf(Struct.class, result.value());

        assertEquals(Map.of("errors", 3L, "warnings", 12L), resultStruct.getMap("counts"));
    }

    @Test
    public void testNullValueRoundTripsThroughConverterPipeline() {
        ProtobufConverter<DynamicMessage> converter = new ProtobufConverter<>();
        try {
            converter.configure(
                    Map.of(SerdeConfig.REGISTRY_URL, "http://localhost:8080/apis/registry/v3"), false);
            Schema schema = SchemaBuilder.struct()
                    .name("io.apicurio.registry.test.converter.NullValue")
                    .field("id", Schema.INT32_SCHEMA)
                    .build();

            byte[] serialized = converter.fromConnectData("test-topic", schema, null);
            assertNull(serialized,
                    "null Connect value must serialize to null bytes (Kafka tombstone), not throw");

            SchemaAndValue deserialized = converter.toConnectData("test-topic", null);
            assertEquals(SchemaAndValue.NULL, deserialized,
                    "null bytes must deserialize to SchemaAndValue.NULL");

            byte[] serializedWithHeaders = converter.fromConnectData("test-topic",
                    new RecordHeaders(), schema, null);
            assertNull(serializedWithHeaders,
                    "null Connect value must serialize to null bytes via the Headers overload");

            SchemaAndValue deserializedWithHeaders = converter.toConnectData("test-topic",
                    new RecordHeaders(), null);
            assertEquals(SchemaAndValue.NULL, deserializedWithHeaders,
                    "null bytes must deserialize to SchemaAndValue.NULL via the Headers overload");
        } finally {
            converter.close();
        }
    }

    @Test
    public void testNestedCollectionMapOfArrayFullRoundTrip() {
        ProtobufData protobufData = new ProtobufData();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.converter.NestedColl")
                .field("tagsByGroup",
                        SchemaBuilder.map(Schema.STRING_SCHEMA,
                                SchemaBuilder.array(Schema.STRING_SCHEMA).build()).build())
                .build();
        Struct struct = new Struct(schema)
                .put("tagsByGroup", Map.of("backend", List.of("java", "go")));

        SchemaAndValue result = protobufData.toConnectData(protobufData.fromConnectData(schema, struct));
        Struct resultStruct = assertInstanceOf(Struct.class, result.value());

        Schema tagsByGroup = result.schema().field("tagsByGroup").schema();
        assertEquals(Schema.Type.MAP, tagsByGroup.type(), "tagsByGroup must decode as MAP");
        assertEquals(Schema.Type.ARRAY, tagsByGroup.valueSchema().type(),
                "map value must decode as ARRAY after wrapper unwrap");

        Map<?, ?> decoded = resultStruct.getMap("tagsByGroup");
        assertEquals(List.of("java", "go"), decoded.get("backend"),
                "map-of-array values must be a List, not struct{items:[...]}");
    }

}
