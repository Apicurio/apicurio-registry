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
            ProtobufFile.toProtoFileElement(content.getContent().content());
            return true;
        } catch (Exception e) {
            try {
                // Attempt to parse binary FileDescriptorProto
                byte[] bytes = Base64.getDecoder().decode(content.getContent().content());
                FileDescriptorUtils
                        .fileDescriptorToProtoFile(DescriptorProtos.FileDescriptorProto.parseFrom(bytes));
                return true;
            } catch (Exception pe) {
                // Doesn't seem to be protobuf
            }
        }
        return false;
    }

}
