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

import io.apicurio.registry.common.proto.Serde;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.tests.common.serdes.proto.TestCmmn;

/**
 * @author Fabian Martinez
 */
public class ProtobufUUIDTestMessage {

    public TestCmmn.UUID generateMessage(int count) {
        return TestCmmn.UUID.newBuilder().setLsb(new Date().getTime()).setMsb(new Date().getTime()).build();
    }

    public boolean validateMessage(DynamicMessage message) {
        Descriptors.Descriptor descriptor = message.getDescriptorForType();
        Object lsb = message.getField(descriptor.findFieldByName("lsb"));

        Object msb = message.getField(descriptor.findFieldByName("msb"));

        return lsb != null && msb != null;
    }

    public Serde.Schema generateSchema() {
        return toSchemaProto(
                TestCmmn.UUID.newBuilder().build().getDescriptorForType().getFile());
    }

    public InputStream generateSchemaStream() {
        return IoUtil.toStream(generateSchema().toByteArray());
    }

    public byte[] generateSchemaBytes() {
        return generateSchema().toByteArray();
    }

    private Serde.Schema toSchemaProto(Descriptors.FileDescriptor file) {
        Serde.Schema.Builder b = Serde.Schema.newBuilder();
        b.setFile(file.toProto());
        for (Descriptors.FileDescriptor d : file.getDependencies()) {
            b.addImport(toSchemaProto(d));
        }
        return b.build();
    }

}
