/*
 * Copyright 2025 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.registry.content.canon;

import io.apicurio.registry.content.TypedContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Base class for content canonicalizers using the template method pattern.
 * Subclasses implement {@link #doCanonicalize} for type-specific canonicalization,
 * while this class provides uniform error handling that returns the original
 * content on failure.
 */
public abstract class BaseContentCanonicalizer implements ContentCanonicalizer {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public final TypedContent canonicalize(TypedContent content,
            Map<String, TypedContent> resolvedReferences) {
        try {
            return doCanonicalize(content, resolvedReferences);
        } catch (ContentCanonicalizationException e) {
            return handleCanonicalizationError(e, content);
        } catch (RuntimeException e) {
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
     * Default behavior logs at DEBUG level and returns the original content.
     */
    protected TypedContent handleCanonicalizationError(ContentCanonicalizationException e, TypedContent content) {
        log.debug("Failed to canonicalize content, returning original. Error: {}", e.getMessage(), e);
        return content;
    }

}
