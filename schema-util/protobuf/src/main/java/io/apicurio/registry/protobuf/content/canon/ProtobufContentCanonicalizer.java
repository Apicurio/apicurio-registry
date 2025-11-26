package io.apicurio.registry.protobuf.content.canon;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchemaUtils;
import io.roastedroot.protobuf4j.Protobuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A Protobuf implementation of a content Canonicalizer.
 *
 * <p>This canonicalizer converts protobuf schemas to a normalized text format
 * using protobuf4j's schema normalization. The normalized form ensures consistent
 * representation of semantically equivalent schemas.</p>
 */
public class ProtobufContentCanonicalizer implements ContentCanonicalizer {

    private static final Logger log = LoggerFactory.getLogger(ProtobufContentCanonicalizer.class);

    private static final String SCHEMA_PROTO = "schema.proto";

    /**
     * @see io.apicurio.registry.content.canon.ContentCanonicalizer#canonicalize(TypedContent, Map)
     */
    @Override
    public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            // Handle empty or blank schemas - return original content
            String schemaContent = content.getContent().content();
            if (schemaContent == null || schemaContent.trim().isEmpty()) {
                return content;
            }

            // Build dependencies map from resolved references
            // Filter out well-known types as protobuf4j provides them internally via ensureWellKnownTypes()
            Map<String, String> dependencies = (resolvedReferences == null || resolvedReferences.isEmpty())
                ? Collections.emptyMap()
                : resolvedReferences.entrySet().stream()
                    .filter(e -> !isWellKnownType(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getContent().content()));

            // Use protobuf4j to compile to FileDescriptor
            Descriptors.FileDescriptor fileDescriptor = ProtobufSchemaUtils.parseAndCompile(
                    SCHEMA_PROTO, schemaContent, dependencies);

            // Build a FileDescriptorSet that includes the main file and all dependencies
            // This is required because normalizeSchemaToText(FileDescriptor) tries to re-compile
            // through WASM protoc which doesn't have access to the dependencies
            DescriptorProtos.FileDescriptorSet.Builder fdsBuilder = DescriptorProtos.FileDescriptorSet.newBuilder();
            // Pre-size the set based on dependencies count + main file + well-known types estimate
            int estimatedFiles = dependencies.size() + 15; // 15 for well-known types
            Set<String> addedFiles = new HashSet<>(estimatedFiles);
            addFileDescriptorToSet(fileDescriptor, fdsBuilder, addedFiles);

            // Use protobuf4j's normalization with the complete FileDescriptorSet
            Map<String, String> normalizedSchemas = Protobuf.normalizeSchemaToText(fdsBuilder.build());

            // Get the normalized form of our main schema
            String canonicalForm = normalizedSchemas.get(SCHEMA_PROTO);
            if (canonicalForm == null) {
                canonicalForm = normalizedSchemas.get(fileDescriptor.getName());
            }

            if (canonicalForm == null) {
                throw new IOException("Normalized schema not found in result for " + SCHEMA_PROTO);
            }

            return TypedContent.create(ContentHandle.create(canonicalForm),
                    ContentTypes.APPLICATION_PROTOBUF);
        } catch (IOException e) {
            // Expected errors during parsing/compilation - log and return original
            log.warn("Failed to canonicalize protobuf schema, returning original content: {}", e.getMessage());
            log.debug("Canonicalization error details", e);
            return content;
        } catch (Exception e) {
            // Unexpected errors - log more seriously but still return original to avoid breaking flow
            log.error("Unexpected error during protobuf canonicalization, returning original content", e);
            return content;
        }
    }

    /**
     * Recursively adds a FileDescriptor and all its dependencies to the FileDescriptorSet builder.
     * Uses a set to track already-added files to avoid duplicates.
     */
    private void addFileDescriptorToSet(Descriptors.FileDescriptor fd,
            DescriptorProtos.FileDescriptorSet.Builder builder, Set<String> addedFiles) {
        String fileName = fd.getName();
        if (addedFiles.contains(fileName)) {
            return; // Already added
        }

        // Add dependencies first (recursively)
        for (Descriptors.FileDescriptor dep : fd.getDependencies()) {
            addFileDescriptorToSet(dep, builder, addedFiles);
        }

        // Add this file's proto
        builder.addFile(fd.toProto());
        addedFiles.add(fileName);
    }

    /**
     * Checks if the given file name is a well-known protobuf type.
     * These are provided by protobuf4j internally and should not be included in dependencies.
     */
    private boolean isWellKnownType(String fileName) {
        return fileName != null && fileName.startsWith("google/protobuf/");
    }

}
