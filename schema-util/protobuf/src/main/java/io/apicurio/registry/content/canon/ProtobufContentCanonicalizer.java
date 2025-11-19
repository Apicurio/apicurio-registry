package io.apicurio.registry.content.canon;

import com.google.protobuf.Descriptors;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchemaUtils;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A Protobuf implementation of a content Canonicalizer.
 */
public class ProtobufContentCanonicalizer implements ContentCanonicalizer {

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
        } catch (Throwable e) {
            // If canonicalization fails, return original content
            return content;
        }
    }

}
