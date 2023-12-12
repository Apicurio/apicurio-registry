package io.apicurio.registry.content.refs;

import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A Google Protocol Buffer implementation of a reference finder.
 */
public class ProtobufReferenceFinder implements ReferenceFinder {

    private static final Logger log = LoggerFactory.getLogger(ProtobufReferenceFinder.class);

    /**
     * @see io.apicurio.registry.content.refs.ReferenceFinder#findExternalReferences(io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public Set<ExternalReference> findExternalReferences(ContentHandle content) {
        try {
            ProtoFileElement protoFileElement = ProtobufFile.toProtoFileElement(content.content());
            Set<String> allImports = new HashSet<>();
            allImports.addAll(protoFileElement.getImports());
            allImports.addAll(protoFileElement.getPublicImports());
            return allImports.stream().map(imprt -> new ExternalReference(imprt)).collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Error finding external references in a Protobuf file.", e);
            return Collections.emptySet();
        }
    }

}
