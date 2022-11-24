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

package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Message;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.ParsedSchemaImpl;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;
import org.apache.kafka.common.errors.SerializationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Fabian Martinez
 */
public class ProtobufSchemaParser<U extends Message> implements SchemaParser<ProtobufSchema, U> {

    /**
     * @see io.apicurio.registry.serde.SchemaParser#artifactType()
     */
    @Override
    public String artifactType() {
        return ArtifactType.PROTOBUF;
    }

    /**
     * @see io.apicurio.registry.serde.SchemaParser#parseSchema(byte[])
     */
    @Override
    public ProtobufSchema parseSchema(byte[] rawSchema, Map<String, ParsedSchema<ProtobufSchema>> resolvedReferences) {
        try {
            //textual .proto file
            ProtoFileElement fileElem = ProtoParser.Companion.parse(FileDescriptorUtils.DEFAULT_LOCATION, IoUtil.toString(rawSchema));
            Map<String, ProtoFileElement> dependencies = new HashMap<>();
            resolvedReferences.forEach((key, value) -> {
                dependencies.put(key, value.getParsedSchema().getProtoFileElement());
                if (value.hasReferences()) {
                    addReferencesToDependencies(value.getSchemaReferences(), dependencies);
                }
            });
            MessageElement firstMessage = FileDescriptorUtils.firstMessage(fileElem);
            if (firstMessage != null) {
                try {
                    final Descriptors.Descriptor fileDescriptor = FileDescriptorUtils.toDescriptor(firstMessage.getName(), fileElem, dependencies);
                    return new ProtobufSchema(fileDescriptor.getFile(), fileElem);
                } catch (IllegalStateException ise) {
                    //If we fail to init the dynamic schema, try to get the descriptor from the proto element
                    return getFileDescriptorFromElement(fileElem);
                }
            } else {
                return getFileDescriptorFromElement(fileElem);
            }
        } catch (DescriptorValidationException pe) {
            throw new SerializationException("Error parsing protobuf schema ", pe);
        }
    }

    private ProtobufSchema getFileDescriptorFromElement(ProtoFileElement fileElem) throws DescriptorValidationException {
        FileDescriptor fileDescriptor = FileDescriptorUtils.protoFileToFileDescriptor(fileElem);
        return new ProtobufSchema(fileDescriptor, fileElem);
    }

    private void addReferencesToDependencies
            (List<ParsedSchema<ProtobufSchema>> schemaReferences, Map<String, ProtoFileElement> dependencies) {
        schemaReferences.forEach(parsedSchema -> {
            dependencies.put(parsedSchema.referenceName(), parsedSchema.getParsedSchema().getProtoFileElement());
            if (parsedSchema.hasReferences()) {
                addReferencesToDependencies(parsedSchema.getSchemaReferences(), dependencies);
            }
        });
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaParser#getSchemaFromData(java.lang.Object)
     */
    @Override
    public ParsedSchema<ProtobufSchema> getSchemaFromData(Record<U> data) {
        FileDescriptor schemaFileDescriptor = data.payload().getDescriptorForType().getFile();
        ProtoFileElement protoFileElement = toProtoFileElement(schemaFileDescriptor);
        ProtobufSchema protobufSchema = new ProtobufSchema(schemaFileDescriptor, protoFileElement);

        byte[] rawSchema = IoUtil.toBytes(protoFileElement.toSchema());

        return new ParsedSchemaImpl<ProtobufSchema>()
                .setParsedSchema(protobufSchema)
                .setReferenceName(protobufSchema.getFileDescriptor().getName())
                .setSchemaReferences(handleDependencies(schemaFileDescriptor))
                .setRawSchema(rawSchema);
    }

    private List<ParsedSchema<ProtobufSchema>> handleDependencies(FileDescriptor fileDescriptor) {
        List<ParsedSchema<ProtobufSchema>> schemaReferences = new ArrayList<>();
        fileDescriptor.getDependencies().forEach(referenceFileDescriptor -> {

            ProtoFileElement referenceProtoFileElement = toProtoFileElement(referenceFileDescriptor);
            ProtobufSchema referenceProtobufSchema = new ProtobufSchema(referenceFileDescriptor, referenceProtoFileElement);

            byte[] rawSchema = IoUtil.toBytes(referenceProtoFileElement.toSchema());

            ParsedSchema<ProtobufSchema> referencedSchema = new ParsedSchemaImpl<ProtobufSchema>()
                    .setParsedSchema(referenceProtobufSchema)
                    .setReferenceName(referenceProtobufSchema.getFileDescriptor().getName())
                    .setSchemaReferences(handleDependencies(referenceFileDescriptor))
                    .setRawSchema(rawSchema);
            schemaReferences.add(referencedSchema);
        });

        return schemaReferences;
    }

    /**
     * This method converts the Descriptor to a ProtoFileElement that allows to get a textual representation .proto file
     *
     * @param fileDescriptor
     * @return textual protobuf representation
     */
    public ProtoFileElement toProtoFileElement(FileDescriptor fileDescriptor) {
        return FileDescriptorUtils.fileDescriptorToProtoFile(fileDescriptor.toProto());
    }
}
