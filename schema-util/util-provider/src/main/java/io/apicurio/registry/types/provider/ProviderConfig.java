package io.apicurio.registry.types.provider;

import java.util.Set;
import java.util.function.Supplier;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.NoOpContentAccepter;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.NoOpContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.dereference.NoopContentDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.NoopContentExtractor;
import io.apicurio.registry.content.extract.NoopStructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.refs.DefaultReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.NoOpReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.NoopCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;

public final class ProviderConfig {

    private final Set<String> contentTypes;
    private final boolean supportsReferencesWithContext;
    private final Supplier<ContentAccepter> accepter;
    private final Supplier<CompatibilityChecker> compatibilityChecker;
    private final Supplier<ContentCanonicalizer> canonicalizer;
    private final Supplier<ContentValidator> validator;
    private final Supplier<ContentExtractor> extractor;
    private final Supplier<ContentDereferencer> dereferencer;
    private final Supplier<ReferenceFinder> referenceFinder;
    private final Supplier<ReferenceArtifactIdentifierExtractor> referenceArtifactIdentifierExtractor;
    private final Supplier<StructuredContentExtractor> structuredContentExtractor;

    private ProviderConfig(Builder builder) {
        this.contentTypes = builder.contentTypes;
        this.supportsReferencesWithContext = builder.supportsReferencesWithContext;
        this.accepter = builder.accepter;
        this.compatibilityChecker = builder.compatibilityChecker;
        this.canonicalizer = builder.canonicalizer;
        this.validator = builder.validator;
        this.extractor = builder.extractor;
        this.dereferencer = builder.dereferencer;
        this.referenceFinder = builder.referenceFinder;
        this.referenceArtifactIdentifierExtractor = builder.referenceArtifactIdentifierExtractor;
        this.structuredContentExtractor = builder.structuredContentExtractor;
    }

    public Set<String> getContentTypes() {
        return contentTypes;
    }

    public boolean supportsReferencesWithContext() {
        return supportsReferencesWithContext;
    }

    public Supplier<ContentAccepter> getAccepter() {
        return accepter;
    }

    public Supplier<CompatibilityChecker> getCompatibilityChecker() {
        return compatibilityChecker;
    }

    public Supplier<ContentCanonicalizer> getCanonicalizer() {
        return canonicalizer;
    }

    public Supplier<ContentValidator> getValidator() {
        return validator;
    }

    public Supplier<ContentExtractor> getExtractor() {
        return extractor;
    }

    public Supplier<ContentDereferencer> getDereferencer() {
        return dereferencer;
    }

    public Supplier<ReferenceFinder> getReferenceFinder() {
        return referenceFinder;
    }

    public Supplier<ReferenceArtifactIdentifierExtractor> getReferenceArtifactIdentifierExtractor() {
        return referenceArtifactIdentifierExtractor;
    }

    public Supplier<StructuredContentExtractor> getStructuredContentExtractor() {
        return structuredContentExtractor;
    }

    public static class Builder {
        private Set<String> contentTypes = Set.of();
        private boolean supportsReferencesWithContext = false;
        private Supplier<ContentAccepter> accepter = () -> NoOpContentAccepter.INSTANCE;
        private Supplier<CompatibilityChecker> compatibilityChecker = () -> NoopCompatibilityChecker.INSTANCE;
        private Supplier<ContentCanonicalizer> canonicalizer = () -> NoOpContentCanonicalizer.INSTANCE;
        private Supplier<ContentValidator> validator;
        private Supplier<ContentExtractor> extractor = () -> NoopContentExtractor.INSTANCE;
        private Supplier<ContentDereferencer> dereferencer = () -> NoopContentDereferencer.INSTANCE;
        private Supplier<ReferenceFinder> referenceFinder = () -> NoOpReferenceFinder.INSTANCE;
        private Supplier<ReferenceArtifactIdentifierExtractor> referenceArtifactIdentifierExtractor = () -> DefaultReferenceArtifactIdentifierExtractor.INSTANCE;
        private Supplier<StructuredContentExtractor> structuredContentExtractor = () -> NoopStructuredContentExtractor.INSTANCE;

        public Builder contentTypes(Set<String> contentTypes) {
            this.contentTypes = contentTypes;
            return this;
        }

        public Builder supportsReferencesWithContext(boolean supports) {
            this.supportsReferencesWithContext = supports;
            return this;
        }

        public Builder accepter(Supplier<ContentAccepter> accepter) {
            this.accepter = accepter;
            return this;
        }

        public Builder compatibilityChecker(Supplier<CompatibilityChecker> checker) {
            this.compatibilityChecker = checker;
            return this;
        }

        public Builder canonicalizer(Supplier<ContentCanonicalizer> canonicalizer) {
            this.canonicalizer = canonicalizer;
            return this;
        }

        public Builder validator(Supplier<ContentValidator> validator) {
            this.validator = validator;
            return this;
        }

        public Builder extractor(Supplier<ContentExtractor> extractor) {
            this.extractor = extractor;
            return this;
        }

        public Builder dereferencer(Supplier<ContentDereferencer> dereferencer) {
            this.dereferencer = dereferencer;
            return this;
        }

        public Builder referenceFinder(Supplier<ReferenceFinder> finder) {
            this.referenceFinder = finder;
            return this;
        }

        public Builder referenceArtifactIdentifierExtractor(Supplier<ReferenceArtifactIdentifierExtractor> extractor) {
            this.referenceArtifactIdentifierExtractor = extractor;
            return this;
        }

        public Builder structuredContentExtractor(Supplier<StructuredContentExtractor> extractor) {
            this.structuredContentExtractor = extractor;
            return this;
        }

        public ProviderConfig build() {
            return new ProviderConfig(this);
        }
    }
}