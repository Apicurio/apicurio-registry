package io.apicurio.registry.types.provider;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.AsyncApiContentCanonicalizer;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.dereference.AsyncApiDereferencer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.extract.AsyncApiContentExtractor;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.refs.AsyncApiReferenceFinder;
import io.apicurio.registry.content.refs.DefaultReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.ReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.NoopCompatibilityChecker;
import io.apicurio.registry.rules.validity.AsyncApiContentValidator;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.types.ArtifactType;

import java.util.Map;

public class AsyncApiArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

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
            if (tree.has("asyncapi")) {
                return true;
            }
        } catch (Exception e) {
            // Error - invalid syntax
        }
        return false;
    }

    @Override
    public String getArtifactType() {
        return ArtifactType.ASYNCAPI;
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return NoopCompatibilityChecker.INSTANCE;
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new AsyncApiContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new AsyncApiContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return new AsyncApiContentExtractor();
    }

    @Override
    protected ReferenceArtifactIdentifierExtractor createReferenceArtifactIdentifierExtractor() {
        return new DefaultReferenceArtifactIdentifierExtractor();
    }

    @Override
    public ContentDereferencer getContentDereferencer() {
        return new AsyncApiDereferencer();
    }

    @Override
    public ReferenceFinder getReferenceFinder() {
        return new AsyncApiReferenceFinder();
    }

    @Override
    public boolean supportsReferencesWithContext() {
        return true;
    }
}
