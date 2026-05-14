package io.apicurio.registry.content.extract;

import io.apicurio.registry.content.ContentHandle;

import java.util.List;

/**
 * Extracts structured elements from artifact content for search indexing. Each artifact type provides its own
 * implementation that understands the content format and can identify searchable structured elements.
 */
public interface StructuredContentExtractor {

    /**
     * Extracts structured elements from the given artifact content.
     *
     * @param content the artifact content to extract structured elements from
     * @return a list of structured elements found in the content, or an empty list if none
     */
    List<StructuredElement> extract(ContentHandle content);
}
