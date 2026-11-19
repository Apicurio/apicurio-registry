package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Converting a Protobuf message to the map contract rules are evaluated on, and the output of a TRANSFORM
 * rule back to a message.
 */
class ProtobufContractTransformTest {

    private static Descriptors.Descriptor orderDescriptor() throws Exception {
        DescriptorProtos.DescriptorProto order = DescriptorProtos.DescriptorProto.newBuilder().setName("Order")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder().setName("order_id").setNumber(1)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder().setName("total").setNumber(2)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE))
                .build();
        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("order.proto").addMessageType(order).build();
        return Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0])
                .findMessageTypeByName("Order");
    }

    @Test
    void transformedRecordBecomesAMessageOfTheSameType() throws Exception {
        Descriptors.Descriptor descriptor = orderDescriptor();
        DynamicMessage original = DynamicMessage.newBuilder(descriptor)
                .setField(descriptor.findFieldByName("order_id"), "ord-1")
                .setField(descriptor.findFieldByName("total"), 10.0).build();
        ProtobufSerializer<DynamicMessage> serializer = new ProtobufSerializer<>();

        assertEquals(Map.of("order_id", "ord-1", "total", 10.0), serializer.toRuleRecord(original));

        DynamicMessage transformed = serializer.fromRuleRecord(
                Map.of("order_id", "ORD-1", "total", 12.5), null, original);
        assertEquals(descriptor, transformed.getDescriptorForType());
        assertEquals("ORD-1", transformed.getField(descriptor.findFieldByName("order_id")));
        assertEquals(12.5, transformed.getField(descriptor.findFieldByName("total")));
    }
}
