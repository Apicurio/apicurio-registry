package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.XsdContentAccepter;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.XmlContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.dereference.NoopContentDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.WsdlOrXsdContentExtractor;
import io.apicurio.registry.content.refs.NoOpReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.NoopCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.XsdContentValidator;
import io.apicurio.registry.types.ArtifactType;

public class XsdArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    public static final XsdContentAccepter contentAccepter = new XsdContentAccepter();
    public static final XmlContentCanonicalizer contentCanonicalizer = new XmlContentCanonicalizer();
    public static final XsdContentValidator contentValidator = new XsdContentValidator();
    public static final WsdlOrXsdContentExtractor contentExtractor = new WsdlOrXsdContentExtractor();

    @Override
    public String getArtifactType() {
        return ArtifactType.XSD;
    }

    @Override
    public ContentAccepter getContentAccepter() {
        return contentAccepter;
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
        return NoopContentDereferencer.INSTANCE;
    }

    @Override
    public ReferenceFinder getReferenceFinder() {
        return NoOpReferenceFinder.INSTANCE;
    }

    @Override
    public boolean supportsReferencesWithContext() {
        return false;
    }

}
