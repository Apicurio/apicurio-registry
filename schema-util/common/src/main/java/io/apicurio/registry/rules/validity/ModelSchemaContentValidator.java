package io.apicurio.registry.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Content validator for AI/ML Model Schema artifacts.
 *
 * Validates the structural integrity of Model Schema documents including required fields,
 * field types, and reference consistency.
 */
public class ModelSchemaContentValidator extends AbstractContentValidator {

    private static final String FIELD_MODEL_ID = "modelId";
    private static final String FIELD_INPUT = "input";
    private static final String FIELD_OUTPUT = "output";

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
                throw new RuleViolationException("Model Schema must be a JSON or YAML object",
                        RuleType.VALIDITY, level.name(),
                        Collections.singleton(new RuleViolation("Model Schema must be a JSON or YAML object", "")));
            }

            if (level == ValidityLevel.SYNTAX_ONLY) {
                return;
            }

            validateRequiredFields(tree, violations);
            validateFieldTypes(tree, violations);

            if (!violations.isEmpty()) {
                throw new RuleViolationException("Invalid Model Schema", RuleType.VALIDITY, level.name(),
                        violations);
            }

        } catch (RuleViolationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleViolationException("Invalid Model Schema content: " + e.getMessage(),
                    RuleType.VALIDITY, level.name(), e);
        }
    }

    private void validateRequiredFields(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has(FIELD_MODEL_ID) || !tree.get(FIELD_MODEL_ID).isTextual()
                || tree.get(FIELD_MODEL_ID).asText().trim().isEmpty()) {
            violations.add(new RuleViolation(
                    "Missing or invalid required field 'modelId'. Must be a non-empty string.",
                    "/modelId"));
        }

        if (!tree.has(FIELD_INPUT) && !tree.has(FIELD_OUTPUT)) {
            violations.add(new RuleViolation(
                    "At least one of 'input' or 'output' schema must be defined.", "/"));
        }
    }

    private void validateFieldTypes(JsonNode tree, Set<RuleViolation> violations) {
        if (tree.has(FIELD_INPUT) && !tree.get(FIELD_INPUT).isObject()) {
            violations.add(new RuleViolation(
                    "Field 'input' must be an object (JSON Schema).", "/input"));
        }

        if (tree.has(FIELD_OUTPUT) && !tree.get(FIELD_OUTPUT).isObject()) {
            violations.add(new RuleViolation(
                    "Field 'output' must be an object (JSON Schema).", "/output"));
        }

        if (tree.has("metadata") && !tree.get("metadata").isObject()) {
            violations.add(new RuleViolation(
                    "Field 'metadata' must be an object if provided.", "/metadata"));
        }
    }

    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        Set<String> allRefs = extractExternalJsonRefs(content);
        if (!allRefs.isEmpty()) {
            validateMappedReferences(references, allRefs, "Unmapped reference detected.");
        }
    }
}
