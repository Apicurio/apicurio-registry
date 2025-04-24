package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.JsonSchemaContentAccepter;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.JsonContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.dereference.JsonSchemaDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.JsonContentExtractor;
import io.apicurio.registry.content.refs.JsonSchemaReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.JsonSchemaCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.JsonSchemaContentValidator;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;

import java.util.Set;

public class JsonArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    @Override
    public String getArtifactType() {
        return ArtifactType.JSON;
    }

    @Override
    public Set<String> getContentTypes() {
        return Set.of(ContentTypes.APPLICATION_JSON);
    }

    @Override
    public ContentAccepter createContentAccepter() {
        return new JsonSchemaContentAccepter();
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return new JsonSchemaCompatibilityChecker();
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new JsonContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new JsonSchemaContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return new JsonContentExtractor();
    }

    @Override
    public ContentDereferencer createContentDereferencer() {
        return new JsonSchemaDereferencer();
    }

    @Override
    public ReferenceFinder createReferenceFinder() {
        return new JsonSchemaReferenceFinder();
    }

    @Override
    public boolean supportsReferencesWithContext() {
        return true;
    }

}
