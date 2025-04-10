package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;

public abstract class AbstractArtifactTypeUtilProvider implements ArtifactTypeUtilProvider {

    private volatile ContentAccepter contentAccepter;
    private volatile CompatibilityChecker compatibilityChecker;
    private volatile ContentCanonicalizer contentCanonicalizer;
    private volatile ContentValidator contentValidator;
    private volatile ContentExtractor contentExtractor;
    private volatile ContentDereferencer contentDereferencer;
    private volatile ReferenceFinder referenceFinder;

    @Override
    public ContentAccepter getContentAccepter() {
        if (contentAccepter == null) {
            contentAccepter = createContentAccepter();
        }
        return contentAccepter;
    }

    protected abstract ContentAccepter createContentAccepter();

    @Override
    public CompatibilityChecker getCompatibilityChecker() {
        if (compatibilityChecker == null) {
            compatibilityChecker = createCompatibilityChecker();
        }
        return compatibilityChecker;
    }

    protected abstract CompatibilityChecker createCompatibilityChecker();

    @Override
    public ContentCanonicalizer getContentCanonicalizer() {
        if (contentCanonicalizer == null) {
            contentCanonicalizer = createContentCanonicalizer();
        }
        return contentCanonicalizer;
    }

    protected abstract ContentCanonicalizer createContentCanonicalizer();

    @Override
    public ContentValidator getContentValidator() {
        if (contentValidator == null) {
            contentValidator = createContentValidator();
        }
        return contentValidator;
    }

    protected abstract ContentValidator createContentValidator();

    @Override
    public ContentExtractor getContentExtractor() {
        if (contentExtractor == null) {
            contentExtractor = createContentExtractor();
        }
        return contentExtractor;
    }

    protected abstract ContentExtractor createContentExtractor();

    @Override
    public ContentDereferencer getContentDereferencer() {
        if (contentDereferencer == null) {
            contentDereferencer = createContentDereferencer();
        }
        return contentDereferencer;
    }

    protected abstract ContentDereferencer createContentDereferencer();

    @Override
    public ReferenceFinder getReferenceFinder() {
        if (referenceFinder == null) {
            referenceFinder = createReferenceFinder();
        }
        return referenceFinder;
    }

    protected abstract ReferenceFinder createReferenceFinder();

}
