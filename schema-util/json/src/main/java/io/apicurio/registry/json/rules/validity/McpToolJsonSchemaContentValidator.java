package io.apicurio.registry.json.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.json.rules.compatibility.jsonschema.JsonUtil;
import io.apicurio.registry.rules.validity.McpToolContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import org.everit.json.schema.SchemaException;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * MCP tool validator that reuses the common top-level MCP checks and adds deep
 * JSON Schema validation for embedded input/output schemas.
 */
public class McpToolJsonSchemaContentValidator extends McpToolContentValidator {

    @Override
    public void validate(ValidityLevel level, TypedContent content,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        super.validate(level, content, resolvedReferences);

        if (level != ValidityLevel.FULL) {
            return;
        }

        Set<RuleViolation> violations = new HashSet<>();

        try {
            JsonNode tree = ContentTypeUtil.parseJson(content.getContent());
            JsonNode inputSchema = tree.get("inputSchema");
            if (inputSchema != null) {
                validateEmbeddedSchema("inputSchema", inputSchema, resolvedReferences, violations);
            }

            JsonNode outputSchema = tree.get("outputSchema");
            if (outputSchema != null) {
                validateEmbeddedSchema("outputSchema", outputSchema, resolvedReferences,
                        violations);
            }
        } catch (Exception e) {
            throw new RuleViolationException(
                    "Invalid MCP tool definition JSON: " + e.getMessage(), RuleType.VALIDITY,
                    level.name(), e);
        }

        if (!violations.isEmpty()) {
            throw new RuleViolationException("Invalid MCP tool definition", RuleType.VALIDITY,
                    level.name(), violations);
        }
    }

    private void validateEmbeddedSchema(String fieldName, JsonNode schemaNode,
            Map<String, TypedContent> resolvedReferences, Set<RuleViolation> violations) {
        if (schemaNode == null || schemaNode.isNull()) {
            violations.add(new RuleViolation("'" + fieldName + "' field must be an object",
                    "/" + fieldName));
            return;
        }

        try {
            JsonUtil.readSchema(schemaNode.toString(), resolvedReferences);
        } catch (SchemaException e) {
            String description = e.getMessage();
            if (description != null && description.contains(":")) {
                description = description.substring(description.indexOf(":") + 1).trim();
            }
            violations.add(new RuleViolation(description,
                    prefixSchemaLocation(fieldName, e.getSchemaLocation())));
        } catch (Exception e) {
            violations.add(new RuleViolation(
                    fieldName + " JSON schema not valid: " + e.getMessage(),
                    "/" + fieldName));
        }
    }

    private String prefixSchemaLocation(String fieldName, String schemaLocation) {
        String basePath = "/" + fieldName;
        if (schemaLocation == null || schemaLocation.isEmpty() || "#".equals(schemaLocation)) {
            return basePath;
        }

        String normalizedLocation = schemaLocation;
        if (normalizedLocation.startsWith("#")) {
            normalizedLocation = normalizedLocation.substring(1);
        }
        if (normalizedLocation.isEmpty()) {
            return basePath;
        }
        if (!normalizedLocation.startsWith("/")) {
            normalizedLocation = "/" + normalizedLocation;
        }
        return basePath + normalizedLocation;
    }
}
