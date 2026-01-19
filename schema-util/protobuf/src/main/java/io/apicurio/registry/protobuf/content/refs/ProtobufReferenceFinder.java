package io.apicurio.registry.protobuf.content.refs;

import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinderException;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

import java.util.HashSet;
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
            ProtoFileElement protoFileElement = ProtobufFile
                    .toProtoFileElement(content.getContent().content());
            Set<String> allImports = new HashSet<>();
            allImports.addAll(protoFileElement.getImports());
            allImports.addAll(protoFileElement.getPublicImports());
            return allImports.stream()
                    .filter(imprt -> !imprt.startsWith("google/protobuf/"))
                    .map(imprt -> new ExternalReference(imprt)).collect(Collectors.toSet());
        } catch (Exception e) {
            throw new ReferenceFinderException("Error finding external references in a Protobuf file.", e);
        }
    }

}
