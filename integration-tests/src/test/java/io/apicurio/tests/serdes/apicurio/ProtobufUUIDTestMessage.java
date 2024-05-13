package io.apicurio.tests.serdes.apicurio;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import io.apicurio.tests.common.serdes.proto.TestCmmn;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;

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

    public ProtoFileElement generateSchema() {
        return FileDescriptorUtils.fileDescriptorToProtoFile(TestCmmn.UUID.newBuilder().build().getDescriptorForType().getFile().toProto());
    }

    public InputStream generateSchemaStream() {
        return IoUtil.toStream(generateSchema().toSchema());
    }

    public byte[] generateSchemaBytes() {
        return IoUtil.toBytes(generateSchema().toSchema());
    }

    public String generateSchemaString() {
        return IoUtil.toString(generateSchemaBytes());
    }

}
