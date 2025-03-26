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

    public static final AvroContentAccepter contentAccepter = new AvroContentAccepter();
    public static final AvroCompatibilityChecker compatibilityChecker = new AvroCompatibilityChecker();
    public static final EnhancedAvroContentCanonicalizer contentCanonicalizer = new EnhancedAvroContentCanonicalizer();
    public static final AvroContentValidator contentValidator = new AvroContentValidator();
    public static final AvroContentExtractor contentExtractor = new AvroContentExtractor();
    public static final AvroDereferencer dereferencer = new AvroDereferencer();
    public static final AvroReferenceFinder referenceFinder = new AvroReferenceFinder();

    @Override
    public String getArtifactType() {
        return ArtifactType.AVRO;
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
        return false;
    }

}
