package io.apicurio.registry.iceberg.content;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.ExtractedMetaData;
import io.apicurio.registry.content.util.ContentTypeUtil;

/**
 * Content extractor for Apache Iceberg table and view metadata.
 *
 * Extracts metadata such as name and description from Iceberg metadata documents.
 */
public class IcebergContentExtractor implements ContentExtractor {

    private final boolean isTable;

    public IcebergContentExtractor(boolean isTable) {
        this.isTable = isTable;
    }

    @Override
    public ExtractedMetaData extract(ContentHandle content) {
        try {
            JsonNode tree = ContentTypeUtil.parseJson(content);

            ExtractedMetaData metaData = new ExtractedMetaData();

            // Extract uuid as name if available
            String uuidField = isTable ? "table-uuid" : "view-uuid";
            if (tree.has(uuidField) && tree.get(uuidField).isTextual()) {
                metaData.setName(tree.get(uuidField).asText());
            }

            // Extract location as description
            if (tree.has("location") && tree.get("location").isTextual()) {
                metaData.setDescription("Location: " + tree.get("location").asText());
            }

            // Try to extract properties.comment as description if available
            if (tree.has("properties") && tree.get("properties").isObject()) {
                JsonNode properties = tree.get("properties");
                if (properties.has("comment") && properties.get("comment").isTextual()) {
                    metaData.setDescription(properties.get("comment").asText());
                }
            }

            return metaData;
        } catch (Exception e) {
            return null;
        }
    }
}
