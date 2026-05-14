package io.apicurio.registry.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Content validator for Prompt Template artifacts.
 *
 * Validates the structural integrity of Prompt Template documents including required fields,
 * template variable cross-checking, variable schema validation, and reference consistency.
 */
public class PromptTemplateContentValidator extends AbstractContentValidator {

    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    private static final List<String> VALID_VARIABLE_TYPES = Arrays.asList(
            "string", "integer", "number", "boolean", "array", "object");

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
            if (tree.has("template") && tree.get("template").isTextual()) {
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
        if (!tree.has("templateId") || !tree.get("templateId").isTextual()
                || tree.get("templateId").asText().trim().isEmpty()) {
            violations.add(new RuleViolation(
                    "Missing or invalid required field 'templateId'. Must be a non-empty string.",
                    "/templateId"));
        }

        if (!tree.has("template") || !tree.get("template").isTextual()
                || tree.get("template").asText().trim().isEmpty()) {
            violations.add(new RuleViolation(
                    "Missing or invalid required field 'template'. Must be a non-empty string.",
                    "/template"));
        }
    }

    private void validateTemplateVariables(JsonNode tree, Set<RuleViolation> violations) {
        String template = tree.get("template").asText();
        List<String> templateVars = extractTemplateVariables(template);

        JsonNode variables = tree.get("variables");
        Set<String> definedVars = new HashSet<>();
        if (variables != null && variables.isObject()) {
            Iterator<String> fieldNames = variables.fieldNames();
            while (fieldNames.hasNext()) {
                definedVars.add(fieldNames.next());
            }
        }

        for (String variable : templateVars) {
            if (!definedVars.contains(variable)) {
                violations.add(new RuleViolation(
                        "Template variable '{{" + variable + "}}' is used but not defined in 'variables' schema.",
                        "/variables/" + variable));
            }
        }
    }

    private void validateVariableDefinitions(JsonNode tree, Set<RuleViolation> violations) {
        JsonNode variables = tree.get("variables");
        if (variables == null || !variables.isObject()) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = variables.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String varName = entry.getKey();
            JsonNode varSchema = entry.getValue();

            if (!varSchema.isObject()) {
                continue;
            }

            if (varSchema.has("type") && varSchema.get("type").isTextual()) {
                String type = varSchema.get("type").asText();
                if (!VALID_VARIABLE_TYPES.contains(type)) {
                    violations.add(new RuleViolation(
                            "Variable '" + varName + "' has invalid type '" + type
                                    + "'. Must be one of: " + String.join(", ", VALID_VARIABLE_TYPES) + ".",
                            "/variables/" + varName + "/type"));
                }
            }

            if (varSchema.has("minimum") && !varSchema.get("minimum").isNumber()) {
                violations.add(new RuleViolation(
                        "Variable '" + varName + "' has invalid 'minimum' value. Must be a number.",
                        "/variables/" + varName + "/minimum"));
            }
            if (varSchema.has("maximum") && !varSchema.get("maximum").isNumber()) {
                violations.add(new RuleViolation(
                        "Variable '" + varName + "' has invalid 'maximum' value. Must be a number.",
                        "/variables/" + varName + "/maximum"));
            }

            if (varSchema.has("enum") && !varSchema.get("enum").isArray()) {
                violations.add(new RuleViolation(
                        "Variable '" + varName + "' has invalid 'enum' value. Must be an array.",
                        "/variables/" + varName + "/enum"));
            }
        }
    }

    private void validateOptionalFields(JsonNode tree, Set<RuleViolation> violations) {
        if (tree.has("outputSchema") && !tree.get("outputSchema").isObject()) {
            violations.add(new RuleViolation(
                    "Field 'outputSchema' must be an object if provided.", "/outputSchema"));
        }

        if (tree.has("metadata") && !tree.get("metadata").isObject()) {
            violations.add(new RuleViolation(
                    "Field 'metadata' must be an object if provided.", "/metadata"));
        }
    }

    public static List<String> extractTemplateVariables(String template) {
        List<String> variables = new ArrayList<>();
        Matcher matcher = TEMPLATE_VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!variables.contains(varName)) {
                variables.add(varName);
            }
        }
        return variables;
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
            if (tree.has("variables")) {
                findRefs(tree.get("variables"), refs);
            }
            if (tree.has("outputSchema")) {
                findRefs(tree.get("outputSchema"), refs);
            }
            return refs;
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    private void findRefs(JsonNode node, Set<String> refs) {
        if (node.isObject()) {
            if (node.has("$ref")) {
                String ref = node.get("$ref").asText(null);
                if (ref != null && !ref.startsWith("#/")) {
                    refs.add(ref);
                }
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                findRefs(fields.next().getValue(), refs);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                findRefs(element, refs);
            }
        }
    }
}
