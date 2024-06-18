package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolationException;

import java.util.List;
import java.util.Map;

/**
 * Validates content. Syntax and semantic validations are possible based on configuration. An implementation
 * of this interface should exist for each content-type supported by the registry. Also provides validation of
 * references.
 */
public interface ContentValidator {

    /**
     * Called to validate the given content.
     */
    public void validate(ValidityLevel level, TypedContent content,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException;

    /**
     * Ensures that all references in the content are represented in the list of passed references. This is
     * used to ensure that the content does not have any references that are unmapped.
     */
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException;

}
