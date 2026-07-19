package io.apicurio.registry.rules.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

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
 * - Narrowing an existing parameter's enum relative to the prior version: Forward incompatible
 * - Changing name, title, description, annotations: Always compatible
 */
public class McpToolCompatibilityChecker
        extends AbstractCompatibilityChecker<McpToolCompatibilityDifference> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel,
            List<TypedContent> existingArtifacts, TypedContent proposedArtifact,
            Map<String, TypedContent> resolvedReferences) {
        requireNonNull(compatibilityLevel, "compatibilityLevel MUST NOT be null");
        requireNonNull(existingArtifacts, "existingSchemas MUST NOT be null");
        requireNonNull(proposedArtifact, "proposedSchema MUST NOT be null");

        if (existingArtifacts.isEmpty()) {
            return CompatibilityExecutionResult.compatible();
        }

        final String proposedArtifactContent = proposedArtifact.getContent().content();
        String lastExistingSchema = existingArtifacts.get(existingArtifacts.size() - 1).getContent()
                .content();

        Set<McpToolCompatibilityDifference> incompatibleDiffs = new HashSet<>();

        switch (compatibilityLevel) {
            case BACKWARD:
                incompatibleDiffs = doCompatibilityCheck(lastExistingSchema, proposedArtifactContent,
                        resolvedReferences, false);
                break;
            case BACKWARD_TRANSITIVE:
                incompatibleDiffs = transitivelyCheck(existingArtifacts, proposedArtifactContent,
                        resolvedReferences, false);
                break;
            case FORWARD:
                incompatibleDiffs = doCompatibilityCheck(proposedArtifactContent, lastExistingSchema,
                        resolvedReferences, true);
                break;
            case FORWARD_TRANSITIVE:
                incompatibleDiffs = transitivelyCheck(existingArtifacts, proposedArtifactContent,
                        resolvedReferences, true);
                break;
            case FULL:
                incompatibleDiffs = unionOf(
                        doCompatibilityCheck(lastExistingSchema, proposedArtifactContent,
                                resolvedReferences, false),
                        doCompatibilityCheck(proposedArtifactContent, lastExistingSchema,
                                resolvedReferences, true));
                break;
            case FULL_TRANSITIVE:
                incompatibleDiffs = unionOf(
                        transitivelyCheck(existingArtifacts, proposedArtifactContent,
                                resolvedReferences, false),
                        transitivelyCheck(existingArtifacts, proposedArtifactContent,
                                resolvedReferences, true));
                break;
            case NONE:
                break;
        }

        Set<CompatibilityDifference> diffs = incompatibleDiffs.stream().map(this::transform)
                .collect(Collectors.toSet());
        return CompatibilityExecutionResult.incompatibleOrEmpty(diffs);
    }

    private Set<McpToolCompatibilityDifference> transitivelyCheck(List<TypedContent> existingArtifacts,
            String proposedArtifactContent, Map<String, TypedContent> resolvedReferences,
            boolean forwardEnumCheck) {
        Set<McpToolCompatibilityDifference> result = new HashSet<>();
        for (int i = existingArtifacts.size() - 1; i >= 0; i--) {
            String existing = existingArtifacts.get(i).getContent().content();
            if (forwardEnumCheck) {
                result.addAll(doCompatibilityCheck(proposedArtifactContent, existing,
                        resolvedReferences, true));
            } else {
                result.addAll(doCompatibilityCheck(existing, proposedArtifactContent,
                        resolvedReferences, false));
            }
        }
        return result;
    }

    @SafeVarargs
    private Set<McpToolCompatibilityDifference> unionOf(Set<McpToolCompatibilityDifference>... from) {
        Set<McpToolCompatibilityDifference> result = new HashSet<>();
        for (Set<McpToolCompatibilityDifference> set : from) {
            result.addAll(set);
        }
        return result;
    }

    @Override
    protected Set<McpToolCompatibilityDifference> isBackwardsCompatibleWith(String existing,
            String proposed, Map<String, TypedContent> resolvedReferences) {
        return doCompatibilityCheck(existing, proposed, resolvedReferences, false);
    }

    private Set<McpToolCompatibilityDifference> doCompatibilityCheck(String existing, String proposed,
            Map<String, TypedContent> resolvedReferences, boolean forwardEnumCheck) {
        Set<McpToolCompatibilityDifference> differences = new HashSet<>();

        try {
            JsonNode existingNode = mapper.readTree(existing);
            JsonNode proposedNode = mapper.readTree(proposed);

            // Check inputSchema type changes
            checkInputSchemaTypeChange(existingNode, proposedNode, differences);

            // Check removed properties
            checkPropertyRemovals(existingNode, proposedNode, differences);

            // Check per-property schema changes for properties present in both versions
            checkPropertySchemaChanges(existingNode, proposedNode, differences, forwardEnumCheck);

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
            Set<McpToolCompatibilityDifference> differences, boolean forwardEnumCheck) {
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
            JsonNode fromProp = forwardEnumCheck ? proposedProp : existingProp;
            JsonNode toProp = forwardEnumCheck ? existingProp : proposedProp;
            checkPropertyTypeNarrowing(propName, fromProp, toProp, differences);
            checkEnumNarrowing(propName, fromProp, toProp, differences);
        }
    }

    private void checkPropertyTypeNarrowing(String propName, JsonNode fromProp, JsonNode toProp,
            Set<McpToolCompatibilityDifference> differences) {
        Set<String> fromTypes = getTypeValues(fromProp);
        Set<String> toTypes = getTypeValues(toProp);
        if (fromTypes == null || toTypes == null) {
            return;
        }

        for (String type : fromTypes) {
            if (!toTypes.contains(type)) {
                differences.add(new McpToolCompatibilityDifference(
                        McpToolCompatibilityDifference.Type.PROPERTY_TYPE_CHANGED,
                        "Input property '" + propName + "' no longer allows type '" + type + "'"));
                return;
            }
        }
    }

    private void checkEnumNarrowing(String propName, JsonNode fromProp, JsonNode toProp,
            Set<McpToolCompatibilityDifference> differences) {
        JsonNode fromEnum = fromProp.get("enum");
        JsonNode toEnum = toProp.get("enum");
        if (fromEnum == null || !fromEnum.isArray() || toEnum == null || !toEnum.isArray()) {
            return;
        }

        Set<String> toValues = new HashSet<>();
        for (JsonNode val : toEnum) {
            toValues.add(val.asText());
        }

        for (JsonNode val : fromEnum) {
            if (!toValues.contains(val.asText())) {
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

    private Set<String> getTypeValues(JsonNode node) {
        JsonNode typeNode = node.get("type");
        if (typeNode == null || typeNode.isNull()) {
            return null;
        }
        if (typeNode.isTextual()) {
            return Set.of(typeNode.asText());
        }
        if (typeNode.isArray()) {
            Set<String> types = new HashSet<>();
            for (JsonNode item : typeNode) {
                if (item.isTextual()) {
                    types.add(item.asText());
                }
            }
            return types.isEmpty() ? null : types;
        }
        return null;
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
