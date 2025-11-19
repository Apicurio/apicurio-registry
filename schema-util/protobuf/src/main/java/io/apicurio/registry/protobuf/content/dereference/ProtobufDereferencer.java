package io.apicurio.registry.protobuf.content.dereference;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchemaUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class ProtobufDereferencer implements ContentDereferencer {

    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        // Build dependencies map from resolved references
        final Map<String, String> schemaDefs = Collections
                .unmodifiableMap(resolvedReferences.entrySet().stream().collect(
                        Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getContent().content())));

        try {
            // Use protobuf4j to compile the schema with all dependencies
            Descriptors.FileDescriptor fileDescriptor = ProtobufSchemaUtils.parseAndCompile(
                    "schema.proto", content.getContent().content(), schemaDefs);

            // Convert to FileDescriptorProto (binary representation)
            DescriptorProtos.FileDescriptorProto fileDescriptorProto = fileDescriptor.toProto();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            fileDescriptorProto.writeTo(outputStream);

            // Dereference returns the whole file descriptor bytes representing the main protobuf schema with the
            // required dependencies.
            return TypedContent.create(ContentHandle.create(outputStream.toByteArray()),
                    ContentTypes.APPLICATION_PROTOBUF);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @see io.apicurio.registry.content.dereference.ContentDereferencer#rewriteReferences(TypedContent, Map)
     */
    @Override
    public TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls) {
        // TODO not yet implemented (perhaps cannot be implemented?)
        return content;
    }
}