package io.apicurio.registry.json.content.extract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 */
public class JsonSchemaStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaStructuredContentExtractor.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            JsonNode root = objectMapper.readTree(content.content());
            List<StructuredElement> elements = new ArrayList<>();

            extractId(root, elements);
            extractProperties(root, elements);
            extractDefinitions(root, elements);
            extractRequired(root, elements);

            return elements;
        } catch (Exception e) {
            log.debug("Failed to extract structured content from JSON Schema: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extracts the $id value from the schema.
     */
    private void extractId(JsonNode root, List<StructuredElement> elements) {
        if (root.has("$id")) {
            elements.add(new StructuredElement("id", root.get("$id").asText()));
        }
    }

    /**
     * Extracts property names from the properties object.
     */
    private void extractProperties(JsonNode root, List<StructuredElement> elements) {
        JsonNode properties = root.path("properties");
        if (!properties.isMissingNode() && properties.isObject()) {
            properties.fieldNames().forEachRemaining(name -> {
                elements.add(new StructuredElement("property", name));
            });
        }
    }

    /**
     * Extracts definition names from $defs and definitions (both are supported by JSON Schema).
     */
    private void extractDefinitions(JsonNode root, List<StructuredElement> elements) {
        // JSON Schema draft 2019-09+ uses $defs
        JsonNode defs = root.path("$defs");
        if (!defs.isMissingNode() && defs.isObject()) {
            defs.fieldNames().forEachRemaining(name -> {
                elements.add(new StructuredElement("definition", name));
            });
        }

        // Older drafts use definitions
        JsonNode definitions = root.path("definitions");
        if (!definitions.isMissingNode() && definitions.isObject()) {
            definitions.fieldNames().forEachRemaining(name -> {
                elements.add(new StructuredElement("definition", name));
            });
        }
    }

    /**
     * Extracts required field names from the required array.
     */
    private void extractRequired(JsonNode root, List<StructuredElement> elements) {
        JsonNode required = root.path("required");
        if (!required.isMissingNode() && required.isArray()) {
            for (JsonNode req : required) {
                if (req.isTextual()) {
                    elements.add(new StructuredElement("required", req.asText()));
                }
            }
        }
    }
}
