package io.apicurio.registry.content.canon;

import io.apicurio.registry.content.ContentHandle;

import java.util.Map;

/**
 * A canonicalizer that passes through the content unchanged.
 */
public class NoOpContentCanonicalizer implements ContentCanonicalizer {
    
    /**
     * @see ContentCanonicalizer#canonicalize(io.apicurio.registry.content.ContentHandle, Map)
     */
    @Override
    public ContentHandle canonicalize(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        return content;
    }

}
