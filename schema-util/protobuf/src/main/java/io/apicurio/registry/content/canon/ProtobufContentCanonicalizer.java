package io.apicurio.registry.content.canon;

import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;

import java.util.Map;

/**
 * A Protobuf implementation of a content Canonicalizer.
 */
public class ProtobufContentCanonicalizer implements ContentCanonicalizer {

    /**
     * @see io.apicurio.registry.content.canon.ContentCanonicalizer#canonicalize(TypedContent, Map)
     */
    @Override
    public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            ProtoFileElement fileElem = ProtoParser.Companion.parse(FileDescriptorUtils.DEFAULT_LOCATION,
                    content.getContent().content());

            // TODO maybe use FileDescriptorUtils to convert to a FileDescriptor and then convert back to
            // ProtoFileElement

            return TypedContent.create(ContentHandle.create(fileElem.toSchema()),
                    ContentTypes.APPLICATION_PROTOBUF);
        } catch (Throwable e) {
            return content;
        }
    }

}
