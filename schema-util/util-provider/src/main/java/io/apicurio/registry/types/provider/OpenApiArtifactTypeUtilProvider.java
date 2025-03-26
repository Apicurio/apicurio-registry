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

    public static final OpenApiContentAccepter contentAccepter = new OpenApiContentAccepter();
    public static final OpenApiCompatibilityChecker compatibilityChecker = new OpenApiCompatibilityChecker();
    public static final OpenApiContentCanonicalizer contentCanonicalizer = new OpenApiContentCanonicalizer();
    public static final OpenApiContentValidator contentValidator = new OpenApiContentValidator();
    public static final OpenApiContentExtractor contentExtractor = new OpenApiContentExtractor();
    public static final OpenApiDereferencer dereferencer = new OpenApiDereferencer();
    public static final OpenApiReferenceFinder referenceFinder = new OpenApiReferenceFinder();

    @Override
    public String getArtifactType() {
        return ArtifactType.OPENAPI;
    }

    @Override
    public ContentAccepter getContentAccepter() {
        return contentAccepter;
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return compatibilityChecker;
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return contentCanonicalizer;
    }

    @Override
    protected ContentValidator createContentValidator() {
        return contentValidator;
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return contentExtractor;
    }

    @Override
    public ContentDereferencer getContentDereferencer() {
        return dereferencer;
    }

    @Override
    public ReferenceFinder getReferenceFinder() {
        return referenceFinder;
    }

    @Override
    public boolean supportsReferencesWithContext() {
        return true;
    }

}
