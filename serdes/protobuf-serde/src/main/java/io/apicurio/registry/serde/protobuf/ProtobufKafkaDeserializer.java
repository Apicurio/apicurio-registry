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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.header.Headers;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import io.apicurio.registry.common.proto.Serde;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractKafkaDeserializer;
import io.apicurio.registry.serde.ParsedSchema;
import io.apicurio.registry.serde.SchemaResolver;
import io.apicurio.registry.serde.utils.HeaderUtils;
import io.apicurio.registry.types.ArtifactType;

/**
 * @author Ales Justin
 * @author Hiram Chirino
 * @author Fabian Martinez
 */
public class ProtobufKafkaDeserializer extends AbstractKafkaDeserializer<Descriptors.FileDescriptor, DynamicMessage, ProtobufKafkaDeserializer> {

    public ProtobufKafkaDeserializer() {
        super();
    }

    public ProtobufKafkaDeserializer(RegistryClient client,
            SchemaResolver<FileDescriptor, DynamicMessage> schemaResolver) {
        super(client, schemaResolver);
    }

    public ProtobufKafkaDeserializer(RegistryClient client) {
        super(client);
    }

    public ProtobufKafkaDeserializer(SchemaResolver<FileDescriptor, DynamicMessage> schemaResolver) {
        super(schemaResolver);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);
        // Always add headerUtils, so consumer can read both formats i.e. id stored in header or magic byte
        headerUtils = new HeaderUtils((Map<String, Object>) configs, isKey);
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
    public Descriptors.FileDescriptor parseSchema(byte[] rawSchema) {
        try {
            Serde.Schema s = Serde.Schema.parseFrom(rawSchema);
            return toFileDescriptor(s);
        } catch (IOException | Descriptors.DescriptorValidationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaDeserializer#readData(io.apicurio.registry.serde.ParsedSchema, java.nio.ByteBuffer, int, int)
     */
    @Override
    protected DynamicMessage readData(ParsedSchema<Descriptors.FileDescriptor> schema, ByteBuffer buffer, int start, int length) {
        try {
            byte[] bytes = new byte[length];
            System.arraycopy(buffer.array(), start, bytes, 0, length);
            ByteArrayInputStream is = new ByteArrayInputStream(bytes);

            Serde.Ref ref = Serde.Ref.parseDelimitedFrom(is);

            Descriptors.Descriptor descriptor = schema.getParsedSchema().findMessageTypeByName(ref.getName());
            return DynamicMessage.parseFrom(descriptor, is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaDeserializer#readData(org.apache.kafka.common.header.Headers, io.apicurio.registry.serde.ParsedSchema, java.nio.ByteBuffer, int, int)
     */
    @Override
    protected DynamicMessage readData(Headers headers, ParsedSchema<Descriptors.FileDescriptor> schema, ByteBuffer buffer, int start, int length) {
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
