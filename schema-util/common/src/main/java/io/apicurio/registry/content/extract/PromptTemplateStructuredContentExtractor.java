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
 * Extracts structured elements from Prompt Template content for search indexing.
 *
 * Extracts the template ID, variable names, and output schema property names.
 */
public class PromptTemplateStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateStructuredContentExtractor.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            JsonNode root = objectMapper.readTree(content.content());
            List<StructuredElement> elements = new ArrayList<>();

            extractTextField(root, "templateId", "templateid", elements);
            extractVariableNames(root, elements);
            extractOutputSchemaProperties(root, elements);

            return elements;
        } catch (Exception e) {
            log.debug("Failed to extract structured content from Prompt Template: {}", e.getMessage());
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

    private void extractVariableNames(JsonNode root, List<StructuredElement> elements) {
        JsonNode variables = root.path("variables");
        if (!variables.isMissingNode() && variables.isObject()) {
            Iterator<String> fieldNames = variables.fieldNames();
            while (fieldNames.hasNext()) {
                elements.add(new StructuredElement("variable", fieldNames.next()));
            }
        }
    }

    private void extractOutputSchemaProperties(JsonNode root, List<StructuredElement> elements) {
        JsonNode outputSchema = root.path("outputSchema");
        if (!outputSchema.isMissingNode() && outputSchema.isObject()) {
            JsonNode properties = outputSchema.path("properties");
            if (!properties.isMissingNode() && properties.isObject()) {
                Iterator<String> fieldNames = properties.fieldNames();
                while (fieldNames.hasNext()) {
                    elements.add(new StructuredElement("outputproperty", fieldNames.next()));
                }
            }
        }
    }
}
