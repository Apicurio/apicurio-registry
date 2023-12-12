package io.apicurio.registry.content.extract;

import io.apicurio.registry.content.ContentHandle;

import static io.apicurio.registry.utils.StringUtil.isEmpty;

public interface ContentExtractor {
    /**
     * Extract metadata from content. Return null if no content is extracted.
     *
     * @param content the content
     * @return extracted metadata or null if none
     */
    ExtractedMetaData extract(ContentHandle content);

    /**
     * Did we actually extracted something from the content.
     *
     * @param metaData the extracted metadata
     * @return true if extracted, false otherwise
     */
    default boolean isExtracted(ExtractedMetaData metaData) {
        if (metaData == null) {
            return false;
        }
        return !isEmpty(metaData.getName()) || !isEmpty(metaData.getDescription());
    }
}
