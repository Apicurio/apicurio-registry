package io.apicurio.registry.types.provider;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.JsonContentCanonicalizer;
import io.apicurio.registry.content.dereference.AsyncApiDereferencer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.JsonContentExtractor;
import io.apicurio.registry.content.refs.JsonSchemaReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.JsonSchemaCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.JsonSchemaContentValidator;
import io.apicurio.registry.types.ArtifactType;

import java.util.Map;

public class JsonArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            if (content.getContentType() != null && content.getContentType().toLowerCase().contains("json")
                    && !ContentTypeUtil.isParsableJson(content.getContent())) {
                return false;
            }
            JsonNode tree = ContentTypeUtil.parseJson(content.getContent());
            if (tree.has("$schema") && tree.get("$schema").asText().contains("json-schema.org")
                    || tree.has("properties")) {
                return true;
            }
        } catch (Exception e) {
            // Error - invalid syntax
        }
        return false;
    }

    @Override
    public String getArtifactType() {
        return ArtifactType.JSON;
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
    public ContentDereferencer getContentDereferencer() {
        return new AsyncApiDereferencer();
    }

    @Override
    public ReferenceFinder getReferenceFinder() {
        return new JsonSchemaReferenceFinder();
    }
}
