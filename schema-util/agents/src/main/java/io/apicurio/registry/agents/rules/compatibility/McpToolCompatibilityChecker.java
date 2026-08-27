package io.apicurio.registry.agents.rules.compatibility;

import io.apicurio.registry.rules.compatibility.AbstractCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.SimpleCompatibilityDifference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;

import java.util.Collections;
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
        extends AbstractCompatibilityChecker<SimpleCompatibilityDifference> {

    private static final String CONTEXT_REQUIRED = "/inputSchema/required";
    private static final String CONTEXT_TYPE = "/inputSchema/type";
    private static final String CONTEXT_PROPERTIES = "/inputSchema/properties";
    private static final String CONTEXT_DOCUMENT = "/document";

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String INPUT_SCHEMA = "inputSchema";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String TYPE = "type";

    @Override
    protected Set<SimpleCompatibilityDifference> isBackwardsCompatibleWith(String existing,
            String proposed, Map<String, TypedContent> resolvedReferences) {
        Set<SimpleCompatibilityDifference> differences = new HashSet<>();

        try {
            JsonNode existingNode = mapper.readTree(existing);
            JsonNode proposedNode = mapper.readTree(proposed);

            // Fast-path: if JSON trees are identical, no differences exist
            if (existingNode.equals(proposedNode)) {
                return Collections.emptySet();
            }

            JsonNode existingSchema = existingNode.get(INPUT_SCHEMA);
            JsonNode proposedSchema = proposedNode.get(INPUT_SCHEMA);

            // Check inputSchema type changes
            checkInputSchemaTypeChange(existingSchema, proposedSchema, differences);

            // Check removed properties
            checkPropertyRemovals(existingSchema, proposedSchema, differences);

            // Check added required parameters
            checkRequiredParamAdditions(existingSchema, proposedSchema, differences);

        } catch (Exception e) {
            differences.add(new SimpleCompatibilityDifference(
                    "Failed to parse MCP tool definition: " + e.getMessage(), CONTEXT_DOCUMENT));
        }

        return differences;
    }

    private void checkInputSchemaTypeChange(JsonNode existingSchema, JsonNode proposedSchema,
            Set<SimpleCompatibilityDifference> differences) {
        Set<JsonNode> existingTypes = getInputSchemaTypes(existingSchema);
        Set<JsonNode> proposedTypes = getInputSchemaTypes(proposedSchema);

        if (!existingTypes.isEmpty() && !proposedTypes.isEmpty() && !existingTypes.equals(proposedTypes)) {
            differences.add(new SimpleCompatibilityDifference(
                    "inputSchema type changed from '" + formatTypes(existingTypes) + "' to '" + formatTypes(proposedTypes) + "'",
                    CONTEXT_TYPE));
        }
    }

    private void checkPropertyRemovals(JsonNode existingSchema, JsonNode proposedSchema,
            Set<SimpleCompatibilityDifference> differences) {
        Set<String> existingProps = extractPropertyNames(existingSchema);
        Set<String> proposedProps = extractPropertyNames(proposedSchema);

        for (String prop : existingProps) {
            if (!proposedProps.contains(prop)) {
                differences.add(new SimpleCompatibilityDifference(
                        "Input property '" + prop + "' was removed", CONTEXT_PROPERTIES));
            }
        }
    }

    private void checkRequiredParamAdditions(JsonNode existingSchema, JsonNode proposedSchema,
            Set<SimpleCompatibilityDifference> differences) {
        Set<JsonNode> existingRequired = extractRequiredParams(existingSchema);
        Set<JsonNode> proposedRequired = extractRequiredParams(proposedSchema);

        for (JsonNode param : proposedRequired) {
            if (!existingRequired.contains(param)) {
                String paramStr = param.isTextual() ? param.asText() : param.toString();
                differences.add(new SimpleCompatibilityDifference(
                        "Required parameter '" + paramStr + "' was added", CONTEXT_REQUIRED));
            }
        }
    }

    private Set<JsonNode> getInputSchemaTypes(JsonNode inputSchema) {
        if (inputSchema == null || !inputSchema.isObject()) {
            return Collections.emptySet();
        }
        JsonNode typeNode = inputSchema.get(TYPE);
        if (typeNode == null) {
            return Collections.emptySet();
        }
        if (typeNode.isArray()) {
            Set<JsonNode> types = new HashSet<>(typeNode.size());
            for (JsonNode item : typeNode) {
                types.add(item);
            }
            return types;
        } else {
            return Collections.singleton(typeNode);
        }
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

    private Set<String> extractPropertyNames(JsonNode inputSchema) {
        if (inputSchema == null || !inputSchema.isObject()) {
            return Collections.emptySet();
        }
        JsonNode props = inputSchema.get(PROPERTIES);
        if (props == null || !props.isObject() || props.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> properties = new HashSet<>(props.size());
        Iterator<String> fieldNames = props.fieldNames();
        while (fieldNames.hasNext()) {
            properties.add(fieldNames.next());
        }
        return properties;
    }

    private Set<JsonNode> extractRequiredParams(JsonNode inputSchema) {
        if (inputSchema == null || !inputSchema.isObject()) {
            return Collections.emptySet();
        }
        JsonNode requiredNode = inputSchema.get(REQUIRED);
        if (requiredNode == null || !requiredNode.isArray() || requiredNode.isEmpty()) {
            return Collections.emptySet();
        }
        Set<JsonNode> required = new HashSet<>(requiredNode.size());
        for (JsonNode item : requiredNode) {
            required.add(item);
        }
        return required;
    }
}
