package io.apicurio.registry.content.canon;


import io.apicurio.registry.content.TypedContent;
import org.slf4j.LoggerFactory;

import java.util.Map;
import org.slf4j.Logger;

public abstract class BaseContentCanonicalizer implements ContentCanonicalizer {

    private static final Logger log = LoggerFactory.getLogger(BaseContentCanonicalizer.class);

    @Override
    public final TypedContent canonicalize(TypedContent content,
                                           Map<String, TypedContent> resolvedReferences) {
        try {
            return doCanonicalize(content, resolvedReferences);
        } catch (Throwable t) {
            return handleCanonicalizationError(t, content);
        }
    }

    /**
     * Perform type-specific canonicalization.
     */
    protected abstract TypedContent doCanonicalize(TypedContent content,
                                                   Map<String, TypedContent> refs) throws Exception;

    /**
     * Handle canonicalization errors. Override for custom error handling.
     */
    protected TypedContent handleCanonicalizationError(Throwable t, TypedContent content) {
        log.error("Failed to canonicalize content, returning original", t);
        return content;
    }

}

