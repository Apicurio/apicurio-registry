package io.apicurio.registry.content.refs.resolve;

import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;

import java.util.List;
import java.util.Optional;

/**
 * Options controlling {@link ReferenceResolver#plan}.
 */
public final class PlanOptions {

    private final ArtifactNamingStrategy namingStrategy;
    private final List<ExistingArtifact> existingArtifacts;
    private final ReferenceUrlResolver urlResolver;
    private final ArtifactTypeUtilProviderFactory providerFactory;

    private PlanOptions(Builder builder) {
        this.namingStrategy = builder.namingStrategy;
        this.existingArtifacts = List.copyOf(builder.existingArtifacts);
        this.urlResolver = builder.urlResolver;
        this.providerFactory = builder.providerFactory != null ? builder.providerFactory
                : new DefaultArtifactTypeUtilProviderImpl(true);
    }

    /**
     * Creates a new builder.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the naming strategy.
     *
     * @return the naming strategy
     */
    public ArtifactNamingStrategy namingStrategy() {
        return namingStrategy;
    }

    /**
     * Returns the existing artifacts.
     *
     * @return the existing artifacts
     */
    public List<ExistingArtifact> existingArtifacts() {
        return existingArtifacts;
    }

    /**
     * Returns the URL resolver.
     *
     * @return the URL resolver, or empty when none is configured
     */
    public Optional<ReferenceUrlResolver> urlResolver() {
        return Optional.ofNullable(urlResolver);
    }

    /**
     * Returns the provider factory.
     *
     * @return the provider factory
     */
    public ArtifactTypeUtilProviderFactory providerFactory() {
        return providerFactory;
    }

    /**
     * Builder for {@link PlanOptions}.
     */
    public static final class Builder {

        private ArtifactNamingStrategy namingStrategy = DefaultNamingStrategy.INSTANCE;
        private List<ExistingArtifact> existingArtifacts = List.of();
        private ReferenceUrlResolver urlResolver;
        private ArtifactTypeUtilProviderFactory providerFactory;

        private Builder() {
        }

        /**
         * Sets the naming strategy for referenced artifacts.
         *
         * @param value the strategy
         * @return this builder
         */
        public Builder namingStrategy(ArtifactNamingStrategy value) {
            this.namingStrategy = value;
            return this;
        }

        /**
         * Sets the artifacts that already exist in the registry.
         *
         * @param value the existing artifacts
         * @return this builder
         */
        public Builder existingArtifacts(List<ExistingArtifact> value) {
            this.existingArtifacts = value;
            return this;
        }

        /**
         * Sets the resolver for absolute reference URLs.
         *
         * @param value the resolver, or null for none
         * @return this builder
         */
        public Builder urlResolver(ReferenceUrlResolver value) {
            this.urlResolver = value;
            return this;
        }

        /**
         * Sets the artifact type provider factory.
         *
         * @param value the factory
         * @return this builder
         */
        public Builder providerFactory(ArtifactTypeUtilProviderFactory value) {
            this.providerFactory = value;
            return this;
        }

        /**
         * Builds the options.
         *
         * @return the options
         */
        public PlanOptions build() {
            return new PlanOptions(this);
        }
    }
}
