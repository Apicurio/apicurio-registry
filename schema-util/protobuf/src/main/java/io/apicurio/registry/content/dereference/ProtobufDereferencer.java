package io.apicurio.registry.content.dereference;

import io.apicurio.registry.content.ContentHandle;

import java.util.Map;

public class ProtobufDereferencer implements ContentDereferencer {

    @Override
    public ContentHandle dereference(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        throw new DereferencingNotSupportedException("Content dereferencing is not supported for Protobuf");
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