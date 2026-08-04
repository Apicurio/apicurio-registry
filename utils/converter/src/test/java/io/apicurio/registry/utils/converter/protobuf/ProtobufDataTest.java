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
}
