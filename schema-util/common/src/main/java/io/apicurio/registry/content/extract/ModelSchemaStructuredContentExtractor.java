package io.apicurio.registry.content.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Extracts structured elements from AI/ML Model Schema content for search indexing.
 *
 * Extracts the model ID, provider, and input/output property names.
 */
public class ModelSchemaStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(ModelSchemaStructuredContentExtractor.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            JsonNode root = objectMapper.readTree(content.content());
            List<StructuredElement> elements = new ArrayList<>();

            extractTextField(root, "modelId", "modelid", elements);
            extractTextField(root, "provider", "provider", elements);
            extractSchemaProperties(root, "input", "inputproperty", elements);
            extractSchemaProperties(root, "output", "outputproperty", elements);

            return elements;
        } catch (Exception e) {
            log.debug("Failed to extract structured content from Model Schema: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void extractTextField(JsonNode root, String fieldName, String elementType,
            List<StructuredElement> elements) {
        JsonNode field = root.path(fieldName);
        if (!field.isMissingNode() && field.isTextual()) {
            elements.add(new StructuredElement(elementType, field.asText()));
        }
    }

    private void extractSchemaProperties(JsonNode root, String schemaField, String elementType,
            List<StructuredElement> elements) {
        JsonNode schema = root.path(schemaField);
        if (!schema.isMissingNode() && schema.isObject()) {
            JsonNode properties = schema.path("properties");
            if (!properties.isMissingNode() && properties.isObject()) {
                Iterator<String> fieldNames = properties.fieldNames();
                while (fieldNames.hasNext()) {
                    elements.add(new StructuredElement(elementType, fieldNames.next()));
                }
            }
        }
    }
}
