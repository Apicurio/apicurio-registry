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
import com.google.protobuf.Message;
import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.serde.strategy.ArtifactIdStrategy;
import io.apicurio.registry.utils.serde.strategy.IdStrategy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * @author Ales Justin
 */
public class ProtobufKafkaSerializer<U extends Message> extends AbstractKafkaSerializer<byte[], U> {
    public ProtobufKafkaSerializer(RegistryService client) {
        super(client);
    }

    public ProtobufKafkaSerializer(RegistryService client, ArtifactIdStrategy<byte[]> artifactIdStrategy, IdStrategy<byte[]> idStrategy) {
        super(client, artifactIdStrategy, idStrategy);
    }

    @Override
    protected byte[] toSchema(U data) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Descriptors.Descriptor descriptor = data.getDescriptorForType();
            DescriptorProtos.DescriptorProto proto = descriptor.toProto();
            proto.writeTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected ArtifactType artifactType() {
        return ArtifactType.PROTOBUF;
    }

    @Override
    protected void serializeData(byte[] schema, U data, ByteArrayOutputStream out) throws IOException {
        data.writeTo(out);
    }
}
