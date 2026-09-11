package io.apicurio.registry.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.apicurio.registry.rules.violation.RuleViolation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared JSON validation utility methods used by content validators for JSON-based artifact types
 * such as AGENT_CARD and MCP_TOOL.
 */
public final class JsonValidationUtils {

    /**
     * Dialect used when a schema does not declare {@code $schema}. The MCP specification is written
     * against JSON Schema without pinning a draft, so the most recent one is assumed.
     */
    private static final SpecVersion.VersionFlag DEFAULT_SCHEMA_DIALECT = SpecVersion.VersionFlag.V202012;

    /**
     * Reports validation failures with JSON Pointer locations, so they can be appended to the
     * field path a {@link RuleViolation} already uses as its context.
     */
    private static final SchemaValidatorsConfig META_VALIDATION_CONFIG = SchemaValidatorsConfig.builder()
            .pathType(PathType.JSON_POINTER).build();

    /**
     * Meta-schemas are immutable once built, so they are cached per dialect rather than rebuilt per
     * call. Each is loaded from a document bundled in the validator library, never over the network.
     */
    private static final Map<SpecVersion.VersionFlag, JsonSchema> META_SCHEMAS = new ConcurrentHashMap<>();

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
     * <p>The dialect is taken from the schema's own {@code $schema} when it declares a recognised
     * one, and is {@link #DEFAULT_SCHEMA_DIALECT} otherwise. A single malformed keyword can fail
     * several meta-schema branches at once, so at most one violation is reported per location.
     */
    public static void validateJsonSchema(JsonNode schemaNode, String basePath,
            Set<RuleViolation> violations) {
        Set<String> reportedLocations = new HashSet<>();
        for (ValidationMessage message : metaSchemaFor(schemaNode).validate(schemaNode)) {
            String location = message.getInstanceLocation().toString();
            if (reportedLocations.add(location)) {
                violations.add(new RuleViolation(messageWithoutLocation(message, location),
                        basePath + location));
            }
        }
    }

    private static JsonSchema metaSchemaFor(JsonNode schemaNode) {
        JsonNode declaredDialect = schemaNode.get("$schema");
        SpecVersion.VersionFlag dialect = declaredDialect != null && declaredDialect.isTextual()
                ? SpecVersion.VersionFlag.fromId(declaredDialect.asText()).orElse(DEFAULT_SCHEMA_DIALECT)
                : DEFAULT_SCHEMA_DIALECT;
        return META_SCHEMAS.computeIfAbsent(dialect, version -> JsonSchemaFactory.getInstance(version)
                .getSchema(SchemaLocation.of(version.getId()), META_VALIDATION_CONFIG));
    }

    /**
     * The library prefixes every message with the instance location, which the violation already
     * carries as its context.
     */
    private static String messageWithoutLocation(ValidationMessage message, String location) {
        String text = message.getMessage();
        String prefix = location + ": ";
        return text.startsWith(prefix) ? text.substring(prefix.length()) : text;
    }
}
