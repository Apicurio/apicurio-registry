package io.apicurio.registry.agents.rules.validity;

import io.apicurio.registry.rules.validity.AbstractContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;

import tools.jackson.databind.JsonNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.agents.content.util.PromptTemplateVariableUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Content validator for Prompt Template artifacts.
 *
 * Validates the structural integrity of Prompt Template documents including required fields,
 * template variable cross-checking, variable schema validation, and reference consistency.
 */
public class PromptTemplateContentValidator extends AbstractContentValidator {

    private static final String VARIABLES = "variables";
    private static final String VARIABLES_PATH = "/variables/";
    private static final String VARIABLE_PREFIX = "Variable '";
    private static final String OUTPUT_SCHEMA = "outputSchema";
    private static final String TEMPLATE = "template";
    private static final String TEMPLATE_ID = "templateId";

    private static final List<String> VALID_VARIABLE_TYPES = Arrays.asList(
            "string", "integer", "number", "boolean", "array", "object");
            
    private static final String FIELD_MINIMUM = "minimum";
    private static final String FIELD_MAXIMUM = "maximum";

    @Override
    public void validate(ValidityLevel level, TypedContent content,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {

        if (level == ValidityLevel.NONE) {
            return;
        }

        Set<RuleViolation> violations = new HashSet<>();

        try {
            JsonNode tree = ContentTypeUtil.parseJsonOrYaml(content);

            if (!tree.isObject()) {
                throw new RuleViolationException("Prompt Template must be a JSON or YAML object",
                        RuleType.VALIDITY, level.name(),
                        Collections.singleton(
                                new RuleViolation("Prompt Template must be a JSON or YAML object", "")));
            }

            if (level == ValidityLevel.SYNTAX_ONLY) {
                return;
            }

            validateRequiredFields(tree, violations);
            if (tree.has(TEMPLATE) && tree.get(TEMPLATE).isString()) {
                validateTemplateVariables(tree, violations);
            }
            validateVariableDefinitions(tree, violations);
            validateOptionalFields(tree, violations);

            if (!violations.isEmpty()) {
                throw new RuleViolationException("Invalid Prompt Template", RuleType.VALIDITY,
                        level.name(), violations);
            }

        } catch (RuleViolationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleViolationException("Invalid Prompt Template content: " + e.getMessage(),
                    RuleType.VALIDITY, level.name(), e);
        }
    }

    private void validateRequiredFields(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has(TEMPLATE_ID) || !tree.get(TEMPLATE_ID).isString()
                || tree.get(TEMPLATE_ID).asString().trim().isEmpty()) {
            violations.add(new RuleViolation(
                    "Missing or invalid required field 'templateId'. Must be a non-empty string.",
                    "/templateId"));
        }

        if (!tree.has(TEMPLATE) || !tree.get(TEMPLATE).isString()
                || tree.get(TEMPLATE).asString().trim().isEmpty()) {
            violations.add(new RuleViolation(
                    "Missing or invalid required field 'template'. Must be a non-empty string.",
                    "/template"));
        }
    }

    private void validateTemplateVariables(JsonNode tree, Set<RuleViolation> violations) {
        String template = tree.get(TEMPLATE).asString();
        List<String> templateVars = extractTemplateVariables(template);

        JsonNode variables = tree.get(VARIABLES);
        Set<String> definedVars = new HashSet<>();
        if (variables != null && variables.isObject()) {
            Iterator<String> fieldNames = variables.propertyNames().iterator();
            while (fieldNames.hasNext()) {
                definedVars.add(fieldNames.next());
            }
        }

        for (String variable : templateVars) {
            if (!definedVars.contains(variable)) {
                violations.add(new RuleViolation(
                        "Template variable '{{" + variable + "}}' is used but not defined in 'variables' schema.",
                        VARIABLES_PATH + variable));
            }
        }
    }

