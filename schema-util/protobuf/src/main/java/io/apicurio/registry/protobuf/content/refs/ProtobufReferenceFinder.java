package io.apicurio.registry.protobuf.content.refs;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinderException;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchemaUtils;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * A Google Protocol Buffer implementation of a reference finder.
 */
public class ProtobufReferenceFinder implements ReferenceFinder {

    /**
     * @see io.apicurio.registry.content.refs.ReferenceFinder#findExternalReferences(TypedContent)
     */
    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        try {
            String schemaContent = content.getContent().content();

            // First validate the syntax to ensure we're dealing with valid protobuf content
            // This handles both text format and base64-encoded binary descriptors
            if (ProtobufSchemaUtils.isBase64BinaryDescriptor(schemaContent)) {
                // Binary descriptors are pre-compiled, validate they parse correctly
                ProtobufSchemaUtils.validateBinaryDescriptorSyntax(schemaContent);
            } else {
                // Text format - validate syntax
                ProtobufFile.validateSyntaxOnly(schemaContent);
            }

            // Extract imports from the proto content without compiling
            // This allows finding references even when the imported files are not available
            return ProtobufSchemaUtils.extractNonWellKnownImports(schemaContent)
                    .stream()
                    .map(ExternalReference::new)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            throw new ReferenceFinderException("Error finding external references in a Protobuf file.", e);
        }
    }

}
