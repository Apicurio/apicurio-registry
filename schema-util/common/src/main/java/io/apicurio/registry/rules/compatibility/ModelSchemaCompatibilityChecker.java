package io.apicurio.registry.rules.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.TypedContent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Compatibility checker for AI/ML Model Schema artifacts.
 *
 * Backward compatibility rules:
 * - Cannot remove required input fields
 * - Cannot add new required input fields
 * - Cannot change input/output property types
 * - Cannot remove input or output properties
 * - Cannot remove input or output schemas entirely
 */
public class ModelSchemaCompatibilityChecker
        extends AbstractCompatibilityChecker<SimpleCompatibilityDifference> {

    private static final String CONTEXT_INPUT = "/input";
    private static final String CONTEXT_INPUT_REQUIRED = "/input/required";
    private static final String CONTEXT_OUTPUT = "/output";
    private static final String CONTEXT_DOCUMENT = "/document";

    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Override
    protected Set<SimpleCompatibilityDifference> isBackwardsCompatibleWith(String existing,
            String proposed, Map<String, TypedContent> resolvedReferences) {
        Set<SimpleCompatibilityDifference> differences = new HashSet<>();

        try {
            JsonNode existingNode = parseContent(existing);
            JsonNode proposedNode = parseContent(proposed);

            checkInputSchemaCompatibility(existingNode, proposedNode, differences);
            checkOutputSchemaCompatibility(existingNode, proposedNode, differences);

        } catch (Exception e) {
            differences.add(new SimpleCompatibilityDifference(
                    "Failed to parse Model Schema: " + e.getMessage(), CONTEXT_DOCUMENT));
        }

        return differences;
    }

    private JsonNode parseContent(String content) throws Exception {
        String trimmed = content.trim();
        if (trimmed.startsWith("{")) {
            return jsonMapper.readTree(content);
        }
        return yamlMapper.readTree(content);
    }

    private void checkInputSchemaCompatibility(JsonNode existing, JsonNode proposed,
            Set<SimpleCompatibilityDifference> differences) {
        JsonNode existingInput = existing.get("input");
        JsonNode proposedInput = proposed.get("input");

        if (existingInput != null && existingInput.isObject()) {
            if (proposedInput == null || !proposedInput.isObject()) {
                differences.add(new SimpleCompatibilityDifference(
                        "Input schema was removed.", CONTEXT_INPUT));
                return;
            }

            checkRequiredFieldChanges(existingInput, proposedInput, differences);
            checkPropertyRemovals(existingInput, proposedInput, "input", differences);
            checkPropertyTypeChanges(existingInput, proposedInput, "input", differences);
        }
    }

    private void checkOutputSchemaCompatibility(JsonNode existing, JsonNode proposed,
            Set<SimpleCompatibilityDifference> differences) {
        JsonNode existingOutput = existing.get("output");
        JsonNode proposedOutput = proposed.get("output");

        if (existingOutput != null && existingOutput.isObject()) {
            if (proposedOutput == null || !proposedOutput.isObject()) {
                differences.add(new SimpleCompatibilityDifference(
                        "Output schema was removed.", CONTEXT_OUTPUT));
                return;
            }

            checkPropertyRemovals(existingOutput, proposedOutput, "output", differences);
            checkPropertyTypeChanges(existingOutput, proposedOutput, "output", differences);
        }
    }

    private void checkRequiredFieldChanges(JsonNode existingInput, JsonNode proposedInput,
            Set<SimpleCompatibilityDifference> differences) {
        Set<String> existingRequired = extractRequiredFields(existingInput);
        Set<String> proposedRequired = extractRequiredFields(proposedInput);

        for (String field : proposedRequired) {
            if (!existingRequired.contains(field)) {
                differences.add(new SimpleCompatibilityDifference(
                        "New required input field '" + field + "' was added. This breaks backward compatibility.",
                        CONTEXT_INPUT_REQUIRED));
            }
        }
    }

    private void checkPropertyRemovals(JsonNode existingSchema, JsonNode proposedSchema,
            String schemaName, Set<SimpleCompatibilityDifference> differences) {
        Set<String> existingProps = extractPropertyNames(existingSchema);
        Set<String> proposedProps = extractPropertyNames(proposedSchema);

        for (String prop : existingProps) {
            if (!proposedProps.contains(prop)) {
                differences.add(new SimpleCompatibilityDifference(
                        capitalizeFirst(schemaName) + " property '" + prop + "' was removed.",
                        propertiesContext(schemaName)));
            }
        }
    }

    private void checkPropertyTypeChanges(JsonNode existingSchema, JsonNode proposedSchema,
            String schemaName, Set<SimpleCompatibilityDifference> differences) {
        Set<String> existingProps = extractPropertyNames(existingSchema);
        Set<String> proposedProps = extractPropertyNames(proposedSchema);

        for (String prop : existingProps) {
            if (proposedProps.contains(prop)) {
                String existingType = getPropertyType(existingSchema, prop);
                String proposedType = getPropertyType(proposedSchema, prop);
                if (existingType != null && proposedType != null && !existingType.equals(proposedType)) {
                    differences.add(new SimpleCompatibilityDifference(
                            capitalizeFirst(schemaName) + " property '" + prop + "' type changed from '"
                                    + existingType + "' to '" + proposedType + "'.",
                            propertiesContext(schemaName)));
                }
            }
        }
    }

    private Set<String> extractRequiredFields(JsonNode schema) {
        Set<String> required = new HashSet<>();
        JsonNode requiredNode = schema.get("required");
        if (requiredNode != null && requiredNode.isArray()) {
            for (JsonNode item : requiredNode) {
                if (item.isTextual()) {
                    required.add(item.asText());
                }
            }
        }
        return required;
    }

    private Set<String> extractPropertyNames(JsonNode schema) {
        Set<String> names = new HashSet<>();
        JsonNode properties = schema.get("properties");
        if (properties != null && properties.isObject()) {
            Iterator<String> fieldNames = properties.fieldNames();
            while (fieldNames.hasNext()) {
                names.add(fieldNames.next());
            }
        }
        return names;
    }

    private String getPropertyType(JsonNode schema, String propName) {
        JsonNode properties = schema.get("properties");
        if (properties != null && properties.isObject() && properties.has(propName)) {
            JsonNode prop = properties.get(propName);
            if (prop.isObject() && prop.has("type") && prop.get("type").isTextual()) {
                return prop.get("type").asText();
            }
        }
        return null;
    }

    private static String propertiesContext(String schemaName) {
        return "/" + schemaName + "/properties";
    }

    private static String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + s.substring(1);
    }
}
