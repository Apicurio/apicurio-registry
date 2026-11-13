package io.apicurio.registry.json.content.extract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;

/**
 * Extracts structured elements from JSON Schema content for search indexing. Parses the JSON Schema and
 * extracts the $id, property names, definition names ($defs/definitions), and required field names.
 * Composition keywords (allOf, anyOf, oneOf) are visited recursively so that nested properties and
 * definitions are included in the results.
 */
public class JsonSchemaStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaStructuredContentExtractor.class);

    /** Composition keywords whose array elements are sub-schemas to recurse into. */
    private static final Set<String> COMPOSITION_KEYWORDS = Set.of("allOf", "anyOf", "oneOf", "if", "then", "else", "not");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            JsonNode root = objectMapper.readTree(content.content());
            List<StructuredElement> elements = new ArrayList<>();

            extractId(root, elements);
            extractFromSchema(root, elements);

            return elements;
        } catch (Exception e) {
            log.debug("Failed to extract structured content from JSON Schema: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extracts the $id value from the root schema only.
     */
    private void extractId(JsonNode root, List<StructuredElement> elements) {
        if (root.has("$id")) {
            elements.add(new StructuredElement("id", root.get("$id").asText()));
        }
    }

    /**
     * Recursively extracts properties, definitions, and required fields from a schema node,
     * including sub-schemas reachable through composition keywords (allOf, anyOf, oneOf,
     * if/then/else, not).
     */
    private void extractFromSchema(JsonNode schema, List<StructuredElement> elements) {
        if (schema == null || !schema.isObject()) {
            return;
        }

        extractProperties(schema, elements);
        extractDefinitions(schema, elements);
        extractRequired(schema, elements);

        // Recurse into composition keywords
        for (String keyword : COMPOSITION_KEYWORDS) {
            JsonNode child = schema.path(keyword);
            if (child.isArray()) {
                for (JsonNode subSchema : child) {
                    extractFromSchema(subSchema, elements);
                }
            } else if (child.isObject()) {
                // "not", "if", "then", "else" hold a single sub-schema
                extractFromSchema(child, elements);
            }
        }
    }

    /**
     * Extracts property names from the properties object of this schema node.
     */
    private void extractProperties(JsonNode schema, List<StructuredElement> elements) {
        JsonNode properties = schema.path("properties");
        if (!properties.isMissingNode() && properties.isObject()) {
            properties.fieldNames().forEachRemaining(name -> {
                elements.add(new StructuredElement("property", name));
            });
        }
    }

    /**
     * Extracts definition names from $defs and definitions of this schema node.
     */
    private void extractDefinitions(JsonNode schema, List<StructuredElement> elements) {
        // JSON Schema draft 2019-09+ uses $defs
        JsonNode defs = schema.path("$defs");
        if (!defs.isMissingNode() && defs.isObject()) {
            defs.fieldNames().forEachRemaining(name -> {
                elements.add(new StructuredElement("definition", name));
            });
        }

        // Older drafts use definitions
        JsonNode definitions = schema.path("definitions");
        if (!definitions.isMissingNode() && definitions.isObject()) {
            definitions.fieldNames().forEachRemaining(name -> {
                elements.add(new StructuredElement("definition", name));
            });
        }
    }

    /**
     * Extracts required field names from the required array of this schema node.
     */
    private void extractRequired(JsonNode schema, List<StructuredElement> elements) {
        JsonNode required = schema.path("required");
        if (!required.isMissingNode() && required.isArray()) {
            for (JsonNode req : required) {
                if (req.isTextual()) {
                    elements.add(new StructuredElement("required", req.asText()));
                }
            }
        }
    }
}
