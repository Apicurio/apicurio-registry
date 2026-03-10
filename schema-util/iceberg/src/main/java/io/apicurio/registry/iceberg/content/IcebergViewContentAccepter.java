package io.apicurio.registry.iceberg.content;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;

import java.util.Map;

/**
 * Content accepter for Apache Iceberg view metadata.
 *
 * Iceberg view metadata is a JSON document that describes an Iceberg view,
 * including SQL representation and schema information.
 *
 * @see <a href="https://iceberg.apache.org/spec/">Apache Iceberg Specification</a>
 */
public class IcebergViewContentAccepter implements ContentAccepter {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            if (content.getContentType() != null && content.getContentType().toLowerCase().contains("json")
                    && !ContentTypeUtil.isParsableJson(content.getContent())) {
                return false;
            }

            JsonNode tree = ContentTypeUtil.parseJson(content.getContent());

            // Check for Iceberg View metadata structure
            if (tree.isObject()) {
                // Check for format-version which is required
                if (tree.has("format-version")) {
                    // Views have view-uuid instead of table-uuid
                    if (tree.has("view-uuid")) {
                        return true;
                    }
                    // Check for representations which contain the SQL
                    if (tree.has("representations") && tree.get("representations").isArray()) {
                        return true;
                    }
                    // Check for current-version-id which is specific to views
                    if (tree.has("current-version-id") && tree.has("versions")) {
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
