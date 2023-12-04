package io.apicurio.registry.rules.validity;

import java.util.List;
import java.util.Map;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolationException;

/**
 * Validates content.  Syntax and semantic validations are possible based on configuration.  An
 * implementation of this interface should exist for each content-type supported by the registry.
 * 
 * Also provides validation of references.
 *
 */
public interface ContentValidator {

    /**
     * Called to validate the given content.
     *
     * @param level           the level
     * @param artifactContent the content
     * @param resolvedReferences a map containing the resolved references
     * @throws RuleViolationException for any invalid content
     */
    public void validate(ValidityLevel level, ContentHandle artifactContent, Map<String, ContentHandle> resolvedReferences) throws RuleViolationException;
    
    /**
     * Ensures that all references in the content are represented in the list of passed references.  This is used
     * to ensure that the content does not have any references that are unmapped.
     * 
     * @param artifactContent
     * @param references
     * @throws RuleViolationException
     */
    public void validateReferences(ContentHandle artifactContent, List<ArtifactReference> references) throws RuleViolationException;

}
