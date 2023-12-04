package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.JsonContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.dereference.AsyncApiDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.OpenApiContentExtractor;
import io.apicurio.registry.content.refs.OpenApiReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.NoopCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.OpenApiContentValidator;
import io.apicurio.registry.types.ArtifactType;


public class OpenApiArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    @Override
    public String getArtifactType() {
        return ArtifactType.OPENAPI;
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return NoopCompatibilityChecker.INSTANCE;
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new JsonContentCanonicalizer();
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
    public ContentDereferencer getContentDereferencer() {
        return new AsyncApiDereferencer();
    }
    
    @Override
    public ReferenceFinder getReferenceFinder() {
        return new OpenApiReferenceFinder();
    }
}
