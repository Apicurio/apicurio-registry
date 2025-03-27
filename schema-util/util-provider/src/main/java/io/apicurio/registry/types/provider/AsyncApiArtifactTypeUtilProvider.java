package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.AsyncApiContentAccepter;
import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.canon.AsyncApiContentCanonicalizer;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.dereference.AsyncApiDereferencer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.extract.AsyncApiContentExtractor;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.refs.AsyncApiReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.NoopCompatibilityChecker;
import io.apicurio.registry.rules.validity.AsyncApiContentValidator;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.types.ArtifactType;

public class AsyncApiArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    @Override
    public String getArtifactType() {
        return ArtifactType.ASYNCAPI;
    }

    @Override
    protected ContentAccepter createContentAccepter() {
        return new AsyncApiContentAccepter();
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return NoopCompatibilityChecker.INSTANCE;
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new AsyncApiContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new AsyncApiContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return new AsyncApiContentExtractor();
    }

    @Override
    protected ContentDereferencer createContentDereferencer() {
        return new AsyncApiDereferencer();
    }

    @Override
    protected ReferenceFinder createReferenceFinder() {
        return new AsyncApiReferenceFinder();
    }

    @Override
    public boolean supportsReferencesWithContext() {
        return true;
    }
}
