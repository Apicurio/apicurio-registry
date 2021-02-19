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

package io.apicurio.registry.serde.protobuf;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Map;

import org.apache.kafka.common.header.Headers;

import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import io.apicurio.registry.common.proto.Serde;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractKafkaSerializer;
import io.apicurio.registry.serde.ParsedSchema;
import io.apicurio.registry.serde.SchemaResolver;
import io.apicurio.registry.serde.strategy.ArtifactResolverStrategy;
import io.apicurio.registry.types.ArtifactType;

/**
 * @author Ales Justin
 * @author Hiram Chirino
 * @author Fabian Martinez
 */
public class ProtobufKafkaSerializer<U extends Message> extends AbstractKafkaSerializer<Serde.Schema, U> {

    public ProtobufKafkaSerializer() {
        super();
    }

    public ProtobufKafkaSerializer(RegistryClient client,
            ArtifactResolverStrategy<Serde.Schema> artifactResolverStrategy,
            SchemaResolver<Serde.Schema, U> schemaResolver) {
        super(client, artifactResolverStrategy, schemaResolver);
    }

    public ProtobufKafkaSerializer(RegistryClient client) {
        super(client);
    }

    public ProtobufKafkaSerializer(SchemaResolver<Serde.Schema, U> schemaResolver) {
        super(schemaResolver);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);
    }

    /**
     * @see io.apicurio.registry.serde.SchemaParser#artifactType()
     */
    @Override
    public ArtifactType artifactType() {
        return ArtifactType.PROTOBUF_FD;
    }

    /**
     * @see io.apicurio.registry.serde.SchemaParser#parseSchema(byte[])
     */
    @Override
    public Serde.Schema parseSchema(byte[] rawSchema) {
        try {
            return Serde.Schema.parseFrom(rawSchema);
        } catch (InvalidProtocolBufferException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#getSchemaFromData(java.lang.Object)
     */
    @Override
    protected ParsedSchema<Serde.Schema> getSchemaFromData(U data) {
        Serde.Schema schema = toSchemaProto(data.getDescriptorForType().getFile());
        return new ParsedSchema<Serde.Schema>()
                .setParsedSchema(schema)
                .setRawSchema(schema.toByteArray());
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#serializeData(java.lang.Object, java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(ParsedSchema<Serde.Schema> schema, U data, OutputStream out) throws IOException {
        Serde.Ref ref = Serde.Ref.newBuilder()
                                 .setName(data.getDescriptorForType().getName())
                                 .build();
        ref.writeDelimitedTo(out);
        data.writeTo(out);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#serializeData(org.apache.kafka.common.header.Headers, java.lang.Object, java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(Headers headers, ParsedSchema<Serde.Schema> schema, U data, OutputStream out) throws IOException {
        serializeData(schema, data, out);
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
