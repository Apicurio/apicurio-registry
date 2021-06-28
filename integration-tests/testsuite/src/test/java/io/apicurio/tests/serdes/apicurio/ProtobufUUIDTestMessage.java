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
import com.squareup.wire.schema.internal.parser.ProtoFileElement;

import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.tests.common.serdes.proto.TestCmmn;

/**
 * @author Fabian Martinez
 */
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

}
