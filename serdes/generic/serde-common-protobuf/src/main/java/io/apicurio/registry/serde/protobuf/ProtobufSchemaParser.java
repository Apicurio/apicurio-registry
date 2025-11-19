package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.ParsedSchemaImpl;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchemaUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProtobufSchemaParser<U extends Message> implements SchemaParser<ProtobufSchema, U> {

    /**
     * @see io.apicurio.registry.resolver.SchemaParser#artifactType()
     */
    @Override
    public String artifactType() {
        return ArtifactType.PROTOBUF;
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaParser#parseSchema(byte[], Map<String,
     *      ParsedSchema<ProtobufSchema>>)
     */
    @Override
    public ProtobufSchema parseSchema(byte[] rawSchema,
            Map<String, ParsedSchema<ProtobufSchema>> resolvedReferences) {
        try {
            // Try to parse as textual .proto file using protobuf4j
            String schemaContent = IoUtil.toString(rawSchema);

            // Build dependencies map from resolved references
            Map<String, String> dependencies = new HashMap<>();
            if (!resolvedReferences.isEmpty()) {
                addReferencesToDependencies(new ArrayList<>(resolvedReferences.values()), dependencies);
            }

            // Use protobuf4j to parse and compile the schema
            FileDescriptor fileDescriptor = ProtobufSchemaUtils.parseAndCompile(
                "schema.proto", schemaContent, dependencies);

            // Preserve original .proto text for toProtoText()
            return new ProtobufSchema(fileDescriptor, schemaContent);

        } catch (Exception e) {
            // If parsing as .proto text fails, try parsing as binary FileDescriptorProto
            return parseDescriptor(rawSchema);
        }
    }

    private ProtobufSchema parseDescriptor(byte[] rawSchema) {
        // Try to parse the binary format, in case the server has returned the descriptor format.
        try {
            DescriptorProtos.FileDescriptorProto fileDescriptorProto = DescriptorProtos.FileDescriptorProto
                    .parseFrom(rawSchema);
            FileDescriptor fileDescriptor = FileDescriptorUtils.protoFileToFileDescriptor(fileDescriptorProto);
            // When parsing from binary, we don't have the original .proto text
            return new ProtobufSchema(fileDescriptor);
        } catch (InvalidProtocolBufferException | DescriptorValidationException e) {
            throw new RuntimeException(e);
        }
    }

    private void addReferencesToDependencies(List<ParsedSchema<ProtobufSchema>> schemaReferences,
            Map<String, String> dependencies) {
        schemaReferences.forEach(parsedSchema -> {
            String depContent = parsedSchema.getParsedSchema().toProtoText();
            dependencies.put(parsedSchema.referenceName(), depContent);
            if (parsedSchema.hasReferences()) {
                addReferencesToDependencies(parsedSchema.getSchemaReferences(), dependencies);
            }
        });
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaParser#getSchemaFromData(Record)
     */
    @Override
    public ParsedSchema<ProtobufSchema> getSchemaFromData(Record<U> data) {
        FileDescriptor schemaFileDescriptor = data.payload().getDescriptorForType().getFile();
        ProtobufSchema protobufSchema = new ProtobufSchema(schemaFileDescriptor);

        // Use FileDescriptorProto text format as the raw schema
        byte[] rawSchema = IoUtil.toBytes(protobufSchema.toProtoText());

        return new ParsedSchemaImpl<ProtobufSchema>().setParsedSchema(protobufSchema)
                .setReferenceName(protobufSchema.getFileDescriptor().getName())
                .setSchemaReferences(handleDependencies(schemaFileDescriptor)).setRawSchema(rawSchema);
    }

    @Override
    public ParsedSchema<ProtobufSchema> getSchemaFromData(Record<U> data, boolean dereference) {
        return getSchemaFromData(data);
    }

    private List<ParsedSchema<ProtobufSchema>> handleDependencies(FileDescriptor fileDescriptor) {
        List<ParsedSchema<ProtobufSchema>> schemaReferences = new ArrayList<>();
        fileDescriptor.getDependencies().forEach(referenceFileDescriptor -> {

            ProtobufSchema referenceProtobufSchema = new ProtobufSchema(referenceFileDescriptor);

            // Use FileDescriptorProto text format as the raw schema
            byte[] rawSchema = IoUtil.toBytes(referenceProtobufSchema.toProtoText());

            ParsedSchema<ProtobufSchema> referencedSchema = new ParsedSchemaImpl<ProtobufSchema>()
                    .setParsedSchema(referenceProtobufSchema)
                    .setReferenceName(referenceProtobufSchema.getFileDescriptor().getName())
                    .setSchemaReferences(handleDependencies(referenceFileDescriptor)).setRawSchema(rawSchema);
            schemaReferences.add(referencedSchema);
        });

        return schemaReferences;
    }
}
