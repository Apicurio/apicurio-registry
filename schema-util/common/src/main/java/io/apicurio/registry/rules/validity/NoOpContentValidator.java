package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolationException;

import java.util.List;
import java.util.Map;

public class NoOpContentValidator implements ContentValidator {

    public static final NoOpContentValidator INSTANCE = new NoOpContentValidator();

    @Override
    public void validate(ValidityLevel level, TypedContent content, Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
    }

    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references) throws RuleViolationException {
    }

}
