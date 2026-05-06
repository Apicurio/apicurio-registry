package io.apicurio.registry.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Content validator for MCP (Model Context Protocol) tool definition artifacts.
 *
 * Validates that the content is a valid MCP tool definition JSON document per the MCP specification 2025-11-25.
 *
 * Validation levels:
 * - NONE: No validation
 * - SYNTAX_ONLY: Validates that the content is valid JSON and is an object
 * - FULL: Full schema validation including required fields, type checking, and structure validation
 *
 * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools</a>
 */
public class McpToolContentValidator implements ContentValidator {

    @Override
    public void validate(ValidityLevel level, TypedContent content,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {

        if (level == ValidityLevel.NONE) {
            return;
        }

        Set<RuleViolation> violations = new HashSet<>();

        try {
            JsonNode tree = ContentTypeUtil.parseJson(content.getContent());

            if (!tree.isObject()) {
                throw new RuleViolationException("MCP tool definition must be a JSON object",
                        RuleType.VALIDITY, level.name(),
                        Collections.singleton(
                                new RuleViolation("MCP tool definition must be a JSON object", "")));
            }

            // SYNTAX_ONLY level: just check it's valid JSON object (already done)
            if (level == ValidityLevel.SYNTAX_ONLY) {
                return;
            }

            // FULL level: comprehensive validation
            validateNameField(tree, violations);
            validateOptionalStringFields(tree, violations);
            validateInputSchemaField(tree, violations);
            validateOutputSchemaField(tree, violations);
            validateAnnotationsField(tree, violations);

            if (!violations.isEmpty()) {
                throw new RuleViolationException("Invalid MCP tool definition", RuleType.VALIDITY,
                        level.name(), violations);
            }

        } catch (RuleViolationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleViolationException(
                    "Invalid MCP tool definition JSON: " + e.getMessage(), RuleType.VALIDITY,
                    level.name(), e);
        }
    }

    private void validateNameField(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has("name")) {
            violations.add(
                    new RuleViolation("MCP tool definition must have a 'name' field", "/name"));
        } else if (!tree.get("name").isTextual()) {
            violations.add(new RuleViolation("'name' field must be a string", "/name"));
        } else if (tree.get("name").asText().trim().isEmpty()) {
            violations.add(new RuleViolation("'name' field must not be empty", "/name"));
        }
    }

    private void validateOptionalStringFields(JsonNode tree, Set<RuleViolation> violations) {
        JsonValidationUtils.validateOptionalString(tree, "title", violations);
        JsonValidationUtils.validateOptionalString(tree, "description", violations);
    }

    private void validateInputSchemaField(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has("inputSchema")) {
            violations.add(new RuleViolation("MCP tool definition must have an 'inputSchema' field",
                    "/inputSchema"));
            return;
        }

        JsonNode inputSchema = tree.get("inputSchema");
        if (!inputSchema.isObject()) {
            violations.add(
                    new RuleViolation("'inputSchema' field must be an object", "/inputSchema"));
            return;
        }

        // inputSchema must have a "type" field with value "object"
        if (!inputSchema.has("type")) {
            violations.add(new RuleViolation("'inputSchema' must have a 'type' field",
                    "/inputSchema/type"));
        } else if (!inputSchema.get("type").isTextual()) {
            violations.add(new RuleViolation("'inputSchema.type' must be a string",
                    "/inputSchema/type"));
        } else if (!"object".equals(inputSchema.get("type").asText())) {
            violations.add(new RuleViolation(
                    "'inputSchema.type' must be 'object' per the MCP specification",
                    "/inputSchema/type"));
        }

        // If "properties" is present, it must be an object
        if (inputSchema.has("properties") && !inputSchema.get("properties").isObject()) {
            violations.add(new RuleViolation("'inputSchema.properties' must be an object",
                    "/inputSchema/properties"));
        }

        // If "required" is present, it must be an array of strings
        if (inputSchema.has("required")) {
            if (!inputSchema.get("required").isArray()) {
                violations.add(new RuleViolation("'inputSchema.required' must be an array",
                        "/inputSchema/required"));
            } else {
                JsonValidationUtils.validateStringArray(inputSchema.get("required"),
                        "/inputSchema/required", "required parameter name", violations);
            }
        }
    }

    private void validateOutputSchemaField(JsonNode tree, Set<RuleViolation> violations) {
        if (tree.has("outputSchema") && !tree.get("outputSchema").isObject()) {
            violations.add(new RuleViolation("'outputSchema' field must be an object",
                    "/outputSchema"));
        }
    }

    private void validateAnnotationsField(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has("annotations")) {
            return;
        }

        JsonNode annotations = tree.get("annotations");
        if (!annotations.isObject()) {
            violations.add(new RuleViolation("'annotations' field must be an object",
                    "/annotations"));
            return;
        }

        // title: optional string (fallback display name per MCP spec)
        JsonValidationUtils.validateOptionalString(annotations, "title", violations);

        // audience: optional array of strings ("user", "assistant")
        if (annotations.has("audience")) {
            JsonNode audience = annotations.get("audience");
            if (!audience.isArray()) {
                violations.add(new RuleViolation("'annotations.audience' must be an array",
                        "/annotations/audience"));
            } else {
                JsonValidationUtils.validateStringArray(audience,
                        "/annotations/audience", "audience role", violations);
            }
        }

        // priority: optional number between 0 and 1
        if (annotations.has("priority")) {
            JsonNode priority = annotations.get("priority");
            if (!priority.isNumber()) {
                violations.add(new RuleViolation("'annotations.priority' must be a number",
                        "/annotations/priority"));
            } else {
                double value = priority.asDouble();
                if (value < 0 || value > 1) {
                    violations.add(new RuleViolation(
                            "'annotations.priority' must be between 0 and 1",
                            "/annotations/priority"));
                }
            }
        }
    }

    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        // MCP tool definitions don't support references
        if (references != null && !references.isEmpty()) {
            throw new RuleViolationException("MCP tool definitions do not support references",
                    RuleType.INTEGRITY, "NONE", Collections.singleton(new RuleViolation(
                            "References are not supported for MCP tool definitions", "")));
        }
    }
}
