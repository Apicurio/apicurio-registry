package io.apicurio.registry.rules.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compatibility checker for MCP tool definition artifacts.
 *
 * Compatibility rules for MCP tools:
 * - Adding optional input parameters: Always compatible
 * - Removing input parameters: Backward incompatible
 * - Adding required parameters: Backward incompatible
 * - Removing required parameters (making optional): Always compatible
 * - Changing inputSchema type: Backward incompatible
 * - Changing name, title, description, annotations: Always compatible
 */
public class McpToolCompatibilityChecker
        extends AbstractCompatibilityChecker<McpToolCompatibilityDifference> {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String INPUT_SCHEMA = "inputSchema";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String TYPE = "type";

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

        } catch (Exception e) {
            differences.add(new McpToolCompatibilityDifference(
                    McpToolCompatibilityDifference.Type.PARSE_ERROR,
                    "Failed to parse MCP tool definition: " + e.getMessage()));
        }

        return differences;
    }

    private void checkInputSchemaTypeChange(JsonNode existing, JsonNode proposed,
            Set<McpToolCompatibilityDifference> differences) {
        Set<JsonNode> existingTypes = getInputSchemaTypes(existing);
        Set<JsonNode> proposedTypes = getInputSchemaTypes(proposed);

        if (!existingTypes.isEmpty() && !proposedTypes.isEmpty() && !existingTypes.equals(proposedTypes)) {
            differences.add(new McpToolCompatibilityDifference(
                    McpToolCompatibilityDifference.Type.INPUT_SCHEMA_TYPE_CHANGED,
                    "inputSchema type changed from '" + formatTypes(existingTypes) + "' to '" + formatTypes(proposedTypes)
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
        Set<JsonNode> existingRequired = extractRequiredParams(existing);
        Set<JsonNode> proposedRequired = extractRequiredParams(proposed);

        for (JsonNode param : proposedRequired) {
            if (!existingRequired.contains(param)) {
                String paramStr = param.isTextual() ? param.asText() : param.toString();
                differences.add(new McpToolCompatibilityDifference(
                        McpToolCompatibilityDifference.Type.REQUIRED_PARAM_ADDED,
                        "Required parameter '" + paramStr + "' was added"));
            }
        }
    }

    private Set<JsonNode> getInputSchemaTypes(JsonNode node) {
        Set<JsonNode> types = new HashSet<>();
        JsonNode inputSchema = node.get(INPUT_SCHEMA);
        if (inputSchema != null && inputSchema.isObject()) {
            JsonNode typeNode = inputSchema.get(TYPE);
            if (typeNode != null) {
                if (typeNode.isArray()) {
                    for (JsonNode item : typeNode) {
                        types.add(item);
                    }
                } else {
                    types.add(typeNode);
                }
            }
        }
        return types;
    }

    private String formatTypes(Set<JsonNode> types) {
        if (types.size() == 1) {
            JsonNode node = types.iterator().next();
            return node.isTextual() ? node.asText() : node.toString();
        }
        Set<String> sortedFormatted = new TreeSet<>();
        for (JsonNode node : types) {
            sortedFormatted.add(node.isTextual() ? "\"" + node.asText() + "\"" : node.toString());
        }
        return sortedFormatted.toString();
    }

    private Set<String> extractPropertyNames(JsonNode node) {
        Set<String> properties = new HashSet<>();
        JsonNode inputSchema = node.get(INPUT_SCHEMA);
        if (inputSchema != null && inputSchema.isObject()) {
            JsonNode props = inputSchema.get(PROPERTIES);
            if (props != null && props.isObject()) {
                Iterator<String> fieldNames = props.fieldNames();
                while (fieldNames.hasNext()) {
                    properties.add(fieldNames.next());
                }
            }
        }
        return properties;
    }

    private Set<JsonNode> extractRequiredParams(JsonNode node) {
        Set<JsonNode> required = new HashSet<>();
        JsonNode inputSchema = node.get(INPUT_SCHEMA);
        if (inputSchema != null && inputSchema.isObject()) {
            JsonNode requiredNode = inputSchema.get(REQUIRED);
            if (requiredNode != null && requiredNode.isArray()) {
                for (JsonNode item : requiredNode) {
                    required.add(item);
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

