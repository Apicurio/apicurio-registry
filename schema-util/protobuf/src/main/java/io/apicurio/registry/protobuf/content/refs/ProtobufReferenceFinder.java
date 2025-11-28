package io.apicurio.registry.protobuf.content.refs;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchemaUtils;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;

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
        // Extract imports from the proto content without compiling
        // This allows finding references even when the imported files are not available
        return ProtobufSchemaUtils.extractNonWellKnownImports(content.getContent().content())
                .stream()
                .map(ExternalReference::new)
                .collect(Collectors.toSet());
    }

}
