package io.apicurio.registry.content.canon;

import com.google.protobuf.Descriptors;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchemaUtils;
import io.roastedroot.protobuf4j.Protobuf;
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

            // Build dependencies map from resolved references
            Map<String, String> dependencies = (resolvedReferences == null || resolvedReferences.isEmpty())
                ? Collections.emptyMap()
                : resolvedReferences.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getContent().content()));

            // Use protobuf4j to compile to FileDescriptor
            Descriptors.FileDescriptor fileDescriptor = ProtobufSchemaUtils.parseAndCompile(
                    SCHEMA_PROTO, schemaContent, dependencies);

            // Use protobuf4j's normalization to get normalized proto text directly
            String canonicalForm = Protobuf.normalizeSchemaToText(fileDescriptor);

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

}
