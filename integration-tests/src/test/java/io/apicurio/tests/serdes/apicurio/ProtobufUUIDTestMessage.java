package io.apicurio.tests.serdes.apicurio;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchemaUtils;
import io.apicurio.tests.common.serdes.proto.TestCmmn;

import java.io.InputStream;
import java.util.Date;

public class ProtobufUUIDTestMessage {

    public TestCmmn.UUID generateMessage(int count) {
        return TestCmmn.UUID.newBuilder().setLsb(321).setMsb(new Date().getTime()).build();
    }

    public boolean validateMessage(DynamicMessage message) {
        Descriptors.Descriptor descriptor = message.getDescriptorForType();
        Object lsb = message.getField(descriptor.findFieldByName("lsb"));

        Object msb = message.getField(descriptor.findFieldByName("msb"));

        return lsb != null && msb != null;
    }

    public boolean validateTypeMessage(TestCmmn.UUID message) {
        return message.getLsb() == 321L && message.getMsb() > 0;
    }

    public String generateSchema() {
        try {
            Descriptors.FileDescriptor fileDescriptor = TestCmmn.UUID.newBuilder().build()
                    .getDescriptorForType().getFile();
            return ProtobufSchemaUtils.toProtoText(fileDescriptor);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate schema", e);
        }
    }

    public InputStream generateSchemaStream() {
        return IoUtil.toStream(generateSchema());
    }

    public byte[] generateSchemaBytes() {
        return IoUtil.toBytes(generateSchema());
    }

    public String generateSchemaString() {
        return IoUtil.toString(generateSchemaBytes());
    }

}
