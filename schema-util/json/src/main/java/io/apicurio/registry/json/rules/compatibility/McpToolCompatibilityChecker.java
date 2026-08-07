package io.apicurio.registry.json.rules.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.json.rules.compatibility.jsonschema.JsonSchemaDiffLibrary;
import io.apicurio.registry.json.rules.compatibility.jsonschema.diff.Difference;
import io.apicurio.registry.rules.compatibility.AbstractCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityDifference;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Compatibility checker for MCP tool definition artifacts.
 *
 * Envelope fields (name, description, annotations) are ALWAYS compatible — no checks needed.
 * Delegated compatibility checking for inputSchema is handled by JsonSchemaDiffLibrary.
 */
public class McpToolCompatibilityChecker
        extends AbstractCompatibilityChecker<McpToolCompatibilityDifference> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> SUPPORTED_DRAFTS = Set.of(
            "http://json-schema.org/draft-04/schema#",
            "http://json-schema.org/draft-06/schema#",
            "http://json-schema.org/draft-07/schema#"
    );

    @Override
    protected Set<McpToolCompatibilityDifference> isBackwardsCompatibleWith(String existing,
            String proposed, Map<String, TypedContent> resolvedReferences) {
        Set<McpToolCompatibilityDifference> differences = new HashSet<>();

        try {
            JsonNode existingNode = MAPPER.readTree(existing);
            JsonNode proposedNode = MAPPER.readTree(proposed);

            JsonNode existingSchemaNode = existingNode.get("inputSchema");
            JsonNode proposedSchemaNode = proposedNode.get("inputSchema");

            // Boolean schemas (true/false) are treated as missing because the underlying everit library
            // does not support non-object schema values. In JSON Schema, true accepts everything and false
            // accepts nothing, but MCP tool inputSchema artifacts are always object schemas in practice.
            boolean existingMissing = existingSchemaNode == null || existingSchemaNode.isNull() || !existingSchemaNode.isObject();
            boolean proposedMissing = proposedSchemaNode == null || proposedSchemaNode.isNull() || !proposedSchemaNode.isObject();

            if (existingMissing && proposedMissing) {
                return differences;
            }

            if (existingMissing && !proposedMissing) {
                differences.add(new McpToolCompatibilityDifference(
                        McpToolCompatibilityDifference.Type.INPUT_SCHEMA_INCOMPATIBLE,
                        "inputSchema was added"));
                return differences;
            }

            if (!existingMissing && proposedMissing) {
                // Removing inputSchema entirely is widening (accepts any input) → compatible
                return differences;
            }

            if (validateSchemaDraft(existingSchemaNode, "existing", differences)) {
                return differences;
            }
            if (validateSchemaDraft(proposedSchemaNode, "proposed", differences)) {
                return differences;
            }

            String existingSchemaStr = MAPPER.writeValueAsString(existingSchemaNode);
            String proposedSchemaStr = MAPPER.writeValueAsString(proposedSchemaNode);

            Map<String, TypedContent> refs = resolvedReferences != null ? resolvedReferences : Collections.emptyMap();

            checkInputSchemaDifferences(existingSchemaStr, proposedSchemaStr, refs, differences);

        } catch (Exception e) {
            differences.add(new McpToolCompatibilityDifference(
                    McpToolCompatibilityDifference.Type.PARSE_ERROR,
                    "Failed to parse MCP tool definition: " + (e.getMessage() != null ? e.getMessage() : e.toString())));
        }

        return differences;
    }

    private boolean validateSchemaDraft(JsonNode schemaNode, String side,
            Set<McpToolCompatibilityDifference> differences) {
        if (schemaNode == null || !schemaNode.isObject() || !schemaNode.has("$schema")) {
            return false;
        }
        JsonNode schemaDraftNode = schemaNode.get("$schema");
        if (schemaDraftNode != null && schemaDraftNode.isTextual()) {
            String uri = schemaDraftNode.asText();
            if (!SUPPORTED_DRAFTS.contains(uri)) {
                differences.add(new McpToolCompatibilityDifference(
                        McpToolCompatibilityDifference.Type.PARSE_ERROR,
                        "Unsupported JSON Schema draft in " + side + " inputSchema: " + uri
                                + ". Only Draft 04/06/07 are supported."));
                return true;
            }
        }
        return false;
    }

    private void checkInputSchemaDifferences(String existingSchemaStr, String proposedSchemaStr,
            Map<String, TypedContent> refs, Set<McpToolCompatibilityDifference> differences) {
        try {
            JsonSchemaDiffLibrary.getIncompatibleDifferences(existingSchemaStr, "{}", Map.of());
        } catch (RuntimeException e) {
            differences.add(new McpToolCompatibilityDifference(
                    McpToolCompatibilityDifference.Type.PARSE_ERROR,
                    "Failed to parse existing inputSchema (" + e.getClass().getSimpleName() + "): "
                            + (e.getMessage() != null ? e.getMessage() : e.toString())));
            return;
        }

        try {
            JsonSchemaDiffLibrary.getIncompatibleDifferences(proposedSchemaStr, "{}", Map.of());
        } catch (RuntimeException e) {
            differences.add(new McpToolCompatibilityDifference(
                    McpToolCompatibilityDifference.Type.PARSE_ERROR,
                    "Failed to parse proposed inputSchema (" + e.getClass().getSimpleName() + "): "
                            + (e.getMessage() != null ? e.getMessage() : e.toString())));
            return;
        }

        try {
            Set<Difference> diffs = JsonSchemaDiffLibrary.getIncompatibleDifferences(
                    existingSchemaStr, proposedSchemaStr, refs);
            for (Difference diff : diffs) {
                differences.add(new McpToolCompatibilityDifference(
                        McpToolCompatibilityDifference.Type.INPUT_SCHEMA_INCOMPATIBLE,
                        diff.getDiffType().getDescription() + " at " + diff.getPathUpdated()));
            }
        } catch (RuntimeException e) {
            differences.add(new McpToolCompatibilityDifference(
                    McpToolCompatibilityDifference.Type.PARSE_ERROR,
                    "Unexpected schema comparison error (" + e.getClass().getSimpleName() + "): "
                            + (e.getMessage() != null ? e.getMessage() : e.toString())));
        }
    }

    @Override
    protected CompatibilityDifference transform(McpToolCompatibilityDifference original) {
        return original;
    }
}
