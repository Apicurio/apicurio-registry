package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.WsdlContentAccepter;
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
import io.apicurio.registry.rules.validity.WsdlContentValidator;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;

import java.util.Set;

public class WsdlArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    @Override
    public String getArtifactType() {
        return ArtifactType.WSDL;
    }

    @Override
    public Set<String> getContentTypes() {
        return Set.of(ContentTypes.APPLICATION_XML);
    }

    @Override
    public ContentAccepter createContentAccepter() {
        return new WsdlContentAccepter();
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return NoopCompatibilityChecker.INSTANCE;
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new XmlContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new WsdlContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return new WsdlOrXsdContentExtractor();
    }

    @Override
    public ContentDereferencer createContentDereferencer() {
        return NoopContentDereferencer.INSTANCE;
    }

    @Override
    public ReferenceFinder createReferenceFinder() {
        return NoOpReferenceFinder.INSTANCE;
    }

    @Override
    public boolean supportsReferencesWithContext() {
        return false;
    }

}
