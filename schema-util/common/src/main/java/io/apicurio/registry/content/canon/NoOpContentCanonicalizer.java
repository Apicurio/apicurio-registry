package io.apicurio.registry.content.canon;

import io.apicurio.registry.content.TypedContent;

import java.util.Map;

/**
 * A canonicalizer that passes through the content unchanged.
 */
public class NoOpContentCanonicalizer implements ContentCanonicalizer {

    public static final NoOpContentCanonicalizer INSTANCE = new NoOpContentCanonicalizer();

    /**
     * @see ContentCanonicalizer#canonicalize(TypedContent, Map)
     */
    @Override
    public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        return content;
    }

}
