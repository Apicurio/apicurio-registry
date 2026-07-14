package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.dereference.NoopContentDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.NoopContentExtractor;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.refs.DefaultReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.NoOpReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.NoopCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.thrift.content.ThriftContentAccepter;
import io.apicurio.registry.thrift.content.canon.ThriftContentCanonicalizer;
import io.apicurio.registry.thrift.content.extract.ThriftStructuredContentExtractor;
import io.apicurio.registry.thrift.rules.validity.ThriftContentValidator;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;

import java.util.Set;

public class ThriftArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    @Override
    public String getArtifactType() {
        return ArtifactType.THRIFT;
    }

    @Override
    public Set<String> getContentTypes() {
        return Set.of(ContentTypes.APPLICATION_THRIFT);
    }

    @Override
    public ContentAccepter createContentAccepter() {
        return new ThriftContentAccepter();
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return NoopCompatibilityChecker.INSTANCE;
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new ThriftContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new ThriftContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return NoopContentExtractor.INSTANCE;
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

    @Override
    protected ReferenceArtifactIdentifierExtractor createReferenceArtifactIdentifierExtractor() {
        return new DefaultReferenceArtifactIdentifierExtractor();
    }

    @Override
    protected StructuredContentExtractor createStructuredContentExtractor() {
        return new ThriftStructuredContentExtractor();
    }

}
