package io.apicurio.registry.iceberg.content;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.content.util.ContentTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extracts structured elements from Apache Iceberg table or view metadata for search indexing.
 *
 * For tables, extracts: table name (table-uuid), column names from schemas, partition field names,
 * and sort field names.
 *
 * For views, extracts: view name (view-uuid), column names from schemas, and SQL representations
 * from version entries.
 */
public class IcebergStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(IcebergStructuredContentExtractor.class);

    private final boolean isTable;

    /**
     * Creates a new Iceberg structured content extractor.
     *
     * @param isTable true for table metadata, false for view metadata
     */
    public IcebergStructuredContentExtractor(boolean isTable) {
        this.isTable = isTable;
    }

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            JsonNode tree = ContentTypeUtil.parseJson(content);
            List<StructuredElement> elements = new ArrayList<>();

            extractName(tree, elements);
            extractColumns(tree, elements);

            if (isTable) {
                extractPartitionFields(tree, elements);
                extractSortFields(tree, elements);
            } else {
                extractSqlRepresentations(tree, elements);
            }

            return elements;
        } catch (Exception e) {
            log.debug("Failed to extract structured content from Iceberg metadata: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extracts the table or view name from the uuid field.
     */
    private void extractName(JsonNode tree, List<StructuredElement> elements) {
        String uuidField = isTable ? "table-uuid" : "view-uuid";
        if (tree.has(uuidField) && tree.get(uuidField).isTextual()) {
            elements.add(new StructuredElement("name", tree.get(uuidField).asText()));
        }
    }

    /**
     * Extracts column names from the schemas array. Uses the current schema if current-schema-id is
     * specified, otherwise extracts columns from all schemas.
     */
    private void extractColumns(JsonNode tree, List<StructuredElement> elements) {
        if (!tree.has("schemas") || !tree.get("schemas").isArray()) {
            // Fall back to single "schema" field (format version 1)
            if (tree.has("schema") && tree.get("schema").isObject()) {
                extractFieldsFromSchema(tree.get("schema"), elements);
            }
            return;
        }

        JsonNode schemas = tree.get("schemas");
        int currentSchemaId = -1;
        if (tree.has("current-schema-id") && tree.get("current-schema-id").isInt()) {
            currentSchemaId = tree.get("current-schema-id").intValue();
        }

        // If we have a current-schema-id, only extract from the current schema
        if (currentSchemaId >= 0) {
            for (JsonNode schema : schemas) {
                if (schema.has("schema-id") && schema.get("schema-id").intValue() == currentSchemaId) {
                    extractFieldsFromSchema(schema, elements);
                    return;
                }
            }
        }

        // Otherwise extract from the last schema (most recent)
        if (schemas.size() > 0) {
            extractFieldsFromSchema(schemas.get(schemas.size() - 1), elements);
        }
    }

    /**
     * Extracts field names from a single schema node.
     */
    private void extractFieldsFromSchema(JsonNode schema, List<StructuredElement> elements) {
        if (schema.has("fields") && schema.get("fields").isArray()) {
            for (JsonNode field : schema.get("fields")) {
                if (field.has("name") && field.get("name").isTextual()) {
                    elements.add(new StructuredElement("column", field.get("name").asText()));
                }
            }
        }
    }

    /**
     * Extracts partition field names from the partition-specs array (table only).
     */
    private void extractPartitionFields(JsonNode tree, List<StructuredElement> elements) {
        if (!tree.has("partition-specs") || !tree.get("partition-specs").isArray()) {
            return;
        }

        int defaultSpecId = -1;
        if (tree.has("default-spec-id") && tree.get("default-spec-id").isInt()) {
            defaultSpecId = tree.get("default-spec-id").intValue();
        }

        for (JsonNode spec : tree.get("partition-specs")) {
            // Only extract from the default partition spec if specified
            if (defaultSpecId >= 0 && spec.has("spec-id")
                    && spec.get("spec-id").intValue() != defaultSpecId) {
                continue;
            }

            if (spec.has("fields") && spec.get("fields").isArray()) {
                for (JsonNode field : spec.get("fields")) {
                    if (field.has("name") && field.get("name").isTextual()) {
                        elements.add(new StructuredElement("partition", field.get("name").asText()));
                    }
                }
            }
        }
    }

    /**
     * Extracts sort field names from the sort-orders array (table only).
     */
    private void extractSortFields(JsonNode tree, List<StructuredElement> elements) {
        if (!tree.has("sort-orders") || !tree.get("sort-orders").isArray()) {
            return;
        }

        int defaultSortOrderId = -1;
        if (tree.has("default-sort-order-id") && tree.get("default-sort-order-id").isInt()) {
            defaultSortOrderId = tree.get("default-sort-order-id").intValue();
        }

        for (JsonNode order : tree.get("sort-orders")) {
            // Only extract from the default sort order if specified
            if (defaultSortOrderId >= 0 && order.has("order-id")
                    && order.get("order-id").intValue() != defaultSortOrderId) {
                continue;
            }

            if (order.has("fields") && order.get("fields").isArray()) {
                for (JsonNode field : order.get("fields")) {
                    if (field.has("transform") && field.get("transform").isTextual()) {
                        elements.add(new StructuredElement("sort", field.get("transform").asText()));
                    }
                }
            }
        }
    }

    /**
     * Extracts SQL representations from the versions array (view only).
     */
    private void extractSqlRepresentations(JsonNode tree, List<StructuredElement> elements) {
        if (!tree.has("versions") || !tree.get("versions").isArray()) {
            return;
        }

        // Extract from the current version if specified
        int currentVersionId = -1;
        if (tree.has("current-version-id") && tree.get("current-version-id").isInt()) {
            currentVersionId = tree.get("current-version-id").intValue();
        }

        for (JsonNode version : tree.get("versions")) {
            if (currentVersionId >= 0 && version.has("version-id")
                    && version.get("version-id").intValue() != currentVersionId) {
                continue;
            }

            if (version.has("representations") && version.get("representations").isArray()) {
                for (JsonNode rep : version.get("representations")) {
                    if (rep.has("sql") && rep.get("sql").isTextual()) {
                        elements.add(new StructuredElement("sql", rep.get("sql").asText()));
                    }
                }
            }
        }
    }
}
