package io.apicurio.registry.services;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.util.PromptTemplateFormats;
import io.apicurio.registry.content.util.PromptTemplateVariableUtil;
import io.apicurio.registry.rest.v3.beans.RenderPromptResponse;
import io.apicurio.registry.rest.v3.beans.RenderValidationError;
import io.apicurio.registry.storage.error.InvalidContentException;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.apicurio.registry.util.JsonObjectMapper.MAPPER;
import static io.apicurio.registry.util.YAMLObjectMapper.YAML_MAPPER;

/**
 * Service for rendering prompt templates by substituting variables and evaluating
 * Handlebars-style conditional blocks.
 *
 * <p>Supported syntax:</p>
 * <ul>
 *   <li>{@code {{variable}}} — substituted with the variable value, or kept verbatim when absent</li>
 *   <li>{@code {{#if variable}} ... {{/if}}} — block rendered when variable is truthy</li>
 *   <li>{@code {{#if variable}} ... {{else}} ... {{/if}}} — if/else branch</li>
 *   <li>{@code {{#unless variable}} ... {{/unless}}} — block rendered when variable is falsy</li>
 * </ul>
 *
 * <p>Truthy semantics (matching the MCP render path): a value is truthy when it is
 * non-null, not {@code Boolean.FALSE}, and not an empty string.</p>
 */
@ApplicationScoped
public class PromptRenderingService {

    private static final Logger log = LoggerFactory.getLogger(PromptRenderingService.class);

    /**
     * Upper bound on {@link #warnedUnsupportedFormats}, so that a registry holding many templates
     * with an unsupported format cannot grow the set without limit.
     */
    private static final int MAX_WARNED_UNSUPPORTED_FORMATS = 1000;

    /**
     * Artifact versions already warned about, as {@code groupId/artifactId/version}. Keeps the
     * warning to once per version instead of once per render call.
     */
    private final Set<String> warnedUnsupportedFormats = ConcurrentHashMap.newKeySet();

    /**
     * Matches {{#if var}} ... {{/if}} blocks, including multi-line content.
     * Group 1 = variable name, Group 2 = inner content.
     */
    private static final Pattern IF_BLOCK_PATTERN =
            Pattern.compile("\\{\\{#if\\s+(\\w+)\\}\\}((?:(?!\\{\\{/if\\}\\})[\\s\\S])*?)\\{\\{/if\\}\\}");

    /**
     * Matches if-else blocks: opening tag, truthy branch, else tag, falsy branch, closing tag.
     * Group 1 = variable name, Group 2 = truthy branch, Group 3 = falsy branch.
     */
    private static final Pattern IF_ELSE_BLOCK_PATTERN =
            Pattern.compile("\\{\\{#if\\s+(\\w+)\\}\\}((?:(?!\\{\\{else\\}\\})[\\s\\S])*?)\\{\\{else\\}\\}((?:(?!\\{\\{/if\\}\\})[\\s\\S])*?)\\{\\{/if\\}\\}");

    /**
     * Matches unless blocks: opening tag, inner content, closing tag.
     * Group 1 = variable name, Group 2 = inner content.
     */
    private static final Pattern UNLESS_BLOCK_PATTERN =
            Pattern.compile("\\{\\{#unless\\s+(\\w+)\\}\\}((?:(?!\\{\\{/unless\\}\\})[\\s\\S])*?)\\{\\{/unless\\}\\}");

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

        warnOnUnsupportedTemplateFormat(templateNode, groupId, artifactId, version);

        // Validate variables against schema. Validation intentionally runs against the
        // caller-supplied variables, so that a missing required variable is still reported
        // even when the schema declares a default for it.
        List<RenderValidationError> validationErrors = validateVariables(variables, variablesSchema);

        // Fill in declared defaults for any variable the caller did not provide
        Map<String, Object> effectiveVariables = applyDefaults(variables, variablesSchema);

        // Applied defaults are validated too, so a default that violates its own type, enum or
        // range is reported instead of being rendered silently.
        validateAppliedDefaults(variables, effectiveVariables, variablesSchema, validationErrors);

        // Process conditional blocks first, then substitute plain variables
        String withBlocksResolved = processConditionalBlocks(templateText, effectiveVariables);
        String rendered = substituteVariables(withBlocksResolved, effectiveVariables);

