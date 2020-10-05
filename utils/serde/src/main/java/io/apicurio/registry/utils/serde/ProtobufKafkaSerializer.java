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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.kafka.common.header.Headers;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.common.proto.Serde;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.serde.strategy.ArtifactIdStrategy;
import io.apicurio.registry.utils.serde.strategy.GlobalIdStrategy;

/**
 * @author Ales Justin
 * @author Hiram Chirino
 */
public class ProtobufKafkaSerializer<U extends Message> extends AbstractKafkaSerializer<byte[], U, ProtobufKafkaSerializer<U>> {
    public ProtobufKafkaSerializer() {
    }

    public ProtobufKafkaSerializer(RegistryRestClient client) {
        super(client);
    }

    public ProtobufKafkaSerializer(RegistryRestClient client, ArtifactIdStrategy<byte[]> artifactIdStrategy, GlobalIdStrategy<byte[]> idStrategy) {
        super(client, artifactIdStrategy, idStrategy);
    }

    @Override
    protected byte[] toSchema(U data) {
        Serde.Schema schema = toSchemaProto(data.getDescriptorForType().getFile());
        // Convert to a byte[]
        return schema.toByteArray();
    }

    private Serde.Schema toSchemaProto(Descriptors.FileDescriptor file) {
        Serde.Schema.Builder b = Serde.Schema.newBuilder();
        b.setFile(file.toProto());
        for (Descriptors.FileDescriptor d : file.getDependencies()) {
            b.addImport(toSchemaProto(d));
        }
        return b.build();
    }

    @Override
    protected ArtifactType artifactType() {
        return ArtifactType.PROTOBUF_FD;
    }

    @Override
    protected void serializeData(byte[] schema, U data, OutputStream out) throws IOException {
        Serde.Ref ref = Serde.Ref.newBuilder()
                                 .setName(data.getDescriptorForType().getName())
                                 .build();
        ref.writeDelimitedTo(out);
        data.writeTo(out);
    }

    @Override
    protected void serializeData(Headers headers, byte[] schema, U data, ByteArrayOutputStream out) throws IOException {
        serializeData(schema, data, out);
    }
}
