package io.apicurio.registry.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.rules.compatibility.jsonschema.JsonUtil;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.types.RuleType;
import org.everit.json.schema.SchemaException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A content validator implementation for the JsonSchema content type.
 */
public class JsonSchemaContentValidator implements ContentValidator {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructor.
     */
    public JsonSchemaContentValidator() {
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, TypedContent, Map)
     */
    @Override
    public void validate(ValidityLevel level, TypedContent content,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY) {
            try {
                objectMapper.readTree(content.getContent().bytes());
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for JSON Schema artifact.",
                        RuleType.VALIDITY, level.name(), e);
            }
        } else if (level == ValidityLevel.FULL) {
            try {
                JsonUtil.readSchema(content.getContent().content(), resolvedReferences);
            } catch (SchemaException e) {
                String context = e.getSchemaLocation();
                String description = e.getMessage();
                if (description != null && description.contains(":")) {
                    description = description.substring(description.indexOf(":") + 1).trim();
                }
                RuleViolation violation = new RuleViolation(description, context);
                throw new RuleViolationException("Syntax or semantic violation for JSON Schema artifact.",
                        RuleType.VALIDITY, level.name(), Collections.singleton(violation));
            } catch (Exception e) {
                RuleViolation violation = new RuleViolation("JSON schema not valid: " + e.getMessage(), "");
                throw new RuleViolationException("Syntax or semantic violation for JSON Schema artifact.",
                        RuleType.VALIDITY, level.name(), Collections.singleton(violation));
            }
        }
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validateReferences(TypedContent, List)
     */
    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        Set<String> mappedRefs = references.stream()
                .map(ArtifactReference::getName)
                .collect(Collectors.toSet());

        Set<String> all$refs = getAll$refs(content);
        Set<RuleViolation> violations = all$refs.stream()
                .filter(ref -> !mappedRefs.contains(ref))
                .map(missingRef -> new RuleViolation("Unmapped reference detected.", missingRef))
                .collect(Collectors.toSet());

        if (!violations.isEmpty()) {
            throw new RuleViolationException("Unmapped reference(s) detected.",
                    RuleType.INTEGRITY, IntegrityLevel.ALL_REFS_MAPPED.name(), violations);
        }
    }

    /**
     * Extracts all external $ref values from a JSON Schema document.
     * Internal references (starting with #/) are excluded.
     *
     * @param content the JSON Schema content to analyze
     * @return set of external reference strings
     */
    private Set<String> getAll$refs(TypedContent content) {
        try {
            JsonNode tree = ContentTypeUtil.parseJsonOrYaml(content);
            Set<String> refs = new HashSet<>();
            findRefs(tree, refs);
            return refs;
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    /**
     * Recursively traverses a JSON tree to find all $ref properties.
     * Only external references (not starting with #/) are collected.
     *
     * @param node the current JSON node to examine
     * @param refs the set to collect references into
     */
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
