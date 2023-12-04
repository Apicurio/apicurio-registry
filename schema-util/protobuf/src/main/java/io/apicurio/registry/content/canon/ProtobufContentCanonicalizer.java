package io.apicurio.registry.content.canon;

import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;

import java.util.Map;

/**
 * A Protobuf implementation of a content Canonicalizer.
 *
 */
public class ProtobufContentCanonicalizer implements ContentCanonicalizer {

    /**
     * @see io.apicurio.registry.content.canon.ContentCanonicalizer#canonicalize(io.apicurio.registry.content.ContentHandle, Map)
     */
    @Override
    public ContentHandle canonicalize(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        try {
            ProtoFileElement fileElem = ProtoParser.Companion.parse(FileDescriptorUtils.DEFAULT_LOCATION, content.content());

            //TODO maybe use FileDescriptorUtils to convert to a FileDescriptor and then convert back to ProtoFileElement

            return ContentHandle.create(fileElem.toSchema());
        } catch (Throwable e) {
            return content;
        }
    }

}
