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
 * - Changing name, description, version, annotations: Always compatible
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
