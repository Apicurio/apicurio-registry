package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.ProtobufContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.dereference.ProtobufDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.NoopContentExtractor;
import io.apicurio.registry.content.refs.ProtobufReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.ProtobufCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.ProtobufContentValidator;
import io.apicurio.registry.types.ArtifactType;


public class ProtobufArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {
    @Override
    public String getArtifactType() {
        return ArtifactType.PROTOBUF;
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return new ProtobufCompatibilityChecker();
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new ProtobufContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new ProtobufContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return NoopContentExtractor.INSTANCE;
    }

    @Override
    public ContentDereferencer getContentDereferencer() {
        return new ProtobufDereferencer();
    }
    
    @Override
    public ReferenceFinder getReferenceFinder() {
        return new ProtobufReferenceFinder();
    }
}
