package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.ModelSchemaContentAccepter;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.json.content.canon.JsonContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.dereference.ModelSchemaDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.ModelSchemaContentExtractor;
import io.apicurio.registry.content.extract.ModelSchemaStructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.refs.DefaultReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.ModelSchemaReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.ModelSchemaCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.ModelSchemaContentValidator;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;

import java.util.Set;

/**
 * Artifact type utility provider for AI/ML Model Schema artifacts.
 */
public class ModelSchemaArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    @Override
    public String getArtifactType() {
        return ArtifactType.MODEL_SCHEMA;
    }

    @Override
    public Set<String> getContentTypes() {
        return Set.of(ContentTypes.APPLICATION_JSON, ContentTypes.APPLICATION_YAML);
    }

    @Override
    protected ContentAccepter createContentAccepter() {
        return new ModelSchemaContentAccepter();
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return new ModelSchemaCompatibilityChecker();
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new JsonContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new ModelSchemaContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return new ModelSchemaContentExtractor();
    }

    @Override
    protected ContentDereferencer createContentDereferencer() {
        return new ModelSchemaDereferencer();
    }

    @Override
    protected ReferenceFinder createReferenceFinder() {
        return new ModelSchemaReferenceFinder();
    }

    @Override
    public boolean supportsReferencesWithContext() {
        return true;
    }

    @Override
    protected ReferenceArtifactIdentifierExtractor createReferenceArtifactIdentifierExtractor() {
        return new DefaultReferenceArtifactIdentifierExtractor();
    }

    @Override
    protected StructuredContentExtractor createStructuredContentExtractor() {
        return new ModelSchemaStructuredContentExtractor();
    }
}
