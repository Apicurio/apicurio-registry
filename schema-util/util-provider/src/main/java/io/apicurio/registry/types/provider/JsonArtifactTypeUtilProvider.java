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

public class JsonArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    public static final JsonSchemaContentAccepter contentAccepter = new JsonSchemaContentAccepter();
    public static final JsonSchemaCompatibilityChecker compatibilityChecker = new JsonSchemaCompatibilityChecker();
    public static final JsonContentCanonicalizer contentCanonicalizer = new JsonContentCanonicalizer();
    public static final JsonSchemaContentValidator contentValidator = new JsonSchemaContentValidator();
    public static final JsonContentExtractor contentExtractor = new JsonContentExtractor();
    public static final JsonSchemaDereferencer dereferencer = new JsonSchemaDereferencer();
    public static final JsonSchemaReferenceFinder referenceFinder = new JsonSchemaReferenceFinder();

    @Override
    public String getArtifactType() {
        return ArtifactType.JSON;
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
        return true;
    }

}
