package io.apicurio.registry.rules.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * - Changing name, title, description, annotations: Always compatible
 */
public class McpToolCompatibilityChecker
        extends AbstractCompatibilityChecker<McpToolCompatibilityDifference> {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String INPUT_PROPERTY_PREFIX = "Input property '";
    private static final String WAS_REMOVED_SUFFIX = "' was removed";

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

            // Check property type changes
            checkPropertyTypeChanges(existingNode, proposedNode, differences);

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

        if (existingType != null && (proposedType == null || !existingType.equals(proposedType))) {
            String message = proposedType == null
                    ? "inputSchema type '" + existingType + WAS_REMOVED_SUFFIX
                    : "inputSchema type changed from '" + existingType + "' to '" + proposedType + "'";
            differences.add(new McpToolCompatibilityDifference(
                    McpToolCompatibilityDifference.Type.INPUT_SCHEMA_TYPE_CHANGED,
                    message));
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
                        INPUT_PROPERTY_PREFIX + prop + WAS_REMOVED_SUFFIX));
            }
        }
    }

    private void checkPropertyTypeChanges(JsonNode existing, JsonNode proposed,
            Set<McpToolCompatibilityDifference> differences) {
        Set<String> existingProps = extractPropertyNames(existing);
        Set<String> proposedProps = extractPropertyNames(proposed);

        for (String prop : existingProps) {
            if (proposedProps.contains(prop)) {
                String existingType = getPropertyType(existing, prop);
                String proposedType = getPropertyType(proposed, prop);

                if (!Objects.equals(existingType, proposedType)) {
                    String message;
                    if (existingType != null && proposedType == null) {
                        message = INPUT_PROPERTY_PREFIX + prop + "' type '" + existingType + WAS_REMOVED_SUFFIX;
                    } else if (existingType == null && proposedType != null) {
                        message = INPUT_PROPERTY_PREFIX + prop + "' type constraint '" + proposedType + "' was added";
                    } else {
                        message = INPUT_PROPERTY_PREFIX + prop + "' type changed from '" + existingType
                                + "' to '" + proposedType + "'";
                    }
                    differences.add(new McpToolCompatibilityDifference(
                            McpToolCompatibilityDifference.Type.PROPERTY_TYPE_CHANGED,
                            message));
                }
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
                        "Required parameter '" + param + WAS_REMOVED_SUFFIX));
            }
        }
    }

    private String getInputSchemaType(JsonNode node) {
        JsonNode inputSchema = node.get("inputSchema");
        if (inputSchema != null && inputSchema.isObject()) {
            return extractTypeString(inputSchema.get("type"));
        }
        return null;
    }

    private Set<String> extractPropertyNames(JsonNode node) {
        Set<String> properties = new HashSet<>();
        JsonNode props = getPropertiesNode(node);
        if (props != null) {
            Iterator<String> fieldNames = props.fieldNames();
            while (fieldNames.hasNext()) {
                properties.add(fieldNames.next());
            }
        }
        return properties;
    }

    private String getPropertyType(JsonNode node, String propName) {
        JsonNode props = getPropertiesNode(node);
        if (props != null) {
            JsonNode propNode = props.get(propName);
            if (propNode != null && propNode.isObject()) {
                return extractTypeString(propNode.get("type"));
            }
        }
        return null;
    }

    private JsonNode getPropertiesNode(JsonNode node) {
        JsonNode inputSchema = node.get("inputSchema");
        if (inputSchema != null && inputSchema.isObject()) {
            JsonNode props = inputSchema.get("properties");
            if (props != null && props.isObject()) {
                return props;
            }
        }
        return null;
    }

    private String extractTypeString(JsonNode typeNode) {
        if (typeNode == null) {
            return null;
        }
        if (typeNode.isTextual()) {
            return typeNode.asText();
        }
        if (typeNode.isArray()) {
            List<String> types = new ArrayList<>();
            for (JsonNode item : typeNode) {
                if (item.isTextual()) {
                    types.add(item.asText());
                }
            }
            if (!types.isEmpty()) {
                if (types.size() == 1) {
                    return types.get(0);
                }
                Collections.sort(types);
                return types.toString();
            }
        }
        return null;
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
