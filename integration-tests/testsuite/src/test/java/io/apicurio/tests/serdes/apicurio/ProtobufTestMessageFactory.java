/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.tests.serdes.apicurio;

import java.io.InputStream;
import java.util.Date;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Timestamp;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;

import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.tests.protobuf.Header;
import io.apicurio.tests.protobuf.Point;
import io.apicurio.tests.protobuf.ProtobufTestMessage;

/**
 * @author Fabian Martinez
 */
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

    public byte[] generateSchemaBytes() {
        return IoUtil.toBytes(generateSchemaStream());
    }

    public InputStream generateArtificialSchemaStream() {
        ProtoFileElement element = FileDescriptorUtils.fileDescriptorToProtoFile(ProtobufTestMessage.newBuilder().build().getDescriptorForType().getFile().toProto());
        return IoUtil.toStream(element.toSchema());
    }

}
