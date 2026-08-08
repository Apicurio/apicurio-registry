package io.apicurio.registry.services;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v3.beans.RenderPromptResponse;
import io.apicurio.registry.rest.v3.beans.RenderValidationError;
import io.apicurio.registry.storage.error.InvalidContentException;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.apicurio.registry.util.JsonObjectMapper.MAPPER;
import static io.apicurio.registry.util.YAMLObjectMapper.YAML_MAPPER;

/**
 * Service for rendering prompt templates by substituting variables.
 */
@ApplicationScoped
public class PromptRenderingService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /**
     * Renders a prompt template by substituting variables.
     *
     * @param content       The prompt template content (YAML or JSON)
     * @param variables     The variables to substitute
     * @param groupId       The group ID of the artifact
     * @param artifactId    The artifact ID
     * @param version       The version of the artifact
     * @return The rendered prompt response
     */
    public RenderPromptResponse render(ContentHandle content, Map<String, Object> variables,
                                        String groupId, String artifactId, String version) {
        // Parse the template content (could be YAML or JSON)
        JsonNode templateNode = parseContent(content);
        if (templateNode == null || !templateNode.isObject()) {
            throw new InvalidContentException("Prompt template content must be a JSON or YAML object");
        }

        // Extract template field and variables schema
        String templateText = extractTemplateText(templateNode);
        JsonNode variablesSchema = templateNode.path("variables");

        // Validate variables against schema. Validation intentionally runs against the
        // caller-supplied variables, so that a missing required variable is still reported
        // even when the schema declares a default for it.
        List<RenderValidationError> validationErrors = validateVariables(variables, variablesSchema);

        // Fill in declared defaults for any variable the caller did not provide
        Map<String, Object> effectiveVariables = applyDefaults(variables, variablesSchema);

        // Applied defaults are validated too, so a default that violates its own type, enum or
        // range is reported instead of being rendered silently.
        validateAppliedDefaults(variables, effectiveVariables, variablesSchema, validationErrors);

        // Render the template by substituting variables
        String rendered = substituteVariables(templateText, effectiveVariables);

        return RenderPromptResponse.builder()
                .rendered(rendered)
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .validationErrors(validationErrors)
                .build();
    }

    /**
     * Parse content as YAML or JSON.
     */
    private JsonNode parseContent(ContentHandle content) {
        String text = content.content();

        // Try YAML first (which also handles JSON)
        try {
            return YAML_MAPPER.readTree(text);
        } catch (Exception e) {
            // Fall back to JSON
            try {
                return MAPPER.readTree(text);
            } catch (Exception ex) {
                throw new InvalidContentException("Prompt template content is not valid YAML or JSON");
            }
        }
    }

    /**
     * Extract the template text from the parsed content.
     */
    private String extractTemplateText(JsonNode templateNode) {
        JsonNode templateField = templateNode.path("template");
        if (templateField.isMissingNode()) {
            throw new InvalidContentException("Prompt template is missing 'template' field");
        }
        if (templateField.isNull()) {
            throw new InvalidContentException("Prompt template 'template' field must not be null");
        }
        if (!templateField.isTextual()) {
            throw new InvalidContentException("Prompt template 'template' field must be a string");
        }
        String templateText = templateField.asText();
        if (templateText.isBlank()) {
            throw new InvalidContentException("Prompt template 'template' field must not be blank");
        }
        return templateText;
    }

    /**
     * Returns the variables to render with, filling in the <code>default</code> declared by the
     * schema for any variable the caller did not provide. Caller-supplied values always win, and
     * a variable that is explicitly present is never overwritten.
     * <p>
     * The caller's map is never modified; a copy is made only when there is a default to apply.
     */
    private Map<String, Object> applyDefaults(Map<String, Object> variables, JsonNode variablesSchema) {
        if (variablesSchema.isMissingNode() || !variablesSchema.isObject()) {
            return variables;
        }

        Map<String, Object> effective = null;

        Iterator<Map.Entry<String, JsonNode>> fields = variablesSchema.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String varName = field.getKey();

            if (variables.containsKey(varName)) {
                continue;
            }

            // A YAML "default:" with no value parses as a null node rather than a missing one.
            // Treat it as no default at all. An empty-string default is a real value and applies.
            JsonNode defaultNode = field.getValue().path("default");
            if (defaultNode.isMissingNode() || defaultNode.isNull()) {
                continue;
            }

            if (effective == null) {
                effective = new HashMap<>(variables);
            }
            effective.put(varName, MAPPER.convertValue(defaultNode, Object.class));
        }

        return effective != null ? effective : variables;
    }

    /**
     * Validate variables against the schema defined in the template.
     */
    private List<RenderValidationError> validateVariables(Map<String, Object> variables,
                                                           JsonNode variablesSchema) {
        List<RenderValidationError> errors = new ArrayList<>();

        if (variablesSchema.isMissingNode() || !variablesSchema.isObject()) {
            // No schema to validate against
            return errors;
        }

        // Check each defined variable in the schema
        Iterator<Map.Entry<String, JsonNode>> fields = variablesSchema.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String varName = field.getKey();
            JsonNode varSchema = field.getValue();

            // Check if required variable is present
            boolean required = varSchema.path("required").asBoolean(false);
            if (required && (!variables.containsKey(varName) || variables.get(varName) == null)) {
                errors.add(RenderValidationError.builder()
                        .variableName(varName)
                        .message("Required variable is missing")
                        .build());
                continue;
            }

            // Validate type if variable is present
            if (variables.containsKey(varName)) {
                validateValue(varName, variables.get(varName), varSchema, errors);
            }
        }

        return errors;
    }

    /**
     * Validate a single value against its variable schema: type, enum and numeric range.
     * <p>
     * Presence and <code>required</code> are handled by the caller, so this can be reused for
     * values the caller supplied and for defaults filled in from the schema.
     */
    private void validateValue(String varName, Object value, JsonNode varSchema,
            List<RenderValidationError> errors) {
        String expectedType = varSchema.path("type").asText("string");
        RenderValidationError typeError = validateType(varName, value, expectedType);
        if (typeError != null) {
            errors.add(typeError);
        }

        // Validate enum if specified
        JsonNode enumNode = varSchema.path("enum");
        if (!enumNode.isMissingNode() && enumNode.isArray()) {
            RenderValidationError enumError = validateEnum(varName, value, enumNode);
            if (enumError != null) {
                errors.add(enumError);
            }
        }

        // Validate range for numeric types
        if ("integer".equals(expectedType) || "number".equals(expectedType)) {
            RenderValidationError rangeError = validateRange(varName, value, varSchema);
            if (rangeError != null) {
                errors.add(rangeError);
            }
        }
    }

    /**
     * Validate the defaults that were filled in from the schema, so a declared default that
     * violates its own <code>type</code>, <code>enum</code> or range is reported rather than
     * silently rendered.
     * <p>
     * Only variables the caller omitted are checked here; <code>required</code> is deliberately
     * not re-checked, since a caller that omits a required variable is already reported by
     * {@link #validateVariables}.
     */
    private void validateAppliedDefaults(Map<String, Object> variables,
            Map<String, Object> effectiveVariables, JsonNode variablesSchema,
            List<RenderValidationError> errors) {
        if (effectiveVariables == variables) {
            // No defaults were applied
            return;
        }

        for (Map.Entry<String, Object> entry : effectiveVariables.entrySet()) {
            String varName = entry.getKey();
            if (variables.containsKey(varName)) {
                continue;
            }
            JsonNode varSchema = variablesSchema.path(varName);
            if (varSchema.isObject()) {
                validateValue(varName, entry.getValue(), varSchema, errors);
            }
        }
    }

    /**
     * Validate that a value matches the expected type.
     */
    private RenderValidationError validateType(String varName, Object value, String expectedType) {
        String actualType = getTypeName(value);

        boolean valid = switch (expectedType) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof List;
            case "object" -> value instanceof Map;
            default -> true; // Unknown types pass validation
        };

        if (!valid) {
            return RenderValidationError.builder()
                    .variableName(varName)
                    .message("Type mismatch: expected " + expectedType + " but got " + actualType)
                    .expectedType(expectedType)
                    .actualType(actualType)
                    .build();
        }

        return null;
    }

    /**
     * Validate that a value is within the allowed enum values.
     */
    private RenderValidationError validateEnum(String varName, Object value, JsonNode enumNode) {
        String strValue = String.valueOf(value);
        for (JsonNode enumValue : enumNode) {
            if (enumValue.asText().equals(strValue)) {
                return null; // Valid
            }
        }

        StringBuilder allowedValues = new StringBuilder();
        for (int i = 0; i < enumNode.size(); i++) {
            if (i > 0) allowedValues.append(", ");
            allowedValues.append(enumNode.get(i).asText());
        }

        return RenderValidationError.builder()
                .variableName(varName)
                .message("Value '" + strValue + "' is not in allowed values: " + allowedValues)
                .build();
    }

    /**
     * Validate numeric range constraints.
     */
    private RenderValidationError validateRange(String varName, Object value, JsonNode schema) {
        if (!(value instanceof Number)) {
            return null;
        }

        double numValue = ((Number) value).doubleValue();

        JsonNode minNode = schema.path("minimum");
        if (!minNode.isMissingNode()) {
            double min = minNode.asDouble();
            if (numValue < min) {
                return RenderValidationError.builder()
                        .variableName(varName)
                        .message("Value " + numValue + " is less than minimum " + min)
                        .build();
            }
        }

        JsonNode maxNode = schema.path("maximum");
        if (!maxNode.isMissingNode()) {
            double max = maxNode.asDouble();
            if (numValue > max) {
                return RenderValidationError.builder()
                        .variableName(varName)
                        .message("Value " + numValue + " is greater than maximum " + max)
                        .build();
            }
        }

        return null;
    }

    /**
     * Get the type name of a value.
     */
    private String getTypeName(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "string";
        if (value instanceof Integer || value instanceof Long) return "integer";
        if (value instanceof Number) return "number";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof List) return "array";
        if (value instanceof Map) return "object";
        return value.getClass().getSimpleName();
    }

    /**
     * Substitute variables in the template text using {{variable}} syntax.
     */
    private String substituteVariables(String template, Map<String, Object> variables) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            Object value = variables.get(varName);

            String replacement;
            if (value == null) {
                // Keep the original placeholder if variable is not provided
                replacement = matcher.group(0);
            } else {
                replacement = formatValue(value);
            }

            // Escape special characters for replacement
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Format a value for substitution into the template.
     */
    private String formatValue(Object value) {
        if (value instanceof List || value instanceof Map) {
            try {
                return MAPPER.writeValueAsString(value);
            } catch (Exception e) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }
}