        return RenderPromptResponse.builder()
                .rendered(rendered)
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .validationErrors(validationErrors)
                .build();
    }

    /**
     * Logs when a template declares a format this service cannot render.
     * <p>
     * The VALIDITY rule rejects such templates at write time, but that rule is optional and can be
     * disabled, and artifacts stored before it was enabled are still rendered. Rendering continues
     * with this service's own syntax so those artifacts keep working, and the log records that the
     * output is unlikely to be what the template intended.
     * <p>
     * The condition does not change between renders, so a template polled on every request would
     * otherwise produce one WARN per request. The first render of a given version warns and the
     * rest drop to DEBUG. The set of already-warned versions is capped so that a registry holding
     * many such templates cannot grow it without bound; past the cap everything logs at DEBUG.
     */
    private void warnOnUnsupportedTemplateFormat(JsonNode templateNode, String groupId,
            String artifactId, String version) {
        JsonNode templateFormat = templateNode.path("templateFormat");
        if (!templateFormat.isTextual()) {
            return;
        }

        String format = templateFormat.asText();
        if (PromptTemplateFormats.isSupported(format)) {
            return;
        }

        String message = "Prompt template {}/{}/{} declares templateFormat '{}', which is not "
                + "supported. Rendering with the built-in placeholder syntax instead. "
                + "Supported formats: {}.";

        if (warnedUnsupportedFormats.size() < MAX_WARNED_UNSUPPORTED_FORMATS
                && warnedUnsupportedFormats.add(groupId + "/" + artifactId + "/" + version)) {
            log.warn(message, groupId, artifactId, version, format,
                    PromptTemplateFormats.supported());
        } else {
            log.debug(message, groupId, artifactId, version, format,
                    PromptTemplateFormats.supported());
        }
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
     * Determine whether a value is truthy for conditional evaluation.
     *
     * <p>A value is falsy when it is null, {@code Boolean.FALSE}, or an empty string.
     * All other values are truthy. This matches the MCP render path semantics in
     * {@code PromptTemplateConverter.processConditionalBlocks()}.</p>
     */
    private boolean isTruthy(Object value) {
        return value != null
                && !Boolean.FALSE.equals(value)
                && !(value instanceof String s && s.isEmpty());
    }

    /**
     * Process Handlebars-style conditional blocks before plain variable substitution.
     *
     * <p>Handles three forms in evaluation order:</p>
     * <ol>
     *   <li>{@code {{#if var}} ... {{else}} ... {{/if}}} — if/else (must be matched before plain if)</li>
     *   <li>{@code {{#if var}} ... {{/if}}} — simple if</li>
     *   <li>{@code {{#unless var}} ... {{/unless}}} — unless (logical complement of if)</li>
     * </ol>
     */
    private String processConditionalBlocks(String template, Map<String, Object> variables) {
        // Step 1: resolve if-else blocks first to prevent the plain-if pattern from partially matching them
        Matcher ifElseMatcher = IF_ELSE_BLOCK_PATTERN.matcher(template);
        StringBuffer ifElseResult = new StringBuffer();
        while (ifElseMatcher.find()) {
            String varName = ifElseMatcher.group(1);
            String truthyBranch = ifElseMatcher.group(2);
            String falsyBranch = ifElseMatcher.group(3);
            String chosen = isTruthy(variables.get(varName)) ? truthyBranch : falsyBranch;
            ifElseMatcher.appendReplacement(ifElseResult, Matcher.quoteReplacement(chosen));
        }
        ifElseMatcher.appendTail(ifElseResult);
        template = ifElseResult.toString();

        // Step 2: resolve plain if blocks
        Matcher ifMatcher = IF_BLOCK_PATTERN.matcher(template);
        StringBuffer ifResult = new StringBuffer();
        while (ifMatcher.find()) {
            String varName = ifMatcher.group(1);
            String content = ifMatcher.group(2);
            String chosen = isTruthy(variables.get(varName)) ? content : "";
            ifMatcher.appendReplacement(ifResult, Matcher.quoteReplacement(chosen));
        }
        ifMatcher.appendTail(ifResult);
        template = ifResult.toString();

        // Step 3: resolve unless blocks (logical complement of if)
        Matcher unlessMatcher = UNLESS_BLOCK_PATTERN.matcher(template);
        StringBuffer unlessResult = new StringBuffer();
        while (unlessMatcher.find()) {
            String varName = unlessMatcher.group(1);
            String content = unlessMatcher.group(2);
            String chosen = isTruthy(variables.get(varName)) ? "" : content;
            unlessMatcher.appendReplacement(unlessResult, Matcher.quoteReplacement(chosen));
        }
        unlessMatcher.appendTail(unlessResult);

        return unlessResult.toString();
    }

    /**
     * Substitute variables in the template text using {{variable}} syntax.
     */
    private String substituteVariables(String template, Map<String, Object> variables) {
        return PromptTemplateVariableUtil.substituteVariables(template, varName -> {
            Object value = variables.get(varName);
            // Returning null keeps the original placeholder if the variable is not provided.
            return value == null ? null : formatValue(value);
        });
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
