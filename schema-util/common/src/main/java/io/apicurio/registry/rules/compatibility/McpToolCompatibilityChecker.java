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
 * - Changing name, title, description, annotations: Always compatible
 */
public class McpToolCompatibilityChecker
        extends AbstractCompatibilityChecker<SimpleCompatibilityDifference> {

    private static final String CONTEXT_REQUIRED = "/inputSchema/required";
    private static final String CONTEXT_TYPE = "/inputSchema/type";
    private static final String CONTEXT_PROPERTIES = "/inputSchema/properties";
    private static final String CONTEXT_DOCUMENT = "/document";

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected Set<SimpleCompatibilityDifference> isBackwardsCompatibleWith(String existing,
            String proposed, Map<String, TypedContent> resolvedReferences) {
        Set<SimpleCompatibilityDifference> differences = new HashSet<>();

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
            differences.add(new SimpleCompatibilityDifference(
                    "Failed to parse MCP tool definition: " + e.getMessage(), CONTEXT_DOCUMENT));
        }

        return differences;
    }

    private void checkInputSchemaTypeChange(JsonNode existing, JsonNode proposed,
            Set<SimpleCompatibilityDifference> differences) {
        String existingType = getInputSchemaType(existing);
        String proposedType = getInputSchemaType(proposed);

        if (existingType != null && proposedType != null && !existingType.equals(proposedType)) {
            differences.add(new SimpleCompatibilityDifference(
                    "inputSchema type changed from '" + existingType + "' to '" + proposedType + "'",
                    CONTEXT_TYPE));
        }
    }

    private void checkPropertyRemovals(JsonNode existing, JsonNode proposed,
            Set<SimpleCompatibilityDifference> differences) {
        Set<String> existingProps = extractPropertyNames(existing);
        Set<String> proposedProps = extractPropertyNames(proposed);

        for (String prop : existingProps) {
            if (!proposedProps.contains(prop)) {
                differences.add(new SimpleCompatibilityDifference(
                        "Input property '" + prop + "' was removed", CONTEXT_PROPERTIES));
            }
        }
    }

    private void checkRequiredParamAdditions(JsonNode existing, JsonNode proposed,
            Set<SimpleCompatibilityDifference> differences) {
        Set<String> existingRequired = extractRequiredParams(existing);
        Set<String> proposedRequired = extractRequiredParams(proposed);

        for (String param : proposedRequired) {
            if (!existingRequired.contains(param)) {
                differences.add(new SimpleCompatibilityDifference(
                        "Required parameter '" + param + "' was added", CONTEXT_REQUIRED));
            }
        }
    }

    private void checkRequiredParamRemovals(JsonNode existing, JsonNode proposed,
            Set<SimpleCompatibilityDifference> differences) {
        Set<String> existingRequired = extractRequiredParams(existing);
        Set<String> proposedRequired = extractRequiredParams(proposed);

        for (String param : existingRequired) {
            if (!proposedRequired.contains(param)) {
                differences.add(new SimpleCompatibilityDifference(
                        "Required parameter '" + param + "' was removed", CONTEXT_REQUIRED));
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
}
