package io.apicurio.registry.types.provider;

import java.util.Objects;
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
import io.apicurio.registry.rules.validity.NoOpContentValidator;

/**
 * Immutable configuration describing how a particular artifact type should be handled.
 * <p>
 * The configuration is built from lazily supplied content utilities such as accepters,
 * validators, canonicalizers, dereferencers, and reference finders.
 * </p>
 */
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

    /**
     * Returns the content types supported by this provider.
     *
     * @return the supported content types
     */
    public Set<String> getContentTypes() {
        return contentTypes;
    }

    /**
     * Indicates whether this provider supports reference resolution with context.
     *
     * @return {@code true} if contextual references are supported; otherwise {@code false}
     */
    public boolean supportsReferencesWithContext() {
        return supportsReferencesWithContext;
    }

    /**
     * Returns the configured content accepter.
     *
     * @return the accepter supplier
     */
    public Supplier<ContentAccepter> getAccepter() {
        return accepter;
    }

    /**
     * Returns the configured compatibility checker.
     *
     * @return the compatibility checker supplier
     */
    public Supplier<CompatibilityChecker> getCompatibilityChecker() {
        return compatibilityChecker;
    }

    /**
     * Returns the configured content canonicalizer.
     *
     * @return the canonicalizer supplier
     */
    public Supplier<ContentCanonicalizer> getCanonicalizer() {
        return canonicalizer;
    }

    /**
     * Returns the configured content validator.
     *
     * @return the validator supplier
     */
    public Supplier<ContentValidator> getValidator() {
        return validator;
    }

    /**
     * Returns the configured content extractor.
     *
     * @return the extractor supplier
     */
    public Supplier<ContentExtractor> getExtractor() {
        return extractor;
    }

    /**
     * Returns the configured content dereferencer.
     *
     * @return the dereferencer supplier
     */
    public Supplier<ContentDereferencer> getDereferencer() {
        return dereferencer;
    }

    /**
     * Returns the configured reference finder.
     *
     * @return the reference finder supplier
     */
    public Supplier<ReferenceFinder> getReferenceFinder() {
        return referenceFinder;
    }

    /**
     * Returns the configured reference artifact identifier extractor.
     *
     * @return the reference artifact identifier extractor supplier
     */
    public Supplier<ReferenceArtifactIdentifierExtractor> getReferenceArtifactIdentifierExtractor() {
        return referenceArtifactIdentifierExtractor;
    }

    /**
     * Returns the configured structured content extractor.
     *
     * @return the structured content extractor supplier
     */
    public Supplier<StructuredContentExtractor> getStructuredContentExtractor() {
        return structuredContentExtractor;
    }

    /**
     * Builder for {@link ProviderConfig} instances.
     */
    public static class Builder {
        private Set<String> contentTypes = Set.of();
        private boolean supportsReferencesWithContext = false;
        private Supplier<ContentAccepter> accepter = () -> NoOpContentAccepter.INSTANCE;
        private Supplier<CompatibilityChecker> compatibilityChecker = () -> NoopCompatibilityChecker.INSTANCE;
        private Supplier<ContentCanonicalizer> canonicalizer = () -> NoOpContentCanonicalizer.INSTANCE;
        private Supplier<ContentValidator> validator = () -> NoOpContentValidator.INSTANCE;
        private Supplier<ContentExtractor> extractor = () -> NoopContentExtractor.INSTANCE;
        private Supplier<ContentDereferencer> dereferencer = () -> NoopContentDereferencer.INSTANCE;
        private Supplier<ReferenceFinder> referenceFinder = () -> NoOpReferenceFinder.INSTANCE;
        private Supplier<ReferenceArtifactIdentifierExtractor> referenceArtifactIdentifierExtractor = () -> DefaultReferenceArtifactIdentifierExtractor.INSTANCE;
        private Supplier<StructuredContentExtractor> structuredContentExtractor = () -> NoopStructuredContentExtractor.INSTANCE;

        /**
         * Sets the supported content types.
         *
         * @param contentTypes the supported content types
         * @return this builder
         */
        public Builder contentTypes(Set<String> contentTypes) {
            this.contentTypes = (contentTypes != null) ? Set.copyOf(contentTypes) : Set.of();
            return this;
        }

        /**
         * Sets whether the provider supports references with context.
         *
         * @param supports whether contextual references are supported
         * @return this builder
         */
        public Builder supportsReferencesWithContext(boolean supports) {
            this.supportsReferencesWithContext = supports;
            return this;
        }

        /**
         * Sets the content accepter supplier.
         *
         * @param accepter the accepter supplier
         * @return this builder
         */
        public Builder accepter(Supplier<ContentAccepter> accepter) {
            this.accepter = accepter;
            return this;
        }

        /**
         * Sets the compatibility checker supplier.
         *
         * @param checker the compatibility checker supplier
         * @return this builder
         */
        public Builder compatibilityChecker(Supplier<CompatibilityChecker> checker) {
            this.compatibilityChecker = checker;
            return this;
        }

        /**
         * Sets the content canonicalizer supplier.
         *
         * @param canonicalizer the canonicalizer supplier
         * @return this builder
         */
        public Builder canonicalizer(Supplier<ContentCanonicalizer> canonicalizer) {
            this.canonicalizer = canonicalizer;
            return this;
        }

        /**
         * Sets the content validator supplier.
         *
         * @param validator the validator supplier
         * @return this builder
         */
        public Builder validator(Supplier<ContentValidator> validator) {
            this.validator = validator;
            return this;
        }

        /**
         * Sets the content extractor supplier.
         *
         * @param extractor the extractor supplier
         * @return this builder
         */
        public Builder extractor(Supplier<ContentExtractor> extractor) {
            this.extractor = extractor;
            return this;
        }

        /**
         * Sets the content dereferencer supplier.
         *
         * @param dereferencer the dereferencer supplier
         * @return this builder
         */
        public Builder dereferencer(Supplier<ContentDereferencer> dereferencer) {
            this.dereferencer = dereferencer;
            return this;
        }

        /**
         * Sets the reference finder supplier.
         *
         * @param finder the reference finder supplier
         * @return this builder
         */
        public Builder referenceFinder(Supplier<ReferenceFinder> finder) {
            this.referenceFinder = finder;
            return this;
        }

        /**
         * Sets the reference artifact identifier extractor supplier.
         *
         * @param extractor the reference artifact identifier extractor supplier
         * @return this builder
         */
        public Builder referenceArtifactIdentifierExtractor(Supplier<ReferenceArtifactIdentifierExtractor> extractor) {
            this.referenceArtifactIdentifierExtractor = extractor;
            return this;
        }

        /**
         * Sets the structured content extractor supplier.
         *
         * @param extractor the structured content extractor supplier
         * @return this builder
         */
        public Builder structuredContentExtractor(Supplier<StructuredContentExtractor> extractor) {
            this.structuredContentExtractor = extractor;
            return this;
        }

        /**
         * Builds the immutable provider configuration.
         *
         * @return a new provider configuration
         */
        public ProviderConfig build() {
            Objects.requireNonNull(contentTypes, "contentTypes must not be null");
            if (contentTypes.isEmpty()) {
                throw new IllegalArgumentException("contentTypes must not be empty");
            }
            Objects.requireNonNull(accepter, "accepter must not be null");
            Objects.requireNonNull(compatibilityChecker, "compatibilityChecker must not be null");
            Objects.requireNonNull(canonicalizer, "canonicalizer must not be null");
            Objects.requireNonNull(validator, "validator must not be null");
            Objects.requireNonNull(extractor, "extractor must not be null");
            Objects.requireNonNull(dereferencer, "dereferencer must not be null");
            Objects.requireNonNull(referenceFinder, "referenceFinder must not be null");
            Objects.requireNonNull(referenceArtifactIdentifierExtractor, "referenceArtifactIdentifierExtractor must not be null");
            Objects.requireNonNull(structuredContentExtractor, "structuredContentExtractor must not be null");
            return new ProviderConfig(this);
        }
    }
}