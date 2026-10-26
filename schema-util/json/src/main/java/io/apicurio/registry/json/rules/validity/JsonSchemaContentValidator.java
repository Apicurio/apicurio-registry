package io.apicurio.registry.json.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.json.content.refs.JsonSchemaRefScopes;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.validity.AbstractContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A content validator implementation for the JsonSchema content type.
 */
public class JsonSchemaContentValidator extends AbstractContentValidator {

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
            Set<RuleViolation> violations;
            try {
                violations = new HashSet<>(JsonSchemaLoader.load(content.getContent().content(), resolvedReferences));
            } catch (Exception e) {
                violations = Collections.singleton(new RuleViolation("JSON schema not valid: " + e.getMessage(), ""));
            }
            if (!violations.isEmpty()) {
                throw new RuleViolationException("Syntax or semantic violation for JSON Schema artifact.",
                        RuleType.VALIDITY, level.name(), violations);
            }
        }
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validateReferences(TypedContent, List)
     */
    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        Set<String> mappedNames = references == null ? Set.of()
                : references.stream().map(ArtifactReference::getName).filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Set<String> all$refs = getAll$refs(content, mappedNames);
        validateMappedReferences(references, all$refs, "Unmapped reference detected.");
    }

    /**
     * Extracts all external $ref values from a JSON Schema document, as the reference names they must be
     * mapped under. Internal references (starting with #/) are excluded. A $ref is required under its
     * literal value, unless a reference is mapped under the URI it resolves to against its $id scope.
     *
     * @param content the JSON Schema content to analyze
     * @param mappedNames the names of the references mapped for the content
     * @return set of external reference strings
     */
    private Set<String> getAll$refs(TypedContent content, Set<String> mappedNames) {
        try {
            JsonNode tree = ContentTypeUtil.parseJsonOrYaml(content);
            return JsonSchemaRefScopes.find(tree).stream().filter(site -> !site.ref().startsWith("#/"))
                    .map(site -> site.resolved() != null && mappedNames.contains(site.resolved())
                            ? site.resolved() : site.ref())
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }
}
