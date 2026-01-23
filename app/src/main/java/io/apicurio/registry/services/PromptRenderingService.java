package io.apicurio.registry.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v3.beans.RenderPromptResponse;
import io.apicurio.registry.rest.v3.beans.RenderValidationError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for rendering prompt templates by substituting variables.
 */
@ApplicationScoped
public class PromptRenderingService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @Inject
    Logger log;

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
        try {
            // Parse the template content (could be YAML or JSON)
            JsonNode templateNode = parseContent(content);

            // Extract template field and variables schema
            String templateText = extractTemplateText(templateNode);
            JsonNode variablesSchema = templateNode.path("variables");

            // Validate variables against schema
            List<RenderValidationError> validationErrors = validateVariables(variables, variablesSchema);

            // Render the template by substituting variables
            String rendered = substituteVariables(templateText, variables);

            return RenderPromptResponse.builder()
                    .rendered(rendered)
                    .groupId(groupId)
                    .artifactId(artifactId)
                    .version(version)
                    .validationErrors(validationErrors)
                    .build();

        } catch (Exception e) {
            log.error("Failed to render prompt template", e);
            throw new RuntimeException("Failed to render prompt template: " + e.getMessage(), e);
        }
    }

    /**
     * Parse content as YAML or JSON.
     */
    private JsonNode parseContent(ContentHandle content) throws Exception {
        String text = content.content();

        // Try YAML first (which also handles JSON)
        try {
            return YAML_MAPPER.readTree(text);
        } catch (Exception e) {
            // Fall back to JSON
            return JSON_MAPPER.readTree(text);
        }
    }

    /**
     * Extract the template text from the parsed content.
     */
    private String extractTemplateText(JsonNode templateNode) {
        JsonNode templateField = templateNode.path("template");
        if (templateField.isMissingNode()) {
            throw new IllegalArgumentException("Prompt template is missing 'template' field");
        }
        return templateField.asText();
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
            if (required && !variables.containsKey(varName)) {
                errors.add(RenderValidationError.builder()
                        .variableName(varName)
                        .message("Required variable is missing")
                        .build());
                continue;
            }

            // Validate type if variable is present
            if (variables.containsKey(varName)) {
                Object value = variables.get(varName);
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
        }

        return errors;
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
                return JSON_MAPPER.writeValueAsString(value);
            } catch (Exception e) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }
}
