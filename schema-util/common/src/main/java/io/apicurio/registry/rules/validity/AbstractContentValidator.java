package io.apicurio.registry.rules.validity;

import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;

import java.util.List;
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

        Set<String> mappedRefNames = references.stream()
                .map(this::extractReferenceName)
                .filter(Object::nonNull)
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
}
