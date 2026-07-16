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
            validateEmbeddedSchema("inputSchema", tree.get("inputSchema"), resolvedReferences,
                    violations);
            if (tree.has("outputSchema")) {
                validateEmbeddedSchema("outputSchema", tree.get("outputSchema"),
                        resolvedReferences, violations);
            }
        } catch (RuleViolationException e) {
            throw e;
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
        if (schemaLocation == null || schemaLocation.isEmpty() || "#".equals(schemaLocation)) {
            return "/" + fieldName;
        }
        if (schemaLocation.startsWith("#/")) {
            return "/" + fieldName + schemaLocation.substring(1);
        }
        if (schemaLocation.startsWith("/")) {
            return "/" + fieldName + schemaLocation;
        }
        return "/" + fieldName;
    }
}
