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
import io.apicurio.registry.http.HttpClientService;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.NoopCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.NoOpContentValidator;
import io.apicurio.registry.script.ScriptingService;

import java.util.Set;

/**
 * An implementation of {@link ArtifactTypeUtilProvider} that comes from a configuration
 * file.  The config file typically contains a list of supported artifact types.  Each
 * artifact type contains configuration information needed to implement a single
 * {@link ArtifactTypeUtilProvider}.
 */
public class ConfiguredArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    private final ArtifactTypeConfiguration artifactType;
    private final HttpClientService httpClientService;
    private final ScriptingService scriptingService;

    public ConfiguredArtifactTypeUtilProvider(HttpClientService httpClientService, ScriptingService scriptingService, ArtifactTypeConfiguration artifactType) {
        this.httpClientService = httpClientService;
        this.scriptingService = scriptingService;
        this.artifactType = artifactType;
    }

    @Override
    public String getArtifactType() {
        return this.artifactType.getArtifactType();
    }

    @Override
    public Set<String> getContentTypes() {
        if (this.artifactType.getContentTypes() == null || this.artifactType.getContentTypes().isEmpty()) {
            return Set.of();
        }
        return Set.of(this.artifactType.getContentTypes().toArray(new String[this.artifactType.getContentTypes().size()]));
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
        return new ConfiguredContentAccepter(this.httpClientService, this.scriptingService, this.artifactType);
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        Provider provider = this.artifactType.getCompatibilityChecker();
        if (provider == null) {
            return NoopCompatibilityChecker.INSTANCE;
        }
        return new ConfiguredCompatibilityChecker(this.httpClientService, this.scriptingService, this.artifactType);
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        Provider provider = this.artifactType.getContentCanonicalizer();
        if (provider == null) {
            return NoOpContentCanonicalizer.INSTANCE;
        }
        return new ConfiguredContentCanonicalizer(this.httpClientService, this.scriptingService, this.artifactType);
    }

    @Override
    protected ContentValidator createContentValidator() {
        Provider provider = this.artifactType.getContentValidator();
        if (provider == null) {
            return NoOpContentValidator.INSTANCE;
        }
        return new ConfiguredContentValidator(this.httpClientService, this.scriptingService, this.artifactType);
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
        return new ConfiguredContentDereferencer(this.httpClientService, this.scriptingService, this.artifactType);
    }

    @Override
    protected ReferenceFinder createReferenceFinder() {
        Provider provider = this.artifactType.getReferenceFinder();
        if (provider == null) {
            return NoOpReferenceFinder.INSTANCE;
        }
        return new ConfiguredReferenceFinder(this.httpClientService, this.scriptingService, this.artifactType);
    }
}
