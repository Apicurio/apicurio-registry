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
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;

import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Message;

import io.apicurio.registry.common.proto.Serde;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractKafkaSerializer;
import io.apicurio.registry.serde.ParsedSchema;
import io.apicurio.registry.serde.SchemaParser;
import io.apicurio.registry.serde.SchemaResolver;
import io.apicurio.registry.serde.headers.MessageTypeSerdeHeaders;
import io.apicurio.registry.serde.strategy.ArtifactResolverStrategy;

/**
 * @author Ales Justin
 * @author Hiram Chirino
 * @author Fabian Martinez
 */
public class ProtobufKafkaSerializer<U extends Message> extends AbstractKafkaSerializer<FileDescriptor, U> {

    private MessageTypeSerdeHeaders serdeHeaders;
    private ProtobufSchemaParser parser = new ProtobufSchemaParser();

    public ProtobufKafkaSerializer() {
        super();
    }

    public ProtobufKafkaSerializer(RegistryClient client,
            ArtifactResolverStrategy<FileDescriptor> artifactResolverStrategy,
            SchemaResolver<FileDescriptor, U> schemaResolver) {
        super(client, artifactResolverStrategy, schemaResolver);
    }

    public ProtobufKafkaSerializer(RegistryClient client) {
        super(client);
    }

    public ProtobufKafkaSerializer(SchemaResolver<FileDescriptor, U> schemaResolver) {
        super(schemaResolver);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);
        serdeHeaders = new MessageTypeSerdeHeaders(new HashMap<>(configs), isKey);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerDe#schemaParser()
     */
    @Override
    public SchemaParser<FileDescriptor> schemaParser() {
        return parser;
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#getSchemaFromData(java.lang.Object)
     */
    @Override
    protected ParsedSchema<FileDescriptor> getSchemaFromData(U data) {
        byte[] rawSchema = parser.serializeSchema(data.getDescriptorForType());

        return new ParsedSchema<FileDescriptor>()
                .setParsedSchema(data.getDescriptorForType().getFile())
                .setRawSchema(rawSchema);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#serializeData(io.apicurio.registry.serde.ParsedSchema, java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(ParsedSchema<FileDescriptor> schema, U data, OutputStream out) throws IOException {
        serializeData(null, schema, data, out);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#serializeData(org.apache.kafka.common.header.Headers, io.apicurio.registry.serde.ParsedSchema, java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(Headers headers, ParsedSchema<FileDescriptor> schema, U data, OutputStream out) throws IOException {
        if (headers != null) {
            serdeHeaders.addMessageTypeHeader(headers, data.getClass().getName());
        }

        if (schema.getParsedSchema() != null && schema.getParsedSchema().findMessageTypeByName(data.getDescriptorForType().getName()) == null) {
            throw new SerializationException("Missing message type " + data.getDescriptorForType().getName() + " in the protobuf schema");
        }

        Serde.Ref ref = Serde.Ref.newBuilder()
                .setName(data.getDescriptorForType().getName())
                .build();
        ref.writeDelimitedTo(out);
        data.writeTo(out);
    }

}
