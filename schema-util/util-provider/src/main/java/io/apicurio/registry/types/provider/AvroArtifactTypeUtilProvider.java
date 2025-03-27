package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.AvroContentAccepter;
import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.EnhancedAvroContentCanonicalizer;
import io.apicurio.registry.content.dereference.AvroDereferencer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.extract.AvroContentExtractor;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.refs.AvroReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.AvroCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.validity.AvroContentValidator;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.types.ArtifactType;

public class AvroArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    @Override
    public String getArtifactType() {
        return ArtifactType.AVRO;
    }

    @Override
    public ContentAccepter createContentAccepter() {
        return new AvroContentAccepter();
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return new AvroCompatibilityChecker();
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new EnhancedAvroContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new AvroContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return new AvroContentExtractor();
    }

    @Override
    public ContentDereferencer createContentDereferencer() {
        return new AvroDereferencer();
    }

    @Override
    public ReferenceFinder createReferenceFinder() {
        return new AvroReferenceFinder();
    }

    @Override
    public boolean supportsReferencesWithContext() {
        return false;
    }

}
