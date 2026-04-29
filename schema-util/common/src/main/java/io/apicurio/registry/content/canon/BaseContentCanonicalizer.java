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
        } catch (ContentCanonicalizationException e) {
            return handleCanonicalizationError(e, content);
        } catch (Exception e) {
            return handleCanonicalizationError(new ContentCanonicalizationException(
                    "Failed to canonicalize content", e), content);
        }
    }

    /**
     * Perform type-specific canonicalization.
     */
    protected abstract TypedContent doCanonicalize(TypedContent content,
                                                   Map<String, TypedContent> refs) throws ContentCanonicalizationException;

    /**
     * Handle canonicalization errors. Override for custom error handling.
     */
    protected TypedContent handleCanonicalizationError(ContentCanonicalizationException e, TypedContent content) {
        log.warn("Failed to canonicalize content, returning original. Error: {}", e.getMessage(), e);
        return content;
    }

}

