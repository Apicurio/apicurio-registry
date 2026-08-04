package io.apicurio.registry.utils.converter.protobuf;

import com.google.protobuf.ByteString;
import com.google.protobuf.DynamicMessage;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ProtobufDataTest {

    @Test
    public void testStructToProtobufMessage() {
        ProtobufData protobufData = new ProtobufData();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.Person")
                .field("id", Schema.INT32_SCHEMA)
                .field("name", Schema.STRING_SCHEMA)
                .field("active", Schema.BOOLEAN_SCHEMA)
                .field("payload", Schema.BYTES_SCHEMA)
                .build();
        Struct struct = new Struct(schema)
                .put("id", 7)
                .put("name", "Ada")
                .put("active", true)
                .put("payload", new byte[] { 1, 2, 3 });

        DynamicMessage message = protobufData.fromConnectData(schema, struct);

        assertEquals("io.apicurio.registry.test.Person", message.getDescriptorForType().getFullName());
        assertEquals(7, message.getField(message.getDescriptorForType().findFieldByName("id")));
        assertEquals("Ada", message.getField(message.getDescriptorForType().findFieldByName("name")));
        assertEquals(true, message.getField(message.getDescriptorForType().findFieldByName("active")));
        assertEquals(ByteString.copyFrom(new byte[] { 1, 2, 3 }),
                message.getField(message.getDescriptorForType().findFieldByName("payload")));
    }

    @Test
    public void testNestedCollectionsRoundTripToConnectData() {
        ProtobufData protobufData = new ProtobufData();
        Schema addressSchema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.Address")
                .field("city", Schema.STRING_SCHEMA)
                .field("zip", Schema.INT32_SCHEMA)
                .build();
        Schema personSchema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.Person")
                .field("name", Schema.STRING_SCHEMA)
                .field("address", addressSchema)
                .field("tags", SchemaBuilder.array(Schema.STRING_SCHEMA).build())
                .field("attributes", SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.INT64_SCHEMA).build())
                .build();
        Struct address = new Struct(addressSchema)
                .put("city", "London")
                .put("zip", 12345);
        Struct person = new Struct(personSchema)
                .put("name", "Ada")
                .put("address", address)
                .put("tags", List.of("engineer", "mathematician"))
                .put("attributes", Map.of("score", 42L));

        DynamicMessage message = protobufData.fromConnectData(personSchema, person);
        SchemaAndValue schemaAndValue = protobufData.toConnectData(message);

        assertEquals("io.apicurio.registry.test.Person", schemaAndValue.schema().name());
        Struct result = assertInstanceOf(Struct.class, schemaAndValue.value());
        assertEquals("Ada", result.getString("name"));
        assertEquals("London", result.getStruct("address").getString("city"));
        assertEquals(12345, result.getStruct("address").getInt32("zip"));
        assertEquals(List.of("engineer", "mathematician"), result.getArray("tags"));
        assertEquals(Map.of("score", 42L), result.getMap("attributes"));
    }

    @Test
    public void testBytesRoundTripToConnectData() {
        ProtobufData protobufData = new ProtobufData();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.Payload")
                .field("data", Schema.BYTES_SCHEMA)
                .build();
        Struct struct = new Struct(schema).put("data", new byte[] { 4, 5, 6 });

        DynamicMessage message = protobufData.fromConnectData(schema, struct);
        SchemaAndValue schemaAndValue = protobufData.toConnectData(message);
        Struct result = assertInstanceOf(Struct.class, schemaAndValue.value());

        assertArrayEquals(new byte[] { 4, 5, 6 }, result.getBytes("data"));
    }

    @Test
    public void testNullOptionalFieldRoundTripsAsNull() {
        ProtobufData protobufData = new ProtobufData();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.NullableFields")
                .field("required_str", Schema.STRING_SCHEMA)
                .field("opt_int", SchemaBuilder.int32().optional().build())
                .field("opt_str", SchemaBuilder.string().optional().build())
                .field("opt_bool", SchemaBuilder.bool().optional().build())
                .build();
        Struct struct = new Struct(schema)
                .put("required_str", "hello")
                .put("opt_int", null)
                .put("opt_str", null)
                .put("opt_bool", null);

        DynamicMessage message = protobufData.fromConnectData(schema, struct);
        SchemaAndValue schemaAndValue = protobufData.toConnectData(message);

        Struct result = assertInstanceOf(Struct.class, schemaAndValue.value());
        assertEquals("hello", result.getString("required_str"));
        assertNull(result.get("opt_int"),
                "null optional int32 must round-trip as null, not 0");
        assertNull(result.get("opt_str"),
                "null optional string must round-trip as null, not \"\"");
        assertNull(result.get("opt_bool"),
                "null optional bool must round-trip as null, not false");
    }

    @Test
    public void testNonNullOptionalFieldRoundTripsCorrectly() {
        ProtobufData protobufData = new ProtobufData();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.NullableSet")
                .field("opt_int", SchemaBuilder.int32().optional().build())
                .build();
        Struct struct = new Struct(schema).put("opt_int", 42);

        DynamicMessage message = protobufData.fromConnectData(schema, struct);
        SchemaAndValue schemaAndValue = protobufData.toConnectData(message);

        Struct result = assertInstanceOf(Struct.class, schemaAndValue.value());
        assertEquals(42, result.get("opt_int"));
    }

    @Test
    public void testSpecialCharFieldNameRoundTrips() {
        ProtobufData protobufData = new ProtobufData();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.SpecialFields")
                .field("user-id", Schema.STRING_SCHEMA)
                .field("first.name", Schema.STRING_SCHEMA)
                .field("score value", Schema.INT32_SCHEMA)
                .build();
        Struct struct = new Struct(schema)
                .put("user-id", "u42")
                .put("first.name", "Ada")
                .put("score value", 100);

        DynamicMessage message = protobufData.fromConnectData(schema, struct);
        assertNotNull(message, "fromConnectData must not throw for special-char field names");

        SchemaAndValue schemaAndValue = protobufData.toConnectData(message);
        assertNotNull(schemaAndValue.value(),
                "toConnectData must produce a value for special-char field schemas");
    }

    @Test
    public void testDescriptorIsCachedAcrossCalls() {
        ProtobufData protobufData = new ProtobufData();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.CacheCheck")
                .field("val", Schema.INT32_SCHEMA)
                .build();
        Struct struct = new Struct(schema).put("val", 1);

        DynamicMessage first = protobufData.fromConnectData(schema, struct);
        DynamicMessage second = protobufData.fromConnectData(schema, struct);

        assertSame(first.getDescriptorForType(), second.getDescriptorForType(),
                "Descriptor must be cached: same Schema object must yield the same Descriptor instance");
    }

    @Test
    public void testInt8TypePreservedOnRoundTrip() {
        ProtobufData protobufData = new ProtobufData();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.NarrowInts")
                .field("tiny", Schema.INT8_SCHEMA)
                .build();
        Struct struct = new Struct(schema).put("tiny", (byte) 42);

        DynamicMessage message = protobufData.fromConnectData(schema, struct);
        SchemaAndValue schemaAndValue = protobufData.toConnectData(message);

        Struct result = assertInstanceOf(Struct.class, schemaAndValue.value());
        Object val = result.get("tiny");
        assertEquals(Schema.Type.INT8, schemaAndValue.schema().field("tiny").schema().type(),
                "Field schema type must be INT8 after round-trip");
        assertEquals((byte) 42, ((Number) val).byteValue(),
                "INT8 value must survive round-trip");
    }

    @Test
    public void testInt16TypePreservedOnRoundTrip() {
        ProtobufData protobufData = new ProtobufData();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.SmallInt")
                .field("small", Schema.INT16_SCHEMA)
                .build();
        Struct struct = new Struct(schema).put("small", (short) 1000);

        DynamicMessage message = protobufData.fromConnectData(schema, struct);
        SchemaAndValue schemaAndValue = protobufData.toConnectData(message);

        Struct result = assertInstanceOf(Struct.class, schemaAndValue.value());
        Object val = result.get("small");
        assertEquals(Schema.Type.INT16, schemaAndValue.schema().field("small").schema().type(),
                "Field schema type must be INT16 after round-trip");
        assertEquals((short) 1000, ((Number) val).shortValue(),
                "INT16 value must survive round-trip");
    }

    @Test
    public void testArrayOfStructRoundTrips() {
        ProtobufData protobufData = new ProtobufData();
        Schema itemSchema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.Item")
                .field("label", Schema.STRING_SCHEMA)
                .build();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.Container")
                .field("items", SchemaBuilder.array(itemSchema).build())
                .build();
        Struct item1 = new Struct(itemSchema).put("label", "alpha");
        Struct item2 = new Struct(itemSchema).put("label", "beta");
        Struct container = new Struct(schema).put("items", List.of(item1, item2));

        DynamicMessage message = protobufData.fromConnectData(schema, container);
        assertNotNull(message, "fromConnectData must not throw for array-of-struct");
        SchemaAndValue schemaAndValue = protobufData.toConnectData(message);
        assertNotNull(schemaAndValue.value(), "toConnectData must produce a value for array-of-struct");
        Struct result = assertInstanceOf(Struct.class, schemaAndValue.value());
        List<?> resultItems = result.getArray("items");
        assertEquals(2, resultItems.size());
    }

    @Test
    public void testMapOfStructRoundTrips() {
        ProtobufData protobufData = new ProtobufData();
        Schema itemSchema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.MapItem")
                .field("score", Schema.INT64_SCHEMA)
                .build();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.MapContainer")
                .field("scores", SchemaBuilder.map(Schema.STRING_SCHEMA, itemSchema).build())
                .build();
        Struct item = new Struct(itemSchema).put("score", 99L);
        Struct container = new Struct(schema).put("scores", Map.of("ada", item));

        DynamicMessage message = protobufData.fromConnectData(schema, container);
        assertNotNull(message, "fromConnectData must not throw for map-of-struct");
        SchemaAndValue schemaAndValue = protobufData.toConnectData(message);
        assertNotNull(schemaAndValue.value(), "toConnectData must produce a value for map-of-struct");
    }

    @Test
    public void testNestedArrayInMapValueRoundTrips() {
        ProtobufData protobufData = new ProtobufData();
        Schema schema = SchemaBuilder.struct()
                .name("io.apicurio.registry.test.TagMap")
                .field("tagsByCategory",
                        SchemaBuilder.map(Schema.STRING_SCHEMA,
                                SchemaBuilder.array(Schema.STRING_SCHEMA).build()).build())
                .build();
        Struct struct = new Struct(schema)
                .put("tagsByCategory", Map.of("lang", List.of("java", "python")));
        DynamicMessage message = protobufData.fromConnectData(schema, struct);
        assertNotNull(message, "fromConnectData must not throw for map-of-array");
    }
}
