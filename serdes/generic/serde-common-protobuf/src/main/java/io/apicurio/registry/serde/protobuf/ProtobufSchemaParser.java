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
import io.apicurio.registry.utils.protobuf.schema.ProtobufWellKnownTypes;

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

        // First, check if raw bytes are already binary protobuf format
        if (ProtobufSchemaUtils.isBinaryDescriptor(rawSchema)) {
            return parseDescriptor(rawSchema, resolvedReferences);
        }

        // Parse as text content
        String schemaContent = IoUtil.toString(rawSchema);

        // Check if it's base64-encoded binary descriptor
        if (ProtobufSchemaUtils.isBase64BinaryDescriptor(schemaContent)) {
            return parseBase64Descriptor(schemaContent, resolvedReferences);
        }

        // Try to parse as textual .proto file using protobuf4j
        try {
            // Build dependencies map from resolved references
            Map<String, String> dependencies = new HashMap<>();
            if (!resolvedReferences.isEmpty()) {
                addReferencesToDependencies(new ArrayList<>(resolvedReferences.values()), dependencies);
            }

            // Use protobuf4j to parse and compile the schema
            // NOTE: protobuf4j handles google.protobuf.* well-known types automatically
            FileDescriptor fileDescriptor = ProtobufSchemaUtils.parseAndCompile(
                "schema.proto", schemaContent, dependencies);

            // Preserve original .proto text for toProtoText()
            return new ProtobufSchema(fileDescriptor, schemaContent);

        } catch (Exception e) {
            // If parsing as .proto text fails, try parsing as binary FileDescriptorProto
            // (fallback for edge cases)
            return parseDescriptor(rawSchema, resolvedReferences);
        }
    }

    /**
     * Parse binary FileDescriptorProto format with resolved references.
     */
    private ProtobufSchema parseDescriptor(byte[] rawSchema, Map<String, ParsedSchema<ProtobufSchema>> resolvedReferences) {
        try {
            DescriptorProtos.FileDescriptorProto fileDescriptorProto = DescriptorProtos.FileDescriptorProto
                    .parseFrom(rawSchema);

            // Build FileDescriptor with dependencies
            FileDescriptor[] dependencies = buildDependencyArray(fileDescriptorProto, resolvedReferences);
            FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto, dependencies);

            // When parsing from binary, we don't have the original .proto text
            return new ProtobufSchema(fileDescriptor);
        } catch (InvalidProtocolBufferException | DescriptorValidationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parse base64-encoded binary FileDescriptorProto format with resolved references.
     */
    private ProtobufSchema parseBase64Descriptor(String base64Content, Map<String, ParsedSchema<ProtobufSchema>> resolvedReferences) {
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(base64Content.trim());
            return parseDescriptor(decoded, resolvedReferences);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid base64 encoding for protobuf descriptor", e);
        }
    }

    /**
     * Build the dependency array for FileDescriptor.buildFrom().
     */
    private FileDescriptor[] buildDependencyArray(DescriptorProtos.FileDescriptorProto proto,
            Map<String, ParsedSchema<ProtobufSchema>> resolvedReferences) {

        // Start with well-known dependencies
        FileDescriptor[] wellKnown = FileDescriptorUtils.baseDependencies();
        Map<String, FileDescriptor> allDeps = new HashMap<>();
        for (FileDescriptor fd : wellKnown) {
            allDeps.put(fd.getName(), fd);
        }

        // Add resolved references
        if (resolvedReferences != null) {
            for (ParsedSchema<ProtobufSchema> ref : resolvedReferences.values()) {
                FileDescriptor fd = ref.getParsedSchema().getFileDescriptor();
                allDeps.put(fd.getName(), fd);

                // Also add by reference name (may differ from FileDescriptor name)
                allDeps.put(ref.referenceName(), fd);
            }
        }

        // Return dependencies in the order they're declared in the proto
        FileDescriptor[] result = new FileDescriptor[proto.getDependencyCount()];
        for (int i = 0; i < proto.getDependencyCount(); i++) {
            String depName = proto.getDependency(i);
            FileDescriptor dep = allDeps.get(depName);
            if (dep == null) {
                // Try to find by base name (without path)
                String baseName = depName.contains("/")
                        ? depName.substring(depName.lastIndexOf('/') + 1)
                        : depName;
                for (FileDescriptor fd : allDeps.values()) {
                    if (fd.getName().endsWith(baseName)) {
                        dep = fd;
                        break;
                    }
                }
            }
            result[i] = dep;
        }

        // Filter out nulls and return
        return java.util.Arrays.stream(result)
                .filter(java.util.Objects::nonNull)
                .toArray(FileDescriptor[]::new);
    }

    private void addReferencesToDependencies(List<ParsedSchema<ProtobufSchema>> schemaReferences,
            Map<String, String> dependencies) {
        schemaReferences.forEach(parsedSchema -> {
            String referenceName = parsedSchema.referenceName();

            // Skip well-known types - protobuf4j handles these internally
            // This is important for backward compatibility with older serializers
            // that may have stored well-known types as references
            if (ProtobufWellKnownTypes.shouldSkipAsReference(referenceName)) {
                return; // Skip this reference
            }

            String depContent = parsedSchema.getParsedSchema().toProtoText();
            dependencies.put(referenceName, depContent);
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
            String fileName = referenceFileDescriptor.getName();

            // Skip well-known types - protobuf4j handles these internally
            // This includes google/protobuf/*.proto and google/type/*.proto
            if (ProtobufWellKnownTypes.shouldSkipAsReference(fileName)) {
                return; // Skip this dependency
            }

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
