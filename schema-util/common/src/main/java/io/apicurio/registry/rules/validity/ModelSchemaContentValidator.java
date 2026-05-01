package io.apicurio.registry.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;

import io.apicurio.registry.rules.integrity.IntegrityLevel;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Content validator for AI/ML Model Schema artifacts.
 *
 * Validates the structural integrity of Model Schema documents including required fields,
 * field types, and reference consistency.
 */
public class ModelSchemaContentValidator implements ContentValidator {

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
        if (!tree.has("modelId") || !tree.get("modelId").isTextual()
                || tree.get("modelId").asText().trim().isEmpty()) {
            violations.add(new RuleViolation(
                    "Missing or invalid required field 'modelId'. Must be a non-empty string.",
                    "/modelId"));
        }

        if (!tree.has("input") && !tree.has("output")) {
            violations.add(new RuleViolation(
                    "At least one of 'input' or 'output' schema must be defined.", "/"));
        }
    }

    private void validateFieldTypes(JsonNode tree, Set<RuleViolation> violations) {
        if (tree.has("input") && !tree.get("input").isObject()) {
            violations.add(new RuleViolation(
                    "Field 'input' must be an object (JSON Schema).", "/input"));
        }

        if (tree.has("output") && !tree.get("output").isObject()) {
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
        Set<String> allRefs = getAllRefs(content);
        if (!allRefs.isEmpty()) {
            Set<String> mappedRefNames = references.stream()
                    .map(ArtifactReference::getName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Set<RuleViolation> violations = allRefs.stream()
                    .filter(ref -> !mappedRefNames.contains(ref))
                    .map(missingRef -> new RuleViolation("Unmapped reference detected.", missingRef))
                    .collect(Collectors.toSet());

            if (!violations.isEmpty()) {
                throw new RuleViolationException("Unmapped reference detected.",
                        RuleType.INTEGRITY, IntegrityLevel.ALL_REFS_MAPPED.name(), violations);
            }
        }
    }

    private Set<String> getAllRefs(TypedContent content) {
        try {
            JsonNode tree = ContentTypeUtil.parseJsonOrYaml(content);
            Set<String> refs = new HashSet<>();
            findRefs(tree, refs);
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
                Map.Entry<String, JsonNode> field = fields.next();
                findRefs(field.getValue(), refs);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                findRefs(element, refs);
            }
        }
    }
}
