package io.apicurio.tests.serdes.apicurio;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Timestamp;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.tests.protobuf.Header;
import io.apicurio.tests.protobuf.Point;
import io.apicurio.tests.protobuf.ProtobufTestMessage;

import java.io.InputStream;
import java.util.Date;

public class ProtobufTestMessageFactory {

    public ProtobufTestMessage generateMessage(int count) {

        Date now = new Date();
        return ProtobufTestMessage.newBuilder()
                .setBi1(1)
                .setD1(now.getTime())
                .setI1(123)
                .setS1("a")
                .setHeader(Header.newBuilder().setTime(Timestamp.getDefaultInstance()).build())
                .setPoint(Point.newBuilder().setAltitude(1).setLatitude(22).setLongitude(22).build())
                .build();
    }

    public boolean validateDynamicMessage(DynamicMessage dm) {
        Descriptors.Descriptor descriptor = dm.getDescriptorForType();
        Descriptors.FieldDescriptor fieldI1 = descriptor.findFieldByName("i1");
        Object i1 = dm.getField(fieldI1);
        return i1 != null && ((Integer)i1).intValue() == 123;
    }

    public boolean validateMessage(ProtobufTestMessage msg) {
        return msg.getI1() == 123;
    }

    public InputStream generateSchemaStream() {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream("serdes/testmessage.proto");
    }

    public String generateSchemaString() {
        return IoUtil.toString(generateSchemaStream());
    }

    public byte[] generateSchemaBytes() {
        return IoUtil.toBytes(generateSchemaStream());
    }

    public InputStream generateArtificialSchemaStream() {
        ProtoFileElement element = FileDescriptorUtils.fileDescriptorToProtoFile(ProtobufTestMessage.newBuilder().build().getDescriptorForType().getFile().toProto());
        return IoUtil.toStream(element.toSchema());
    }

    public String generateArtificialSchemaString() {
        return IoUtil.toString(generateArtificialSchemaStream());
    }

}
