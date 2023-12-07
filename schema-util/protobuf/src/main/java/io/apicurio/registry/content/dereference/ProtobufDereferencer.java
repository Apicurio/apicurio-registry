package io.apicurio.registry.content.dereference;

import com.google.protobuf.Descriptors;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class ProtobufDereferencer implements ContentDereferencer {

    @Override
    public ContentHandle dereference(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        //FIXME this code is not dereferencing references, only validating that all that references are resolvable
        //FIXME CAN this even be done in Proto? Can multiple types in different namespaces be defined in the same .proto file?  Does it matter?  Needs investigation.
        final ProtoFileElement protoFileElement = ProtobufFile.toProtoFileElement(content.content());
        final Map<String, ProtoFileElement> dependencies = Collections.unmodifiableMap(resolvedReferences.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> ProtobufFile.toProtoFileElement(e.getValue().content())
                )));
        try {
            return ContentHandle.create(FileDescriptorUtils.fileDescriptorWithDepsToProtoFile(FileDescriptorUtils.protoFileToFileDescriptor(protoFileElement), dependencies).toString());
        } catch (Descriptors.DescriptorValidationException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    /**
     * @see io.apicurio.registry.content.dereference.ContentDereferencer#rewriteReferences(io.apicurio.registry.content.ContentHandle, java.util.Map)
     */
    @Override
    public ContentHandle rewriteReferences(ContentHandle content, Map<String, String> resolvedReferenceUrls) {
        // TODO not yet implemented (perhaps cannot be implemented?)
        return content;
    }
}