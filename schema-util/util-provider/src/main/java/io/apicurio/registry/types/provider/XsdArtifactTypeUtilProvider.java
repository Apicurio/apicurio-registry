package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.XmlContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
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

    /**
     * @see io.apicurio.registry.types.provider.ArtifactTypeUtilProvider#getArtifactType()
     */
    @Override
    public String getArtifactType() {
        return ArtifactType.XSD;
    }

    /**
     * @see io.apicurio.registry.types.provider.AbstractArtifactTypeUtilProvider#createCompatibilityChecker()
     */
    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return NoopCompatibilityChecker.INSTANCE;
    }

    /**
     * @see io.apicurio.registry.types.provider.AbstractArtifactTypeUtilProvider#createContentCanonicalizer()
     */
    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new XmlContentCanonicalizer();
    }

    /**
     * @see io.apicurio.registry.types.provider.AbstractArtifactTypeUtilProvider#createContentValidator()
     */
    @Override
    protected ContentValidator createContentValidator() {
        return new XsdContentValidator();
    }

    /**
     * @see io.apicurio.registry.types.provider.AbstractArtifactTypeUtilProvider#createContentExtractor()
     */
    @Override
    protected ContentExtractor createContentExtractor() {
        return new WsdlOrXsdContentExtractor();
    }

    @Override
    public ContentDereferencer getContentDereferencer() {
        return null;
    }
    
    /**
     * @see io.apicurio.registry.types.provider.ArtifactTypeUtilProvider#getReferenceFinder()
     */
    @Override
    public ReferenceFinder getReferenceFinder() {
        return NoOpReferenceFinder.INSTANCE;
    }
}
