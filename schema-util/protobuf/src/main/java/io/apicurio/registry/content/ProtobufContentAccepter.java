package io.apicurio.registry.content;

import com.google.protobuf.DescriptorProtos;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

import java.util.Base64;
import java.util.Map;

public class ProtobufContentAccepter implements ContentAccepter {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            String contentType = content.getContentType();
            if (contentType != null && !contentType.toLowerCase().contains("proto")) {
                return false;
            }
            // Try to parse as proto text - this will validate it's a valid proto file
            new ProtobufFile(content.getContent().content());
            return true;
        } catch (Exception e) {
            try {
                // Attempt to parse binary FileDescriptorProto and validate it
                byte[] bytes = Base64.getDecoder().decode(content.getContent().content());
                DescriptorProtos.FileDescriptorProto fileDescriptorProto =
                    DescriptorProtos.FileDescriptorProto.parseFrom(bytes);
                // Try to build FileDescriptor to validate it's correct
                FileDescriptorUtils.protoFileToFileDescriptor(fileDescriptorProto);
                return true;
            } catch (Exception pe) {
                // Doesn't seem to be protobuf
            }
        }
        return false;
    }

}
