package io.apicurio.registry.storage.impl.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * Builds Lucene Documents from artifact version metadata and content. Each document represents one
 * artifact version with searchable content and metadata.
 */
@ApplicationScoped
public class LuceneDocumentBuilder {

    private static final Logger log = LoggerFactory.getLogger(LuceneDocumentBuilder.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Builds a Lucene Document from artifact version metadata and content.
     *
     * @param versionMetadata The version metadata (globalId, version, state, etc.)
     * @param contentBytes The artifact content as bytes
     * @return A Lucene Document ready for indexing
     */
    public Document buildVersionDocument(ArtifactVersionMetaDataDto versionMetadata,
            byte[] contentBytes) {
        Document doc = new Document();

        // === ID Fields ===
        // globalId: StringField (indexed+stored for Term-based update/delete), LongPoint (range query),
        // DocValues (sorting)
        doc.add(new StringField("globalId", String.valueOf(versionMetadata.getGlobalId()),
                Field.Store.YES));
        doc.add(new LongPoint("globalId_query", versionMetadata.getGlobalId()));
        doc.add(new NumericDocValuesField("globalId_sort", versionMetadata.getGlobalId()));

        // contentId: StringField (indexed+stored), LongPoint (range query)
        doc.add(new StringField("contentId", String.valueOf(versionMetadata.getContentId()),
                Field.Store.YES));
        doc.add(new LongPoint("contentId_query", versionMetadata.getContentId()));

        // === String Fields (exact match, filterable, stored for result mapping) ===
        String groupId = versionMetadata.getGroupId() != null ? versionMetadata.getGroupId()
                : "default";
        doc.add(new StringField("groupId", groupId, Field.Store.YES));
        doc.add(new SortedDocValuesField("groupId_sort", new BytesRef(groupId)));

        doc.add(new StringField("artifactId", versionMetadata.getArtifactId(), Field.Store.YES));
        doc.add(new SortedDocValuesField("artifactId_sort",
                new BytesRef(versionMetadata.getArtifactId())));

        doc.add(new StringField("version", versionMetadata.getVersion(), Field.Store.YES));
        doc.add(new SortedDocValuesField("version_sort",
                new BytesRef(versionMetadata.getVersion())));

        if (versionMetadata.getArtifactType() != null) {
            doc.add(new StringField("artifactType", versionMetadata.getArtifactType(),
                    Field.Store.YES));
            doc.add(new SortedDocValuesField("artifactType_sort",
                    new BytesRef(versionMetadata.getArtifactType())));
        }

        if (versionMetadata.getState() != null) {
            doc.add(new StringField("state", versionMetadata.getState().name(), Field.Store.YES));
        }

        // === Searchable Metadata Fields (stored for result mapping) ===
        String name = versionMetadata.getName();
        if (name != null && !name.isBlank()) {
            doc.add(new TextField("name", name, Field.Store.YES));
        }
        // Sort by name with fallback to version (matching SQL: coalesce(v.name, v.version))
        String nameSort = (name != null && !name.isBlank()) ? name
                : versionMetadata.getVersion();
        doc.add(new SortedDocValuesField("name_sort", new BytesRef(nameSort)));

        if (versionMetadata.getDescription() != null
                && !versionMetadata.getDescription().isBlank()) {
            doc.add(new TextField("description", versionMetadata.getDescription(),
                    Field.Store.YES));
        }

        // Owner field (stored for result mapping)
        if (versionMetadata.getOwner() != null && !versionMetadata.getOwner().isBlank()) {
            doc.add(new TextField("owner", versionMetadata.getOwner(), Field.Store.YES));
        }

        // ModifiedBy (stored only, not searchable)
        if (versionMetadata.getModifiedBy() != null
                && !versionMetadata.getModifiedBy().isBlank()) {
            doc.add(new StoredField("modifiedBy", versionMetadata.getModifiedBy()));
        }

        // ModifiedOn timestamp (queryable + stored + sortable)
        if (versionMetadata.getModifiedOn() > 0) {
            long modifiedOn = versionMetadata.getModifiedOn();
            doc.add(new StoredField("modifiedOn", String.valueOf(modifiedOn)));
            doc.add(new NumericDocValuesField("modifiedOn_sort", modifiedOn));
        }

        // VersionOrder (stored for result mapping)
        doc.add(new StoredField("versionOrder", versionMetadata.getVersionOrder()));

        // Labels - searchable as key=value pairs, stored as JSON for result mapping
        Map<String, String> labels = versionMetadata.getLabels();
        if (labels != null && !labels.isEmpty()) {
            for (Map.Entry<String, String> label : labels.entrySet()) {
                doc.add(new TextField("label_key", label.getKey(), Field.Store.NO));
                doc.add(new TextField("label_value", label.getValue(), Field.Store.NO));
                // Combined for "key=value" searches
                doc.add(new TextField("label", label.getKey() + "=" + label.getValue(),
                        Field.Store.NO));
            }
            // Store labels as JSON for result retrieval
            try {
                doc.add(new StoredField("labels_json", objectMapper.writeValueAsString(labels)));
            } catch (Exception e) {
                log.warn("Failed to serialize labels to JSON", e);
            }
        }

        // === Content Fields ===
        String contentText = new String(contentBytes, StandardCharsets.UTF_8);

        // Full content text search
        doc.add(new TextField("content", contentText, Field.Store.NO));

        // Type-specific structured fields (basic implementation for Phase 1)
        if (versionMetadata.getArtifactType() != null) {
            indexStructuredContent(doc, contentText, versionMetadata.getArtifactType());
        }

        // === Timestamp for freshness tracking ===
        long indexedAt = System.currentTimeMillis();
        doc.add(new StoredField("indexedAt", String.valueOf(indexedAt)));

        // Creation timestamp (stored as string for retrieval + sortable)
        if (versionMetadata.getCreatedOn() > 0) {
            long createdOn = versionMetadata.getCreatedOn();
            doc.add(new StoredField("createdOn", String.valueOf(createdOn)));
            doc.add(new NumericDocValuesField("createdOn_sort", createdOn));
        }

        log.debug("Built document for version {}/{}/{} (globalId={})", groupId,
                versionMetadata.getArtifactId(), versionMetadata.getVersion(),
                versionMetadata.getGlobalId());

        return doc;
    }

    /**
     * Deserializes a labels JSON string back into a Map.
     *
     * @param labelsJson The JSON string
     * @return A map of label key-value pairs, or empty map if null/invalid
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> deserializeLabels(String labelsJson) {
        if (labelsJson == null || labelsJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(labelsJson, Map.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize labels JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Indexes type-specific structured content. This is a basic implementation that will be
     * enhanced in future phases.
     *
     * @param doc The document to add fields to
     * @param contentText The content as text
     * @param artifactType The artifact type
     */
    private void indexStructuredContent(Document doc, String contentText, String artifactType) {
        try {
            switch (artifactType.toUpperCase()) {
            case "OPENAPI":
            case "ASYNCAPI":
            case "JSON":
                // For JSON-based content, try to extract some basic structured fields
                indexJsonContent(doc, contentText);
                break;

            case "AVRO":
            case "PROTOBUF":
                // For schema types, the content is already indexed as text
                // Future enhancement: extract schema names, field names, etc.
                break;

            default:
                // For other types, rely on full-text content indexing
                break;
            }
        } catch (Exception e) {
            log.warn("Failed to index structured content for artifact type {}: {}", artifactType,
                    e.getMessage());
            // Don't fail the entire indexing operation, just log and continue
        }
    }

    /**
     * Indexes JSON-based content by extracting common fields. This is a basic implementation for
     * Phase 1.
     *
     * @param doc The document to add fields to
     * @param jsonContent The JSON content as text
     */
    private void indexJsonContent(Document doc, String jsonContent) {
        try {
            JsonNode root = objectMapper.readTree(jsonContent);

            // Extract title/name if present
            if (root.has("title")) {
                doc.add(new TextField("content_title", root.get("title").asText(),
                        Field.Store.NO));
            }

            // Extract description if present
            if (root.has("description")) {
                doc.add(new TextField("content_description", root.get("description").asText(),
                        Field.Store.NO));
            }

            // For OpenAPI/AsyncAPI, extract API paths (basic implementation)
            if (root.has("paths")) {
                JsonNode paths = root.get("paths");
                paths.fieldNames().forEachRemaining(path -> {
                    doc.add(new StringField("openapi_path", path, Field.Store.NO));
                    doc.add(new TextField("openapi_path_text", path, Field.Store.NO));
                });
            }

            // For AsyncAPI, extract channels
            if (root.has("channels")) {
                JsonNode channels = root.get("channels");
                channels.fieldNames().forEachRemaining(channel -> {
                    doc.add(new StringField("asyncapi_channel", channel, Field.Store.NO));
                    doc.add(new TextField("asyncapi_channel_text", channel, Field.Store.NO));
                });
            }

        } catch (Exception e) {
            log.debug("Content is not valid JSON or failed to parse: {}", e.getMessage());
            // Not all JSON artifact types will have these fields, which is fine
        }
    }
}
