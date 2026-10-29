package io.apicurio.utils.test.raml.microsvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.rules.validity.AbstractContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.RuleType;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A {@link io.apicurio.registry.rules.validity.ContentValidator} implementation for RAML content. Validates
 * both the structural syntax of a RAML document and the completeness of its external references
 * (RAML {@code !include} style includes, represented in this test harness as {@code ~include} tokens).
 */
public class RamlContentValidator extends AbstractContentValidator {

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    private final RamlReferenceFinder referenceFinder = new RamlReferenceFinder();

    @Override
    public void validate(ValidityLevel level, TypedContent content, Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        Set<RuleViolation> violations = new HashSet<>();

        if (!content.getContentType().equals("application/x-yaml")) {
            violations.add(new RuleViolation("Incorrect content type.  Expected 'application/x-yaml' but found '" + content.getContentType() + "'.", null));
        } else {
            if (!content.getContent().content().startsWith("#%RAML 1.0")) {
                violations.add(new RuleViolation("Missing '#%RAML 1.0' content header.", null));
            } else {
                try {
                    mapper.readTree(content.getContent().content());
                } catch (Throwable t) {
                    violations.add(new RuleViolation("Failed to parse RAML content.  Expected valid YAML formatted content.", t.getMessage()));
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new RuleViolationException("RAML validation failed.", RuleType.VALIDITY, level.name(), violations);
        }
    }

    /**
     * Ensures that every {@code ~include} reference found in the RAML content is represented in the
     * supplied list of artifact references. Any include that is not mapped is reported as a rule
     * violation.
     *
     * @see io.apicurio.registry.rules.validity.ContentValidator#validateReferences(TypedContent, List)
     */
    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references) throws RuleViolationException {
        Set<String> requiredReferences = referenceFinder.findExternalReferences(content).stream()
                .map(ExternalReference::getFullReference).collect(Collectors.toSet());
        validateMappedReferences(references, requiredReferences, "Unmapped reference detected.");
    }
}
