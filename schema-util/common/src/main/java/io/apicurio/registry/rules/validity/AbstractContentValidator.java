package io.apicurio.registry.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Abstract base class for content validators that provides common logic for reference validation.
 */
public abstract class AbstractContentValidator implements ContentValidator {

    /**
     * Validates that all required references are present in the list of provided artifact references.
     * Throws a {@link RuleViolationException} if any references are missing.
     *
     * @param references the list of artifact references to check against
     * @param requiredReferences the set of names of references that are required
     * @param violationDescription the description to use for any rule violations
     * @throws RuleViolationException if any required references are missing
     */
    protected void validateMappedReferences(List<ArtifactReference> references,
            Set<String> requiredReferences, String violationDescription) throws RuleViolationException {
        if (references == null) {
            references = Collections.emptyList();
        }

        Set<String> mappedRefNames = references.stream()
        .map(ref -> extractReferenceName(ref))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

        Set<RuleViolation> violations = requiredReferences.stream()
                .filter(ref -> !mappedRefNames.contains(ref))
                .map(missingRef -> new RuleViolation(violationDescription, missingRef))
                .collect(Collectors.toSet());

        if (!violations.isEmpty()) {
            throw new RuleViolationException(violationDescription,
                    RuleType.INTEGRITY, IntegrityLevel.ALL_REFS_MAPPED.name(), violations);
        }
    }

    /**
     * Extracts the name from an artifact reference. Can be overridden if a different name extraction
     * logic is needed for a specific content type.
     *
     * @param ref the artifact reference
     * @return the reference name
     */
    protected String extractReferenceName(ArtifactReference ref) {
        return ref.getName();
    }

    /**
     * Extracts external $ref values from JSON/YAML content, ignoring internal JSON Pointer references starting with "#/".
     *
     * @param content the typed content to extract references from
     * @return set of external reference strings
     */
    protected Set<String> extractExternalJsonRefs(TypedContent content) {
        try {
            JsonNode tree = ContentTypeUtil.parseJsonOrYaml(content);
            Set<String> refs = new HashSet<>();
            findExternalRefs(tree, refs);
            return refs;
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    /**
     * Recursively traverses a JSON node to collect external $ref values.
     *
     * @param node the current JSON node
     * @param refs the set collecting external references
     */
    protected void findExternalRefs(JsonNode node, Set<String> refs) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            if (node.has("$ref")) {
                String ref = node.get("$ref").asText(null);
                if (ref != null && !ref.startsWith("#/")) {
                    refs.add(ref);
                }
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                findExternalRefs(fields.next().getValue(), refs);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                findExternalRefs(element, refs);
            }
        }
    }
}
