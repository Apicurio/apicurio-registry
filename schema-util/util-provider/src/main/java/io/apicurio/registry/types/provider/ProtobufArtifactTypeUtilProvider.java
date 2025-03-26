package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.ProtobufContentAccepter;
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

    public static final ProtobufContentAccepter contentAccepter = new ProtobufContentAccepter();
    public static final ProtobufCompatibilityChecker compatibilityChecker = new ProtobufCompatibilityChecker();
    public static final ProtobufContentCanonicalizer contentCanonicalizer = new ProtobufContentCanonicalizer();
    public static final ProtobufContentValidator contentValidator = new ProtobufContentValidator();
    public static final ProtobufDereferencer dereferencer = new ProtobufDereferencer();
    public static final ProtobufReferenceFinder referenceFinder = new ProtobufReferenceFinder();

    @Override
    public String getArtifactType() {
        return ArtifactType.PROTOBUF;
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
        return NoopContentExtractor.INSTANCE;
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
        return false;
    }

}
