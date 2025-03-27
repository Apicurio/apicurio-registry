package io.apicurio.registry.types.provider;

import io.apicurio.registry.config.artifactTypes.Provider;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;

import java.util.List;
import java.util.Map;

public class ConfiguredContentValidator implements ContentValidator {
    public ConfiguredContentValidator(Provider provider) {
    }

    @Override
    public void validate(ValidityLevel level, TypedContent content, Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
    }

    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references) throws RuleViolationException {
    }
}
