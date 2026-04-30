package io.apicurio.registry.protobuf.content.canon;

import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.BaseContentCanonicalizer;
import io.apicurio.registry.content.canon.ContentCanonicalizationException;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;

import java.util.Map;

/**
 * A Protobuf implementation of a content Canonicalizer.
 */
public class ProtobufContentCanonicalizer extends BaseContentCanonicalizer {

    /**
     * @see io.apicurio.registry.content.canon.BaseContentCanonicalizer#canonicalize(TypedContent, Map)
     */
    @Override
    protected TypedContent doCanonicalize(TypedContent content,
            Map<String, TypedContent> refs) throws ContentCanonicalizationException {
        ProtoFileElement fileElem = ProtoParser.Companion.parse(FileDescriptorUtils.DEFAULT_LOCATION,
            content.getContent().content());

        // TODO maybe use FileDescriptorUtils to convert to a FileDescriptor and then convert back to
        // ProtoFileElement

        return TypedContent.create(ContentHandle.create(fileElem.toSchema()),
            ContentTypes.APPLICATION_PROTOBUF);
    }
}
