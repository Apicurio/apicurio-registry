package io.apicurio.registry.contracts.tags;

import io.apicurio.registry.content.ContentHandle;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Turns the field tags declared in schema content (Avro {@code tags}, JSON Schema {@code x-tags},
 * Protobuf {@code @tag:} comments, and their Confluent equivalents) into version labels when a
 * version is created: {@code field-tag.{fieldPath}|{tag}} = {@code INLINE}.
 */
@ApplicationScoped
public class InlineFieldTagService {

    private static final Logger log = LoggerFactory.getLogger(InlineFieldTagService.class);

    static final String FIELD_TAG_PREFIX = "field-tag.";
    /** The longest label key every storage variant accepts. */
    static final int MAX_LABEL_KEY_LENGTH = 256;

    @Inject
    TagExtractorFactory extractorFactory;

    /**
     * Returns the labels of a new version with the content's inline field tags added. Labels the
     * caller set explicitly win over extracted tags with the same key.
     *
     * @param artifactType the artifact type of the version
     * @param content the version content
     * @param labels the labels submitted with the version, possibly {@code null}
     * @return the labels to store: {@code labels} itself when the content declares no tags
     */
    public Map<String, String> withInlineTags(String artifactType, ContentHandle content,
            Map<String, String> labels) {
        if (content == null) {
            return labels;
        }
        Map<String, Set<String>> tags = extractorFactory.getExtractor(artifactType)
                .map(extractor -> extractor.extractTags(content))
                .orElse(Map.of());
        if (tags.isEmpty()) {
            return labels;
        }
        Map<String, String> result = new LinkedHashMap<>();
        tags.forEach((fieldPath, tagNames) -> tagNames.forEach(tagName -> {
            String key = FIELD_TAG_PREFIX + fieldPath + "|" + tagName;
            if (key.length() > MAX_LABEL_KEY_LENGTH) {
                log.warn("Skipping inline field tag {}|{}: the label key is longer than {} characters",
                        fieldPath, tagName, MAX_LABEL_KEY_LENGTH);
            } else {
                result.put(key, TagSource.INLINE.name());
            }
        }));
        if (labels != null) {
            result.putAll(labels);
        }
        return result;
    }
}
