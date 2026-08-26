package io.apicurio.registry.rules.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.validity.PromptTemplateContentValidator;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compatibility checker for Prompt Template artifacts.
 *
 * Backward compatibility rules:
 * - Cannot remove variables that are still used in the template
 * - Cannot change variable types
 * - Cannot make optional variables required
 * - Cannot narrow enum values (remove allowed values)
 * - Can add new optional variables
 * - Cannot remove outputSchema properties
 */
public class PromptTemplateCompatibilityChecker
        extends AbstractCompatibilityChecker<SimpleCompatibilityDifference> {

    private static final String CONTEXT_VARIABLES = "/variables";
    private static final String CONTEXT_OUTPUT_SCHEMA = "/outputSchema";
    private static final String CONTEXT_OUTPUT_SCHEMA_PROPERTIES = "/outputSchema/properties";
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

            checkVariableCompatibility(existingNode, proposedNode, differences);
            checkOutputSchemaCompatibility(existingNode, proposedNode, differences);

        } catch (Exception e) {
            differences.add(new SimpleCompatibilityDifference(
                    "Failed to parse Prompt Template: " + e.getMessage(), CONTEXT_DOCUMENT));
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

    private void checkVariableCompatibility(JsonNode existing, JsonNode proposed,
            Set<SimpleCompatibilityDifference> differences) {
        JsonNode existingVars = existing.get("variables");
        JsonNode proposedVars = proposed.get("variables");

        if (existingVars == null || !existingVars.isObject()) {
            return;
        }

        List<String> proposedTemplateVars = List.of();
        if (proposed.has("template") && proposed.get("template").isTextual()) {
            proposedTemplateVars = PromptTemplateContentValidator.extractTemplateVariables(
                    proposed.get("template").asText());
        }

        Iterator<String> existingVarNames = existingVars.fieldNames();
        while (existingVarNames.hasNext()) {
            String varName = existingVarNames.next();

            if (proposedVars == null || !proposedVars.has(varName)) {
                if (proposedTemplateVars.contains(varName)) {
                    differences.add(new SimpleCompatibilityDifference(
                            "Variable '" + varName + "' was removed but is still used in the template.",
                            CONTEXT_VARIABLES));
                }
                continue;
            }

            JsonNode existingVar = existingVars.get(varName);
            JsonNode proposedVar = proposedVars.get(varName);

            checkVariableTypeChange(varName, existingVar, proposedVar, differences);
            checkVariableBecameRequired(varName, existingVar, proposedVar, differences);
            checkEnumNarrowing(varName, existingVar, proposedVar, differences);
        }
    }

    private void checkVariableTypeChange(String varName, JsonNode existingVar, JsonNode proposedVar,
            Set<SimpleCompatibilityDifference> differences) {
        String existingType = getTextValue(existingVar, "type");
        String proposedType = getTextValue(proposedVar, "type");

        if (existingType != null && proposedType != null && !existingType.equals(proposedType)) {
            differences.add(new SimpleCompatibilityDifference(
                    "Variable '" + varName + "' type changed from '" + existingType
                            + "' to '" + proposedType + "'.",
                    CONTEXT_VARIABLES));
        }
    }

    private void checkVariableBecameRequired(String varName, JsonNode existingVar, JsonNode proposedVar,
            Set<SimpleCompatibilityDifference> differences) {
        boolean wasRequired = existingVar.has("required") && existingVar.get("required").asBoolean(false);
        boolean isRequired = proposedVar.has("required") && proposedVar.get("required").asBoolean(false);

        if (!wasRequired && isRequired) {
            differences.add(new SimpleCompatibilityDifference(
                    "Variable '" + varName + "' changed from optional to required.", CONTEXT_VARIABLES));
        }
    }

    private void checkEnumNarrowing(String varName, JsonNode existingVar, JsonNode proposedVar,
            Set<SimpleCompatibilityDifference> differences) {
        JsonNode existingEnum = existingVar.get("enum");
        JsonNode proposedEnum = proposedVar.get("enum");

        if (existingEnum == null || !existingEnum.isArray()
                || proposedEnum == null || !proposedEnum.isArray()) {
            return;
        }

        Set<String> proposedValues = new HashSet<>();
        for (JsonNode val : proposedEnum) {
            proposedValues.add(val.asText());
        }

        for (JsonNode val : existingEnum) {
            if (!proposedValues.contains(val.asText())) {
                differences.add(new SimpleCompatibilityDifference(
                        "Variable '" + varName + "' enum value '" + val.asText() + "' was removed.",
                        CONTEXT_VARIABLES));
            }
        }
    }

    private void checkOutputSchemaCompatibility(JsonNode existing, JsonNode proposed,
            Set<SimpleCompatibilityDifference> differences) {
        JsonNode existingSchema = existing.get("outputSchema");
        JsonNode proposedSchema = proposed.get("outputSchema");

        if (existingSchema == null || !existingSchema.isObject()) {
            return;
        }

        if (proposedSchema == null || !proposedSchema.isObject()) {
            differences.add(new SimpleCompatibilityDifference(
                    "Output schema was removed.", CONTEXT_OUTPUT_SCHEMA));
            return;
        }

        JsonNode existingProps = existingSchema.get("properties");
        JsonNode proposedProps = proposedSchema.get("properties");

        if (existingProps != null && existingProps.isObject()) {
            Iterator<String> propNames = existingProps.fieldNames();
            while (propNames.hasNext()) {
                String propName = propNames.next();
                if (proposedProps == null || !proposedProps.has(propName)) {
                    differences.add(new SimpleCompatibilityDifference(
                            "Output schema property '" + propName + "' was removed.",
                            CONTEXT_OUTPUT_SCHEMA_PROPERTIES));
                }
            }
        }
    }

    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && field.isTextual()) ? field.asText() : null;
    }
}
