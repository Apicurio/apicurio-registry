package io.apicurio.registry.types.provider;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.OpenApiContentCanonicalizer;
import io.apicurio.registry.content.dereference.AsyncApiDereferencer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.OpenApiContentExtractor;
import io.apicurio.registry.content.refs.OpenApiReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.NoopCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.OpenApiContentValidator;
import io.apicurio.registry.types.ArtifactType;

import java.util.Map;

public class OpenApiArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            String contentType = content.getContentType();
            JsonNode tree = null;
            // If the content is YAML, then convert it to JSON first (the data-models library only accepts
            // JSON).
            if (contentType.toLowerCase().contains("yml") || contentType.toLowerCase().contains("yaml")) {
                tree = ContentTypeUtil.parseYaml(content.getContent());
            } else {
                tree = ContentTypeUtil.parseJson(content.getContent());
            }
            if (tree.has("openapi") || tree.has("swagger")) {
                return true;
            }
        } catch (Exception e) {
            // Error - invalid syntax
        }
        return false;
    }

    @Override
    public String getArtifactType() {
        return ArtifactType.OPENAPI;
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return NoopCompatibilityChecker.INSTANCE;
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new OpenApiContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new OpenApiContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return new OpenApiContentExtractor();
    }

    @Override
    public ContentDereferencer getContentDereferencer() {
        return new AsyncApiDereferencer();
    }

    @Override
    public ReferenceFinder getReferenceFinder() {
        return new OpenApiReferenceFinder();
    }
}
