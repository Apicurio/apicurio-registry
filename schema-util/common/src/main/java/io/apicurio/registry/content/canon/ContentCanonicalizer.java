package io.apicurio.registry.content.canon;

import io.apicurio.registry.content.ContentHandle;

import java.util.Map;

/**
 * Canonicalize some content! This means converting content to its canonical form for the purpose of
 * comparison. Should remove things like formatting and should sort fields when ordering is not important.
 */
public interface ContentCanonicalizer {

    /**
     * Called to convert the given content to its canonical form.
     * 
     * @param content
     */
    public ContentHandle canonicalize(ContentHandle content, Map<String, ContentHandle> resolvedReferences);

}
