package io.apicurio.registry.content.refs;

import com.google.protobuf.Descriptors;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchemaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A Google Protocol Buffer implementation of a reference finder.
 */
public class ProtobufReferenceFinder implements ReferenceFinder {

    private static final Logger log = LoggerFactory.getLogger(ProtobufReferenceFinder.class);

    /**
     * @see io.apicurio.registry.content.refs.ReferenceFinder#findExternalReferences(TypedContent)
     */
    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        try {
            // Use protobuf4j to compile and get all dependencies
            Descriptors.FileDescriptor fileDescriptor = ProtobufSchemaUtils.parseAndCompile(
                    "schema.proto", content.getContent().content(), Map.of());

            // Get all imports from the FileDescriptor, excluding well-known types
            return fileDescriptor.getDependencies().stream()
                    .map(Descriptors.FileDescriptor::getName)
                    .filter(imprt -> !imprt.startsWith("google/protobuf/"))
                    .map(ExternalReference::new)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Error finding external references in a Protobuf file.", e);
            return Collections.emptySet();
        }
    }

}
