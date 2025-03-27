package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.OpenApiContentAccepter;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.OpenApiContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.dereference.OpenApiDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.OpenApiContentExtractor;
import io.apicurio.registry.content.refs.OpenApiReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.OpenApiCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.OpenApiContentValidator;
import io.apicurio.registry.types.ArtifactType;

public class OpenApiArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    @Override
    public String getArtifactType() {
        return ArtifactType.OPENAPI;
    }

    @Override
    public ContentAccepter createContentAccepter() {
        return new OpenApiContentAccepter();
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return new OpenApiCompatibilityChecker();
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new OpenApiContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new OpenApiContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return new OpenApiContentExtractor();
    }

    @Override
    public ContentDereferencer createContentDereferencer() {
        return new OpenApiDereferencer();
    }

    @Override
    public ReferenceFinder createReferenceFinder() {
        return new OpenApiReferenceFinder();
    }

    @Override
    public boolean supportsReferencesWithContext() {
        return true;
    }

}
