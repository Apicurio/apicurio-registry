package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.refs.ReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;

public abstract class AbstractArtifactTypeUtilProvider implements ArtifactTypeUtilProvider {

    private volatile CompatibilityChecker checker;
    private volatile ContentCanonicalizer canonicalizer;
    private volatile ContentValidator validator;
    private volatile ContentExtractor extractor;
    private volatile ReferenceArtifactIdentifierExtractor referenceArtifactIdentifierExtractor;

    @Override
    public CompatibilityChecker getCompatibilityChecker() {
        if (checker == null) {
            checker = createCompatibilityChecker();
        }
        return checker;
    }

    protected abstract CompatibilityChecker createCompatibilityChecker();

    @Override
    public ContentCanonicalizer getContentCanonicalizer() {
        if (canonicalizer == null) {
            canonicalizer = createContentCanonicalizer();
        }
        return canonicalizer;
    }

    protected abstract ContentCanonicalizer createContentCanonicalizer();

    @Override
    public ContentValidator getContentValidator() {
        if (validator == null) {
            validator = createContentValidator();
        }
        return validator;
    }

    protected abstract ContentValidator createContentValidator();

    @Override
    public ContentExtractor getContentExtractor() {
        if (extractor == null) {
            extractor = createContentExtractor();
        }
        return extractor;
    }

    protected abstract ContentExtractor createContentExtractor();

    protected abstract ReferenceArtifactIdentifierExtractor createReferenceArtifactIdentifierExtractor();

    @Override
    public ReferenceArtifactIdentifierExtractor getReferenceArtifactIdentifierExtractor() {
        if (referenceArtifactIdentifierExtractor == null) {
            referenceArtifactIdentifierExtractor = createReferenceArtifactIdentifierExtractor();
        }
        return referenceArtifactIdentifierExtractor;
    }
}
