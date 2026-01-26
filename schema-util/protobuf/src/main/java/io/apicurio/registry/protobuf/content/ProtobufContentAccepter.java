package io.apicurio.registry.protobuf.content;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchemaUtils;

import java.util.Map;

public class ProtobufContentAccepter implements ContentAccepter {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            String contentType = content.getContentType();
            if (contentType != null && !contentType.toLowerCase().contains("proto")) {
                return false;
            }

            String schemaContent = content.getContent().content();

            // Check if this is a base64-encoded binary descriptor
            if (ProtobufSchemaUtils.isBase64BinaryDescriptor(schemaContent)) {
                // Binary descriptors are pre-compiled, we accept them if they parse correctly
                ProtobufSchemaUtils.validateBinaryDescriptorSyntax(schemaContent);
                return true;
            }

            // Use syntax-only validation (doesn't require resolving imports)
            // This matches the behavior of the old wire-schema based implementation
            ProtobufFile.validateSyntaxOnly(schemaContent);
            return true;
        } catch (Exception e) {
            // Doesn't seem to be protobuf
            return false;
        }
    }

}
