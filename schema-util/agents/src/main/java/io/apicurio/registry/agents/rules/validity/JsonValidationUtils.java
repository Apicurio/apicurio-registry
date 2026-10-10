package io.apicurio.registry.agents.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.SpecVersion;
import io.apicurio.registry.json.rules.validity.JsonSchemaDocumentValidator;
import io.apicurio.registry.rules.violation.RuleViolation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Shared JSON validation utility methods used by content validators for JSON-based artifact types
 * such as AGENT_CARD and MCP_TOOL.
 */
public final class JsonValidationUtils {

    /**
     * Dialect used when a schema does not declare {@code $schema}, which is the default the MCP
     * specification defines for tool schemas.
     */
    private static final SpecVersion.VersionFlag DEFAULT_SCHEMA_DIALECT = SpecVersion.VersionFlag.V202012;

    /**
     * Agent schemas are validated against their draft's meta-schema only, and a {@code $schema} that
     * names no known draft is reported rather than replaced.
     */
    private static final JsonSchemaDocumentValidator SCHEMA_VALIDATOR = JsonSchemaDocumentValidator.builder()
            .draftDetector(JsonSchemaDocumentValidator.networkntDraftDetector(DEFAULT_SCHEMA_DIALECT))
            .build();

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
     * Validates that a string value is a well-formed HTTP or HTTPS URL.
     *
     * <p>Per RFC 3986 section 3.1, URI schemes are case-insensitive, so the scheme is
     * compared with {@link String#equalsIgnoreCase} rather than regex. The port, when
     * present, is validated to be within the valid range (0–65535).
     */
    public static void validateHttpUrl(String value, String path, Set<RuleViolation> violations) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            // Per RFC 3986 §3.1 schemes are case-insensitive — use equalsIgnoreCase to avoid
            // a per-call regex compile and a toLowerCase allocation.
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                violations.add(new RuleViolation("URL must use http or https scheme", path));
            } else {
                // URI.getHost() returns a non-null value for IPv6 literals (e.g. http://[::1]:8080/)
                // on all standard JDKs, so getAuthority() fallback is not needed.
                String host = uri.getHost();
                if (host == null || host.isEmpty()) {
                    violations.add(new RuleViolation("URL must have a valid host", path));
                } else {
                    int port = uri.getPort();
                    // URI.getPort() returns -1 (no port specified) or a non-negative integer.
                    // After the != -1 guard, port < 0 can never be true; only check the upper bound.
                    if (port != -1 && port > 65535) {
                        violations.add(new RuleViolation("URL port must be in the range 0–65535", path));
                    }
                }
            }
        } catch (URISyntaxException e) {
            violations.add(new RuleViolation("Invalid URL format: " + e.getMessage(), path));
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

    /**
     * Validates that a node is itself a well-formed JSON Schema document, by validating it against
     * the JSON Schema meta-schema. Violations are reported under {@code basePath}, the JSON Pointer
     * of the field holding the schema.
     *
     * <p>The dialect is the one the schema declares in {@code $schema}, or
     * {@link #DEFAULT_SCHEMA_DIALECT} when it declares none. A {@code $schema} that is not a string,
     * or that names a dialect the validator library does not support, is reported as a violation
     * and the schema is not validated against any other dialect. A single malformed keyword can
     * fail several meta-schema branches at once, so at most one violation is reported per location.
     */
    public static void validateJsonSchema(JsonNode schemaNode, String basePath,
            Set<RuleViolation> violations) {
        JsonNode declaredDialect = schemaNode.get("$schema");
        if (declaredDialect != null && !declaredDialect.isTextual()) {
            violations.add(new RuleViolation("'$schema' field must be a string", basePath + "/$schema"));
            return;
        }

        for (RuleViolation violation : SCHEMA_VALIDATOR.validate(schemaNode)) {
            violations.add(new RuleViolation(violation.getDescription(), basePath + violation.getContext()));
        }
    }
}
