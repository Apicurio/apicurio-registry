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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProtobufSchemaParser<U extends Message> implements SchemaParser<ProtobufSchema, U> {

    /**
     * Cache for parsed schemas by FileDescriptor full name.
     * FileDescriptors are immutable at runtime for a given message class,
     * so we can safely cache the extracted schema.
     */

    private final Map<String, ParsedSchema<ProtobufSchema>> schemaCache = new ConcurrentHashMap<>();

    /**
     * Maximum number of entries in the schema cache.
     * Prevents unbounded memory growth in long-running applications.
     */
    private static final int MAX_CACHE_SIZE = 1000;

    /**
     * Cache for parsed schemas by FileDescriptor full name.
     * FileDescriptors are immutable at runtime for a given message class,
     * so we can safely cache the extracted schema.
     * Uses LRU eviction to prevent unbounded growth.
     */
    private final Map<String, ParsedSchema<ProtobufSchema>> schemaCache = createBoundedCache(MAX_CACHE_SIZE);

    /**
     * Creates a thread-safe bounded LRU cache.
     * When the cache exceeds maxSize, the least recently accessed entry is removed.
     */
    private static <K, V> Map<K, V> createBoundedCache(int maxSize) {
        return Collections.synchronizedMap(new LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        });
    }

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
            // NOTE: protobuf4j handles google.protobuf.* well-known types automatically
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
            String referenceName = parsedSchema.referenceName();

            // Skip well-known types - protobuf4j handles these internally
            // This is important for backward compatibility with older serializers
            // that may have stored well-known types as references
            if (isWellKnownType(referenceName)) {
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

        // Use FileDescriptor's full name as cache key - this is unique per proto file
        String cacheKey = schemaFileDescriptor.getFullName();

        return schemaCache.computeIfAbsent(cacheKey, key -> {
            ProtobufSchema protobufSchema = new ProtobufSchema(schemaFileDescriptor);

            // Use FileDescriptorProto text format as the raw schema
            byte[] rawSchema = IoUtil.toBytes(protobufSchema.toProtoText());

            return new ParsedSchemaImpl<ProtobufSchema>().setParsedSchema(protobufSchema)
                    .setReferenceName(protobufSchema.getFileDescriptor().getName())
                    .setSchemaReferences(handleDependencies(schemaFileDescriptor)).setRawSchema(rawSchema);
        });
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
            if (isWellKnownType(fileName)) {
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

    /**
     * Check if a proto file is a well-known type that protobuf4j handles internally.
     * These types don't need to be stored as references in the registry.
     *
     * @param fileName The proto file name (e.g., "google/protobuf/timestamp.proto")
     * @return true if this is a well-known type handled by protobuf4j
     */
    private boolean isWellKnownType(String fileName) {
        // Google Protocol Buffer well-known types
        if (fileName.startsWith("google/protobuf/")) {
            return true;
        }
        // Google common types (from googleapis)
        if (fileName.startsWith("google/type/")) {
            return true;
        }
        return false;
    }
}
