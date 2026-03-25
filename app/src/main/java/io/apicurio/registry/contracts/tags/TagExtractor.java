package io.apicurio.registry.contracts.tags;

import io.apicurio.registry.content.ContentHandle;

import java.util.Map;
import java.util.Set;

public interface TagExtractor {
    /**
     * Returns the artifact type this extractor handles (e.g., "AVRO", "JSON", "PROTOBUF")
     */
    String getArtifactType();

    /**
     * Extracts field-level tags from schema content.
     * @return Map of field path to set of tag names
     */
    Map<String, Set<String>> extractTags(ContentHandle content);
}