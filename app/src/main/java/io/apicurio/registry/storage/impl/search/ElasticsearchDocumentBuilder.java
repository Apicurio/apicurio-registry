package io.apicurio.registry.storage.impl.search;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds Elasticsearch documents (as {@code Map<String, Object>}) from artifact version metadata
 * and content. Each document represents one artifact version with searchable content and metadata.
 */
@ApplicationScoped
public class ElasticsearchDocumentBuilder {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchDocumentBuilder.class);

    @Inject
    ElasticsearchSearchConfig config;

    /**
     * Builds an Elasticsearch document from artifact version metadata and content.
     *
     * @param versionMetadata the version metadata (globalId, version, state, etc.)
     * @param contentBytes the artifact content as bytes
     * @param extractor the structured content extractor for the artifact type (may be null)
     * @return a Map representing the ES document ready for indexing
     */
    public Map<String, Object> buildVersionDocument(ArtifactVersionMetaDataDto versionMetadata,
            byte[] contentBytes, StructuredContentExtractor extractor) {
        Map<String, Object> doc = new HashMap<>();

        // === ID Fields ===
        doc.put("globalId", versionMetadata.getGlobalId());
        doc.put("contentId", versionMetadata.getContentId());

        // === String Fields (exact match, filterable) ===
        String groupId = versionMetadata.getGroupId() != null ? versionMetadata.getGroupId()
                : "default";
        doc.put("groupId", groupId);
        doc.put("artifactId", versionMetadata.getArtifactId());
        doc.put("version", versionMetadata.getVersion());

        if (versionMetadata.getArtifactType() != null) {
            doc.put("artifactType", versionMetadata.getArtifactType());
        }

        if (versionMetadata.getState() != null) {
            doc.put("state", versionMetadata.getState().name());
        }

        // === Searchable Metadata Fields ===
        String name = versionMetadata.getName();
        if (name != null && !name.isBlank()) {
            doc.put("name", name);
        }

        if (versionMetadata.getDescription() != null
                && !versionMetadata.getDescription().isBlank()) {
            doc.put("description", versionMetadata.getDescription());
        }

        if (versionMetadata.getOwner() != null && !versionMetadata.getOwner().isBlank()) {
            doc.put("owner", versionMetadata.getOwner());
        }

        if (versionMetadata.getModifiedBy() != null
                && !versionMetadata.getModifiedBy().isBlank()) {
            doc.put("modifiedBy", versionMetadata.getModifiedBy());
        }

        // === Timestamps ===
        if (versionMetadata.getCreatedOn() > 0) {
            doc.put("createdOn", versionMetadata.getCreatedOn());
        }

        if (versionMetadata.getModifiedOn() > 0) {
            doc.put("modifiedOn", versionMetadata.getModifiedOn());
        }

        doc.put("versionOrder", versionMetadata.getVersionOrder());

        // === Labels (nested objects) ===
        Map<String, String> labels = versionMetadata.getLabels();
        if (labels != null && !labels.isEmpty()) {
            List<Map<String, String>> labelEntries = new ArrayList<>();
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                Map<String, String> labelDoc = new HashMap<>();
                labelDoc.put("key", entry.getKey());
                labelDoc.put("value", entry.getValue());
                labelEntries.add(labelDoc);
            }
            doc.put("labels", labelEntries);
        }

        // === Content Fields ===
        String contentText = new String(contentBytes, StandardCharsets.UTF_8);
        if (contentText.length() > config.getContentMaxSize()) {
            contentText = contentText.substring(0, config.getContentMaxSize());
        }
        doc.put("content", contentText);

        // Structured content extraction
        if (extractor != null && versionMetadata.getArtifactType() != null) {
            indexStructuredElements(doc, contentBytes, versionMetadata.getArtifactType(),
                    extractor);
        }

        // === Timestamp for freshness tracking ===
        doc.put("indexedAt", System.currentTimeMillis());

        log.debug("Built document for version {}/{}/{} (globalId={})", groupId,
                versionMetadata.getArtifactId(), versionMetadata.getVersion(),
                versionMetadata.getGlobalId());

        return doc;
    }

    /**
     * Builds an Elasticsearch document without structured content extraction.
     *
     * @param versionMetadata the version metadata
     * @param contentBytes the artifact content as bytes
     * @return a Map representing the ES document ready for indexing
     */
    public Map<String, Object> buildVersionDocument(ArtifactVersionMetaDataDto versionMetadata,
            byte[] contentBytes) {
        return buildVersionDocument(versionMetadata, contentBytes, null);
    }

    /**
     * Extracts the labels from a document source map returned by Elasticsearch.
     *
     * @param source the document source map
     * @return a map of label key-value pairs, or empty map if no labels
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> extractLabels(Map<String, Object> source) {
        Object labelsObj = source.get("labels");
        if (labelsObj == null) {
            return Collections.emptyMap();
        }
        if (labelsObj instanceof List<?> labelsList) {
            Map<String, String> result = new HashMap<>();
            for (Object entry : labelsList) {
                if (entry instanceof Map<?, ?> labelMap) {
                    String key = String.valueOf(labelMap.get("key"));
                    String value = String.valueOf(labelMap.get("value"));
                    result.put(key, value);
                }
            }
            return result;
        }
        return Collections.emptyMap();
    }

    /**
     * Indexes structured elements extracted from artifact content using the type-specific
     * extractor. Each element is indexed in three fields:
     * <ul>
     * <li>{@code structure} - exact match: "{artifactType}:{kind}:{name}" (lowercased)</li>
     * <li>{@code structure_text} - tokenized: "{kind} {name}" (for text search)</li>
     * <li>{@code structure_kind} - exact match: "{artifactType}:{kind}" (lowercased)</li>
     * </ul>
     *
     * @param doc the document map to add fields to
     * @param contentBytes the content as bytes
     * @param artifactType the artifact type
     * @param extractor the structured content extractor
     */
    private void indexStructuredElements(Map<String, Object> doc, byte[] contentBytes,
            String artifactType, StructuredContentExtractor extractor) {
        try {
            ContentHandle contentHandle = ContentHandle.create(contentBytes);
            List<StructuredElement> elements = extractor.extract(contentHandle);

            String artifactTypeLower = artifactType.toLowerCase();
            List<String> structureValues = new ArrayList<>();
            List<String> structureTextValues = new ArrayList<>();
            List<String> structureKindValues = new ArrayList<>();

            for (StructuredElement element : elements) {
                String kindLower = element.kind().toLowerCase();
                String nameLower = element.name().toLowerCase();

                // Exact match: "artifactType:kind:name"
                structureValues.add(artifactTypeLower + ":" + kindLower + ":" + nameLower);

                // Tokenized text: "kind name" (for text search)
                structureTextValues.add(element.kind() + " " + element.name());

                // Kind facet: "artifactType:kind"
                structureKindValues.add(artifactTypeLower + ":" + kindLower);
            }

            if (!structureValues.isEmpty()) {
                doc.put("structure", structureValues);
                doc.put("structure_text", structureTextValues);
                doc.put("structure_kind", structureKindValues);
            }

            if (!elements.isEmpty()) {
                log.debug("Indexed {} structured elements for artifact type {}",
                        elements.size(), artifactType);
            }
        } catch (Exception e) {
            log.warn("Failed to index structured elements for artifact type {}: {}",
                    artifactType, e.getMessage());
        }
    }
}