    private void validateVariableDefinitions(JsonNode tree, Set<RuleViolation> violations) {
        JsonNode variables = tree.get(VARIABLES);
        if (variables == null || !variables.isObject()) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = variables.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode varSchema = entry.getValue();
            if (varSchema.isObject()) {
                validateVariableDefinition(entry.getKey(), varSchema, violations);
            }
        }
    }

    private void validateVariableDefinition(String varName, JsonNode varSchema,
            Set<RuleViolation> violations) {
        if (varSchema.has("type") && varSchema.get("type").isString()) {
            String type = varSchema.get("type").asString();
            if (!VALID_VARIABLE_TYPES.contains(type)) {
                violations.add(new RuleViolation(
                        VARIABLE_PREFIX + varName + "' has invalid type '" + type
                                + "'. Must be one of: " + String.join(", ", VALID_VARIABLE_TYPES) + ".",
                        VARIABLES_PATH + varName + "/type"));
            }
        }

        if (varSchema.has(FIELD_MINIMUM) && !varSchema.get(FIELD_MINIMUM).isNumber()) {
            violations.add(new RuleViolation(
                    VARIABLE_PREFIX + varName + "' has invalid '" + FIELD_MINIMUM + "' value. Must be a number.",
                    VARIABLES_PATH + varName + "/" + FIELD_MINIMUM));
        }
        if (varSchema.has(FIELD_MAXIMUM) && !varSchema.get(FIELD_MAXIMUM).isNumber()) {
            violations.add(new RuleViolation(
                    VARIABLE_PREFIX + varName + "' has invalid '" + FIELD_MAXIMUM + "' value. Must be a number.",
                    VARIABLES_PATH + varName + "/" + FIELD_MAXIMUM));
        }

        validateVariableRange(varName, varSchema, violations);

        if (varSchema.has("enum") && !varSchema.get("enum").isArray()) {
            violations.add(new RuleViolation(
                    VARIABLE_PREFIX + varName + "' has invalid 'enum' value. Must be an array.",
                    VARIABLES_PATH + varName + "/enum"));
        }
    }

    private void validateVariableRange(String varName, JsonNode varSchema, Set<RuleViolation> violations) {
        if (varSchema.has(FIELD_MINIMUM) && varSchema.get(FIELD_MINIMUM).isNumber()
                && varSchema.has(FIELD_MAXIMUM) && varSchema.get(FIELD_MAXIMUM).isNumber()) {
            double minimum = varSchema.get(FIELD_MINIMUM).asDouble();
            double maximum = varSchema.get(FIELD_MAXIMUM).asDouble();
            if (minimum > maximum) {
                violations.add(new RuleViolation(
                        VARIABLE_PREFIX + varName + "' has '" + FIELD_MINIMUM + "' ("
                                + varSchema.get(FIELD_MINIMUM).asString() + ") greater than '" + FIELD_MAXIMUM
                                + "' (" + varSchema.get(FIELD_MAXIMUM).asString()
                                + "). No value could ever satisfy this constraint.",
                        VARIABLES_PATH + varName + "/" + FIELD_MINIMUM));
            }
        }
    }

    private void validateOptionalFields(JsonNode tree, Set<RuleViolation> violations) {
        if (tree.has(OUTPUT_SCHEMA) && !tree.get(OUTPUT_SCHEMA).isObject()) {
            violations.add(new RuleViolation(
                    "Field 'outputSchema' must be an object if provided.", "/outputSchema"));
        }

        if (tree.has("metadata") && !tree.get("metadata").isObject()) {
            violations.add(new RuleViolation(
                    "Field 'metadata' must be an object if provided.", "/metadata"));
        }
    }

    public static List<String> extractTemplateVariables(String template) {
        return PromptTemplateVariableUtil.extractVariableNames(template);
    }

    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        Set<String> allRefs = getAllRefs(content);
        if (!allRefs.isEmpty()) {
            validateMappedReferences(references, allRefs, "Unmapped reference detected.");
        }
    }

    private Set<String> getAllRefs(TypedContent content) {
        try {
            JsonNode tree = ContentTypeUtil.parseJsonOrYaml(content);
            Set<String> refs = new HashSet<>();
            if (tree.has(VARIABLES)) {
                findExternalRefs(tree.get(VARIABLES), refs);
            }
            if (tree.has(OUTPUT_SCHEMA)) {
                findExternalRefs(tree.get(OUTPUT_SCHEMA), refs);
            }
            return refs;
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }
}
