package io.apicurio.registry.content.dereference;

import com.google.protobuf.DescriptorProtos;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ProtobufDereferencer implements ContentDereferencer {

    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        final ProtoFileElement protoFileElement = ProtobufFile.toProtoFileElement(content.getContent().content());
        final Map<String, String> schemaDefs = Collections.unmodifiableMap(resolvedReferences.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getContent().content()
                )));

        DescriptorProtos.FileDescriptorProto fileDescriptorProto = FileDescriptorUtils.toFileDescriptorProto(content.getContent().content(), FileDescriptorUtils.firstMessage(protoFileElement).getName(), Optional.ofNullable(protoFileElement.getPackageName()), schemaDefs);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            fileDescriptorProto.writeTo(outputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //Dereference returns the whole file descriptor bytes representing the main protobuf schema with the required dependencies.
        return TypedContent.create(ContentHandle.create(outputStream.toByteArray()), ContentTypes.APPLICATION_PROTOBUF);
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