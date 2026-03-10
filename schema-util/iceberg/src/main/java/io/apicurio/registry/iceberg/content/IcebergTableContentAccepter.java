package io.apicurio.registry.iceberg.content;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;

import java.util.Map;

/**
 * Content accepter for Apache Iceberg table metadata.
 *
 * Iceberg table metadata is a JSON document that describes an Iceberg table structure,
 * including schema, partitioning, and snapshot information.
 *
 * @see <a href="https://iceberg.apache.org/spec/">Apache Iceberg Specification</a>
 */
public class IcebergTableContentAccepter implements ContentAccepter {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            if (content.getContentType() != null && content.getContentType().toLowerCase().contains("json")
                    && !ContentTypeUtil.isParsableJson(content.getContent())) {
                return false;
            }

            JsonNode tree = ContentTypeUtil.parseJson(content.getContent());

            // Check for Iceberg TableMetadata structure
            // An Iceberg TableMetadata must have these key fields
            if (tree.isObject()) {
                // Check for format-version which is required in Iceberg metadata
                if (tree.has("format-version")) {
                    // Check for table-uuid which is required for Iceberg tables
                    if (tree.has("table-uuid")) {
                        return true;
                    }
                    // Check for location which is also a key field
                    if (tree.has("location") && tree.has("schema")) {
                        return true;
                    }
                    // Check for schemas array (format version 2+)
                    if (tree.has("schemas") && tree.get("schemas").isArray()) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Invalid syntax
        }
        return false;
    }
}
