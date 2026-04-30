package io.apicurio.registry.content.canon;

import io.apicurio.registry.content.TypedContent;

import java.util.Map;

/**
 * A canonicalizer that passes through the content unchanged.
 */
public class NoOpContentCanonicalizer extends BaseContentCanonicalizer {

    public static final NoOpContentCanonicalizer INSTANCE = new NoOpContentCanonicalizer();

    /**
     * @see BaseContentCanonicalizer#canonicalize(TypedContent, Map)
     */
    @Override
    protected TypedContent doCanonicalize(TypedContent content,
            Map<String, TypedContent> refs) throws ContentCanonicalizationException {
        return content;
    }
}
