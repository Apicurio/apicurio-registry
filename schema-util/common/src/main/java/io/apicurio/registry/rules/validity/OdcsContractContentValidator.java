package io.apicurio.registry.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;

import io.apicurio.registry.rest.v3.beans.ArtifactReference;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OdcsContractContentValidator implements ContentValidator {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @Override
    public void validate(ValidityLevel level, TypedContent content,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {

        if (level == ValidityLevel.NONE) {
            return;
        }

        JsonNode tree;
        try {
            tree = YAML_MAPPER.readTree(content.getContent().content());
        } catch (Exception e) {
            throw new RuleViolationException("Invalid YAML syntax", RuleType.VALIDITY, level.name(),
                    Collections.singleton(
                            new RuleViolation("Content is not valid YAML: " + e.getMessage(), "")));
        }

        if (tree == null || !tree.isObject()) {
            throw new RuleViolationException("ODCS contract must be a YAML object",
                    RuleType.VALIDITY, level.name(),
                    Collections.singleton(
                            new RuleViolation("ODCS contract must be a YAML object", "")));
        }

        if (level == ValidityLevel.SYNTAX_ONLY) {
            return;
        }

        Set<RuleViolation> violations = new HashSet<>();

        if (!tree.has("kind") || !"DataContract".equals(tree.get("kind").asText())) {
            violations.add(
                    new RuleViolation("Missing or invalid 'kind' field (expected 'DataContract')",
                            "/kind"));
        }

        if (!tree.has("apiVersion")) {
            violations.add(new RuleViolation("Missing required field 'apiVersion'", "/apiVersion"));
        }

        if (!tree.has("info")) {
            violations.add(new RuleViolation("Missing required field 'info'", "/info"));
        } else {
            JsonNode info = tree.get("info");
            if (!info.has("title")) {
                violations.add(
                        new RuleViolation("Missing required field 'info.title'", "/info/title"));
            }
            if (!info.has("version")) {
                violations.add(new RuleViolation("Missing required field 'info.version'",
                        "/info/version"));
            }
        }

        if (!violations.isEmpty()) {
            throw new RuleViolationException("ODCS contract validation failed",
                    RuleType.VALIDITY, level.name(), violations);
        }
    }

    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        // ODCS contracts reference schemas by location string, not by JSON $ref.
        // Reference validation is handled by the projection engine, not the content validator.
    }
}
