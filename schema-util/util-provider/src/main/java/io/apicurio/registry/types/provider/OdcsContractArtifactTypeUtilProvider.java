package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.OdcsContractContentAccepter;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.YamlContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.dereference.NoopContentDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.NoopContentExtractor;
import io.apicurio.registry.content.extract.NoopStructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.refs.DefaultReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.OdcsContractReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.NoopCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.OdcsContractContentValidator;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;

import java.util.Set;

public class OdcsContractArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    @Override
    public String getArtifactType() {
        return ArtifactType.ODCS_CONTRACT;
    }

    @Override
    public Set<String> getContentTypes() {
        return Set.of(ContentTypes.APPLICATION_YAML);
    }

    @Override
    protected ContentAccepter createContentAccepter() {
        return new OdcsContractContentAccepter();
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return new NoopCompatibilityChecker();
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new YamlContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new OdcsContractContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return NoopContentExtractor.INSTANCE;
    }

    @Override
    protected ContentDereferencer createContentDereferencer() {
        return NoopContentDereferencer.INSTANCE;
    }

    @Override
    protected ReferenceFinder createReferenceFinder() {
        return new OdcsContractReferenceFinder();
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
        return NoopStructuredContentExtractor.INSTANCE;
    }
}
