package io.apicurio.registry.content.normalize;

import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.normalization.ContentNormalizer;

import java.util.Map;

import static io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils.DEFAULT_LOCATION;

public class ProtobufContentNormalizer implements ContentNormalizer {

    @Override
    public ContentHandle normalize(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        ProtoFileElement fileElem = ProtoParser.Companion.parse(DEFAULT_LOCATION, content.content());
        return ContentHandle.create(fileElem.toSchema());
    }
}
