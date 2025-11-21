package io.apicurio.registry.content.canon;

import com.google.protobuf.Descriptors;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
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
 * by compiling them to FileDescriptors and then converting back to .proto syntax.
 * All important features are preserved including options, defaults, extensions, etc.</p>
 */
public class ProtobufContentCanonicalizer implements ContentCanonicalizer {

    private static final Logger log = LoggerFactory.getLogger(ProtobufContentCanonicalizer.class);

    /**
     * @see io.apicurio.registry.content.canon.ContentCanonicalizer#canonicalize(TypedContent, Map)
     */
    @Override
    public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            // Build dependencies map from resolved references
            Map<String, String> dependencies = (resolvedReferences == null || resolvedReferences.isEmpty())
                ? Collections.emptyMap()
                : resolvedReferences.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getContent().content()));

            // Use protobuf4j to compile to FileDescriptor, then convert to canonical text format
            Descriptors.FileDescriptor fileDescriptor = ProtobufSchemaUtils.parseAndCompile(
                    "schema.proto", content.getContent().content(), dependencies);

            // Convert FileDescriptor to protobuf text format (canonical form)
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

}
