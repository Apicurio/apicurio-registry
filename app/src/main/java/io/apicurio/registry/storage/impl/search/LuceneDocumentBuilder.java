package io.apicurio.registry.storage.impl.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;
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
import java.util.List;
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
     * @param extractor The structured content extractor for the artifact type (may be null)
     * @return A Lucene Document ready for indexing
     */
    public Document buildVersionDocument(ArtifactVersionMetaDataDto versionMetadata,
            byte[] contentBytes, StructuredContentExtractor extractor) {
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

        // Structured content extraction via the extractor
        if (extractor != null && versionMetadata.getArtifactType() != null) {
            indexStructuredElements(doc, contentBytes, versionMetadata.getArtifactType(),
                    extractor);
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
     * Builds a Lucene Document without structured content extraction. Used when the extractor is
     * not available (e.g. in tests or when the artifact type is unknown).
     *
     * @param versionMetadata The version metadata
     * @param contentBytes The artifact content as bytes
     * @return A Lucene Document ready for indexing
     */
    public Document buildVersionDocument(ArtifactVersionMetaDataDto versionMetadata,
            byte[] contentBytes) {
        return buildVersionDocument(versionMetadata, contentBytes, null);
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
     * Indexes structured elements extracted from artifact content using the type-specific
     * extractor. Each element is indexed in three fields:
     * <ul>
     * <li>{@code structure} - exact match: "{artifactType}:{kind}:{name}" (lowercased)</li>
     * <li>{@code structure_text} - tokenized: "{kind} {name}" (for text search)</li>
     * <li>{@code structure_kind} - exact match: "{artifactType}:{kind}" (lowercased)</li>
     * </ul>
     *
     * @param doc The document to add fields to
     * @param contentBytes The content as bytes
     * @param artifactType The artifact type
     * @param extractor The structured content extractor
     */
    private void indexStructuredElements(Document doc, byte[] contentBytes, String artifactType,
            StructuredContentExtractor extractor) {
        try {
            ContentHandle contentHandle = ContentHandle.create(contentBytes);
            List<StructuredElement> elements = extractor.extract(contentHandle);

            String artifactTypeLower = artifactType.toLowerCase();
            for (StructuredElement element : elements) {
                String kindLower = element.kind().toLowerCase();
                String nameLower = element.name().toLowerCase();

                // Exact match: "artifactType:kind:name"
                String structureValue = artifactTypeLower + ":" + kindLower + ":" + nameLower;
                doc.add(new StringField("structure", structureValue, Field.Store.NO));

                // Tokenized text: "kind name" (for text search)
                String structureText = element.kind() + " " + element.name();
                doc.add(new TextField("structure_text", structureText, Field.Store.NO));

                // Kind facet: "artifactType:kind"
                String structureKind = artifactTypeLower + ":" + kindLower;
                doc.add(new StringField("structure_kind", structureKind, Field.Store.NO));
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
