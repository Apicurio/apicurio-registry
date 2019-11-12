/*
 * Copyright 2019 Red Hat
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

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import io.apicurio.registry.client.RegistryService;

import java.nio.ByteBuffer;
import javax.ws.rs.core.Response;

/**
 * @author Ales Justin
 */
public class ProtobufKafkaDeserializer extends AbstractKafkaDeserializer<byte[], DynamicMessage> {
    public ProtobufKafkaDeserializer() {
    }

    public ProtobufKafkaDeserializer(RegistryService client) {
        super(client);
    }

    @Override
    protected byte[] toSchema(Response response) {
        return response.readEntity(byte[].class);
    }

    // TODO -- work-in-progress, do not use yet !!!

    @Override
    protected DynamicMessage readData(byte[] schema, ByteBuffer buffer, int start, int length) {
        try {
            DescriptorProtos.DescriptorProto proto = DescriptorProtos.DescriptorProto.parseFrom(schema);
            Descriptors.Descriptor descriptor = proto.getDescriptorForType(); // TODO -- wrong descriptor!
            byte[] bytes = new byte[length];
            System.arraycopy(buffer.array(), start, bytes, 0, length);
            return DynamicMessage.parseFrom(descriptor, bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
        }
    }
}
