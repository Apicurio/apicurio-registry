package io.apicurio.registry.protobuf.content.canon;

import com.google.protobuf.Descriptors;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchemaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
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

            // Skip canonicalization for Google well-known types (google.protobuf.*)
            // These are handled internally by protobuf4j's ensureWellKnownTypes() and
            // trying to compile them would cause duplicate definition errors.
            // This handles backward compatibility with old serializers (v3.1.2) that
            // registered well-known types as separate artifacts.
            if (isGoogleProtobufPackage(schemaContent)) {
                log.debug("Skipping canonicalization for Google well-known type (google.protobuf.*)");
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

            // Use ProtobufSchemaUtils.toProtoText() which uses protobuf4j's normalizeSchemaToText()
            String canonicalForm = ProtobufSchemaUtils.toProtoText(fileDescriptor);

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
     * Checks if the given file name is a well-known protobuf type.
     * These are provided by protobuf4j internally and should not be included in dependencies.
     */
    private boolean isWellKnownType(String fileName) {
        return fileName != null && fileName.startsWith("google/protobuf/");
    }

    /**
     * Checks if the schema content defines a Google well-known type (package google.protobuf).
     * These types are provided internally by protobuf4j and cannot be compiled separately
     * without causing duplicate definition errors.
     *
     * This handles backward compatibility with old serializers that registered well-known
     * types as separate artifacts in the registry.
     */
    private boolean isGoogleProtobufPackage(String schemaContent) {
        // Check for package google.protobuf declaration
        // This regex matches "package google.protobuf;" with optional whitespace
        return schemaContent != null &&
                schemaContent.matches("(?s).*\\bpackage\\s+google\\.protobuf\\s*;.*");
    }

}
