package io.apicurio.registry.types.provider;

import java.util.Set;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.refs.ReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;

/**
 * {@link ArtifactTypeUtilProvider} implementation backed by a {@link ProviderConfig}.
 * <p>
 * This provider exposes the configured artifact type and delegates all utility creation to
 * lazily supplied components stored in the configuration.
 * </p>
 */
public class ConfigurableArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    private final String artifactType;
    private final ProviderConfig config;

    /**
     * Creates a configurable provider for the given artifact type.
     *
     * @param artifactType the artifact type identifier
     * @param config the provider configuration
     */
    public ConfigurableArtifactTypeUtilProvider(String artifactType, ProviderConfig config) {
        this.artifactType = artifactType;
        this.config = config;
    }

    /**
     * Returns the artifact type handled by this provider.
     *
     * @return the artifact type identifier
     */
    @Override
    public String getArtifactType() {
        return artifactType;
    }

    /**
     * Returns the supported content types for this provider.
     *
     * @return the supported content types
     */
    @Override
    public Set<String> getContentTypes() {
        return config.getContentTypes();
    }

    /**
     * Indicates whether this provider supports reference resolution with context.
     *
     * @return {@code true} if contextual references are supported; otherwise {@code false}
     */
    @Override
    public boolean supportsReferencesWithContext() {
        return config.supportsReferencesWithContext();
    }

    /**
     * Creates the configured content accepter.
     *
     * @return the content accepter
     */
    @Override
    protected ContentAccepter createContentAccepter() {
        return config.getAccepter().get();
    }

    /**
     * Creates the configured compatibility checker.
     *
     * @return the compatibility checker
     */
    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return config.getCompatibilityChecker().get();
    }

    /**
     * Creates the configured content canonicalizer.
     *
     * @return the content canonicalizer
     */
    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return config.getCanonicalizer().get();
    }

    /**
     * Creates the configured content validator.
     *
     * @return the content validator
     */
    @Override
    protected ContentValidator createContentValidator() {
        return config.getValidator().get();
    }

    /**
     * Creates the configured content extractor.
     *
     * @return the content extractor
     */
    @Override
    protected ContentExtractor createContentExtractor() {
        return config.getExtractor().get();
    }

    /**
     * Creates the configured content dereferencer.
     *
     * @return the content dereferencer
     */
    @Override
    protected ContentDereferencer createContentDereferencer() {
        return config.getDereferencer().get();
    }

    /**
     * Creates the configured reference finder.
     *
     * @return the reference finder
     */
    @Override
    protected ReferenceFinder createReferenceFinder() {
        return config.getReferenceFinder().get();
    }

    /**
     * Creates the configured reference artifact identifier extractor.
     *
     * @return the reference artifact identifier extractor
     */
    @Override
    protected ReferenceArtifactIdentifierExtractor createReferenceArtifactIdentifierExtractor() {
        return config.getReferenceArtifactIdentifierExtractor().get();
    }

    /**
     * Creates the configured structured content extractor.
     *
     * @return the structured content extractor
     */
    @Override
    protected StructuredContentExtractor createStructuredContentExtractor() {
        return config.getStructuredContentExtractor().get();
    }
}