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

    public static final AsyncApiContentAccepter contentAcceptor = new AsyncApiContentAccepter();
    public static final AsyncApiContentCanonicalizer contentCanonicalizer = new AsyncApiContentCanonicalizer();
    public static final AsyncApiContentValidator contentValidator = new AsyncApiContentValidator();
    public static final AsyncApiContentExtractor contentExtractor = new AsyncApiContentExtractor();
    public static final AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
    public static final AsyncApiReferenceFinder referenceFinder = new AsyncApiReferenceFinder();

    @Override
    public String getArtifactType() {
        return ArtifactType.ASYNCAPI;
    }

    @Override
    public ContentAccepter getContentAccepter() {
        return contentAcceptor;
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return NoopCompatibilityChecker.INSTANCE;
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
