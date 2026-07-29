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

public class ConfigurableArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    private final String artifactType;
    private final ProviderConfig config;

    public ConfigurableArtifactTypeUtilProvider(String artifactType, ProviderConfig config) {
        this.artifactType = artifactType;
        this.config = config;
    }

    @Override
    public String getArtifactType() {
        return artifactType;
    }

    @Override
    public Set<String> getContentTypes() {
        return config.getContentTypes();
    }

    @Override
    public boolean supportsReferencesWithContext() {
        return config.supportsReferencesWithContext();
    }

    @Override
    protected ContentAccepter createContentAccepter() {
        return config.getAccepter().get();
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return config.getCompatibilityChecker().get();
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return config.getCanonicalizer().get();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return config.getValidator().get();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return config.getExtractor().get();
    }

    @Override
    protected ContentDereferencer createContentDereferencer() {
        return config.getDereferencer().get();
    }

    @Override
    protected ReferenceFinder createReferenceFinder() {
        return config.getReferenceFinder().get();
    }

    @Override
    protected ReferenceArtifactIdentifierExtractor createReferenceArtifactIdentifierExtractor() {
        return config.getReferenceArtifactIdentifierExtractor().get();
    }

    @Override
    protected StructuredContentExtractor createStructuredContentExtractor() {
        return config.getStructuredContentExtractor().get();
    }
}