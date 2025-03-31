package io.apicurio.registry.types.provider;

import io.apicurio.registry.config.artifactTypes.ArtifactTypeConfiguration;
import io.apicurio.registry.config.artifactTypes.Provider;
import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.NoOpContentAccepter;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.NoOpContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.dereference.NoopContentDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.NoopContentExtractor;
import io.apicurio.registry.content.refs.NoOpReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.NoopCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.NoOpContentValidator;

/**
 * An implementation of {@link ArtifactTypeUtilProvider} that comes from a configuration
 * file.  The config file typically contains a list of supported artifact types.  Each
 * artifact type contains configuration information needed to implement a single
 * {@link ArtifactTypeUtilProvider}.
 */
public class ConfiguredArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    private final ArtifactTypeConfiguration artifactType;

    public ConfiguredArtifactTypeUtilProvider(ArtifactTypeConfiguration artifactType) {
        this.artifactType = artifactType;
    }

    @Override
    public String getArtifactType() {
        return this.artifactType.getArtifactType();
    }

    @Override
    public boolean supportsReferencesWithContext() {
        Boolean srwc = this.artifactType.getSupportsReferencesWithContext();
        return srwc == null ? Boolean.FALSE : srwc;
    }

    @Override
    protected ContentAccepter createContentAccepter() {
        Provider provider = this.artifactType.getContentAccepter();
        if (provider == null) {
            return NoOpContentAccepter.INSTANCE;
        }
        return new ConfiguredContentAccepter(this.artifactType);
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        Provider provider = this.artifactType.getCompatibilityChecker();
        if (provider == null) {
            return NoopCompatibilityChecker.INSTANCE;
        }
        return new ConfiguredCompatibilityChecker(provider);
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        Provider provider = this.artifactType.getContentCanonicalizer();
        if (provider == null) {
            return NoOpContentCanonicalizer.INSTANCE;
        }
        return new ConfiguredContentCanonicalizer(provider);
    }

    @Override
    protected ContentValidator createContentValidator() {
        Provider provider = this.artifactType.getContentValidator();
        if (provider == null) {
            return NoOpContentValidator.INSTANCE;
        }
        return new ConfiguredContentValidator(provider);
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return NoopContentExtractor.INSTANCE;
    }

    @Override
    protected ContentDereferencer createContentDereferencer() {
        Provider provider = this.artifactType.getContentDereferencer();
        if (provider == null) {
            return NoopContentDereferencer.INSTANCE;
        }
        return new ConfiguredContentDereferencer(provider);
    }

    @Override
    protected ReferenceFinder createReferenceFinder() {
        Provider provider = this.artifactType.getReferenceFinder();
        if (provider == null) {
            return NoOpReferenceFinder.INSTANCE;
        }
        return new ConfiguredReferenceFinder(provider);
    }
}
