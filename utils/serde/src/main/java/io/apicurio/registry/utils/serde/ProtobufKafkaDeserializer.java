/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.utils.serde;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.common.header.Headers;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.common.proto.Serde;
import io.apicurio.registry.utils.IoUtil;

/**
 * @author Ales Justin
 * @author Hiram Chirino
 */
public class ProtobufKafkaDeserializer extends AbstractKafkaDeserializer<byte[], DynamicMessage, ProtobufKafkaDeserializer> {
    public ProtobufKafkaDeserializer() {
    }

    public ProtobufKafkaDeserializer(RegistryRestClient client) {
        super(client);
    }

    @Override
    protected byte[] toSchema(InputStream schemaData) {
        return IoUtil.toBytes(schemaData);
    }

    @Override
    protected DynamicMessage readData(byte[] schema, ByteBuffer buffer, int start, int length) {
        try {
            Serde.Schema s = Serde.Schema.parseFrom(schema);
            Descriptors.FileDescriptor fileDescriptor = toFileDescriptor(s);

            byte[] bytes = new byte[length];
            System.arraycopy(buffer.array(), start, bytes, 0, length);
            ByteArrayInputStream is = new ByteArrayInputStream(bytes);

            Serde.Ref ref = Serde.Ref.parseDelimitedFrom(is);

            Descriptors.Descriptor descriptor = fileDescriptor.findMessageTypeByName(ref.getName());
            return DynamicMessage.parseFrom(descriptor, is);
        } catch (IOException | Descriptors.DescriptorValidationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    protected DynamicMessage readData(Headers headers, byte[] schema, ByteBuffer buffer, int start, int length) {
        return readData(schema, buffer, start, length);
    }

    private Descriptors.FileDescriptor toFileDescriptor(Serde.Schema s) throws Descriptors.DescriptorValidationException {
        List<Descriptors.FileDescriptor> imports = new ArrayList<>();
        for (Serde.Schema i : s.getImportList()) {
            imports.add(toFileDescriptor(i));
        }
        return Descriptors.FileDescriptor.buildFrom(s.getFile(), imports.toArray(new Descriptors.FileDescriptor[0]));
    }
}
