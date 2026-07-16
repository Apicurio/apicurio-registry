package io.apicurio.registry.rules.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Compatibility checker for MCP tool definition artifacts.
 *
 * Compatibility rules for MCP tools:
 * - Adding optional input parameters: Always compatible
 * - Removing input parameters: Backward incompatible
 * - Adding required parameters: Backward incompatible
 * - Removing required parameters: Backward incompatible
 * - Changing inputSchema type: Backward incompatible
 * - Changing an existing parameter's type: Backward incompatible
 * - Narrowing an existing parameter's enum: Backward incompatible
 * - Changing name, title, description, annotations: Always compatible
 */
public class McpToolCompatibilityChecker
        extends AbstractCompatibilityChecker<McpToolCompatibilityDifference> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected Set<McpToolCompatibilityDifference> isBackwardsCompatibleWith(String existing,
            String proposed, Map<String, TypedContent> resolvedReferences) {
        Set<McpToolCompatibilityDifference> differences = new HashSet<>();

        try {
            JsonNode existingNode = mapper.readTree(existing);
            JsonNode proposedNode = mapper.readTree(proposed);

            // Check inputSchema type changes
            checkInputSchemaTypeChange(existingNode, proposedNode, differences);

            // Check removed properties
            checkPropertyRemovals(existingNode, proposedNode, differences);

            // Check per-property schema changes for properties present in both versions
            checkPropertySchemaChanges(existingNode, proposedNode, differences);

            // Check added required parameters
            checkRequiredParamAdditions(existingNode, proposedNode, differences);

            // Check removed required parameters
            checkRequiredParamRemovals(existingNode, proposedNode, differences);

        } catch (Exception e) {
            differences.add(new McpToolCompatibilityDifference(
                    McpToolCompatibilityDifference.Type.PARSE_ERROR,
                    "Failed to parse MCP tool definition: " + e.getMessage()));
        }

        return differences;
    }

    private void checkInputSchemaTypeChange(JsonNode existing, JsonNode proposed,
            Set<McpToolCompatibilityDifference> differences) {
        String existingType = getInputSchemaType(existing);
        String proposedType = getInputSchemaType(proposed);

        if (existingType != null && proposedType != null && !existingType.equals(proposedType)) {
            differences.add(new McpToolCompatibilityDifference(
                    McpToolCompatibilityDifference.Type.INPUT_SCHEMA_TYPE_CHANGED,
                    "inputSchema type changed from '" + existingType + "' to '" + proposedType
                            + "'"));
        }
    }

    private void checkPropertyRemovals(JsonNode existing, JsonNode proposed,
            Set<McpToolCompatibilityDifference> differences) {
        Set<String> existingProps = extractPropertyNames(existing);
        Set<String> proposedProps = extractPropertyNames(proposed);

        for (String prop : existingProps) {
            if (!proposedProps.contains(prop)) {
                differences.add(new McpToolCompatibilityDifference(
                        McpToolCompatibilityDifference.Type.PROPERTY_REMOVED,
                        "Input property '" + prop + "' was removed"));
            }
        }
    }

    private void checkPropertySchemaChanges(JsonNode existing, JsonNode proposed,
            Set<McpToolCompatibilityDifference> differences) {
        JsonNode existingProps = getInputSchemaProperties(existing);
        JsonNode proposedProps = getInputSchemaProperties(proposed);
        if (existingProps == null || proposedProps == null) {
            return;
        }

        Iterator<String> existingNames = existingProps.fieldNames();
        while (existingNames.hasNext()) {
            String propName = existingNames.next();
            if (!proposedProps.has(propName)) {
                continue;
            }
            JsonNode existingProp = existingProps.get(propName);
            JsonNode proposedProp = proposedProps.get(propName);
            checkPropertyTypeChange(propName, existingProp, proposedProp, differences);
            checkEnumNarrowing(propName, existingProp, proposedProp, differences);
        }
    }

    private void checkPropertyTypeChange(String propName, JsonNode existingProp, JsonNode proposedProp,
            Set<McpToolCompatibilityDifference> differences) {
        String existingType = getTextValue(existingProp, "type");
        String proposedType = getTextValue(proposedProp, "type");
        if (existingType != null && proposedType != null && !existingType.equals(proposedType)) {
            differences.add(new McpToolCompatibilityDifference(
                    McpToolCompatibilityDifference.Type.PROPERTY_TYPE_CHANGED,
                    "Input property '" + propName + "' type changed from '" + existingType
                            + "' to '" + proposedType + "'"));
        }
    }

    private void checkEnumNarrowing(String propName, JsonNode existingProp, JsonNode proposedProp,
            Set<McpToolCompatibilityDifference> differences) {
        JsonNode existingEnum = existingProp.get("enum");
        JsonNode proposedEnum = proposedProp.get("enum");
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
                differences.add(new McpToolCompatibilityDifference(
                        McpToolCompatibilityDifference.Type.ENUM_VALUE_REMOVED,
                        "Input property '" + propName + "' enum value '" + val.asText()
                                + "' was removed"));
            }
        }
    }

    private JsonNode getInputSchemaProperties(JsonNode node) {
        JsonNode inputSchema = node.get("inputSchema");
        if (inputSchema != null && inputSchema.isObject()) {
            JsonNode props = inputSchema.get("properties");
            if (props != null && props.isObject()) {
                return props;
            }
        }
        return null;
    }

    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && field.isTextual()) ? field.asText() : null;
    }

    private void checkRequiredParamAdditions(JsonNode existing, JsonNode proposed,
            Set<McpToolCompatibilityDifference> differences) {
        Set<String> existingRequired = extractRequiredParams(existing);
        Set<String> proposedRequired = extractRequiredParams(proposed);

        for (String param : proposedRequired) {
            if (!existingRequired.contains(param)) {
                differences.add(new McpToolCompatibilityDifference(
                        McpToolCompatibilityDifference.Type.REQUIRED_PARAM_ADDED,
                        "Required parameter '" + param + "' was added"));
            }
        }
    }

    private void checkRequiredParamRemovals(JsonNode existing, JsonNode proposed,
            Set<McpToolCompatibilityDifference> differences) {
        Set<String> existingRequired = extractRequiredParams(existing);
        Set<String> proposedRequired = extractRequiredParams(proposed);

        for (String param : existingRequired) {
            if (!proposedRequired.contains(param)) {
                differences.add(new McpToolCompatibilityDifference(
                        McpToolCompatibilityDifference.Type.REQUIRED_PARAM_REMOVED,
                        "Required parameter '" + param + "' was removed"));
            }
        }
    }

    private String getInputSchemaType(JsonNode node) {
        JsonNode inputSchema = node.get("inputSchema");
        if (inputSchema != null && inputSchema.isObject()) {
            JsonNode type = inputSchema.get("type");
            if (type != null && type.isTextual()) {
                return type.asText();
            }
        }
        return null;
    }

    private Set<String> extractPropertyNames(JsonNode node) {
        Set<String> properties = new HashSet<>();
        JsonNode inputSchema = node.get("inputSchema");
        if (inputSchema != null && inputSchema.isObject()) {
            JsonNode props = inputSchema.get("properties");
            if (props != null && props.isObject()) {
                Iterator<String> fieldNames = props.fieldNames();
                while (fieldNames.hasNext()) {
                    properties.add(fieldNames.next());
                }
            }
        }
        return properties;
    }

    private Set<String> extractRequiredParams(JsonNode node) {
        Set<String> required = new HashSet<>();
        JsonNode inputSchema = node.get("inputSchema");
        if (inputSchema != null && inputSchema.isObject()) {
            JsonNode requiredNode = inputSchema.get("required");
            if (requiredNode != null && requiredNode.isArray()) {
                for (JsonNode item : requiredNode) {
                    if (item.isTextual()) {
                        required.add(item.asText());
                    }
                }
            }
        }
        return required;
    }

    @Override
    protected CompatibilityDifference transform(McpToolCompatibilityDifference original) {
        return original;
    }
}
