package io.apicurio.registry.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.rules.violation.RuleViolation;

import java.util.Set;

/**
 * Shared JSON validation utility methods used by content validators for JSON-based artifact types
 * such as AGENT_CARD and MCP_TOOL.
 */
public final class JsonValidationUtils {

    private JsonValidationUtils() {
        // Utility class
    }

    /**
     * Validates that an optional field, if present, is a string.
     */
    public static void validateOptionalString(JsonNode tree, String fieldName,
            Set<RuleViolation> violations) {
        if (tree.has(fieldName) && !tree.get(fieldName).isTextual()) {
            violations.add(
                    new RuleViolation("'" + fieldName + "' field must be a string", "/" + fieldName));
        }
    }

    /**
     * Validates that an optional field, if present, is an array of strings.
     */
    public static void validateStringArrayField(JsonNode tree, String fieldName,
            Set<RuleViolation> violations) {
        if (!tree.has(fieldName)) {
            return;
        }

        JsonNode array = tree.get(fieldName);
        if (!array.isArray()) {
            violations.add(
                    new RuleViolation("'" + fieldName + "' field must be an array", "/" + fieldName));
            return;
        }

        validateStringArray(array, "/" + fieldName, "item", violations);
    }

    /**
     * Validates that every element in a JSON array is a string.
     */
    public static void validateStringArray(JsonNode array, String basePath, String itemName,
            Set<RuleViolation> violations) {
        int index = 0;
        for (JsonNode item : array) {
            if (!item.isTextual()) {
                violations.add(new RuleViolation("Each " + itemName + " must be a string",
                        basePath + "/" + index));
            }
            index++;
        }
    }
}
