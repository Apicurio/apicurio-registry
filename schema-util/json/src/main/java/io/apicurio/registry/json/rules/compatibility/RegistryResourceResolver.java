package io.apicurio.registry.json.rules.compatibility;

import io.apicurio.registry.content.TypedContent;
import io.apitomy.datamodels.Library;
import io.apitomy.datamodels.jsonschema.ref.RefResolutionContext;
import io.apitomy.datamodels.jsonschema.ref.ResourceResolver;
import io.apitomy.datamodels.models.Node;
import io.apitomy.datamodels.models.jsonschema.JsonSchemaDocument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves external JSON Schema documents from the Registry's
 * pre-resolved reference map ({@code Map<String, TypedContent>}).
 */
public class RegistryResourceResolver implements ResourceResolver {

    private static final Logger log = LoggerFactory.getLogger(RegistryResourceResolver.class);

    private final Map<String, TypedContent> resolvedReferences;

    public RegistryResourceResolver(Map<String, TypedContent> resolvedReferences) {
        this.resolvedReferences = resolvedReferences != null ? resolvedReferences : Map.of();
    }

    @Override
    public Optional<Node> resolveResource(String resource, RefResolutionContext context) {
        var content = resolvedReferences.get(resource);
        if (content == null) {
            return Optional.empty();
        }

        try {
            var doc = Library.readDocumentFromJSONString(content.getContent().content());
            if (doc instanceof JsonSchemaDocument) {
                return Optional.of(doc);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.debug("Failed to parse referenced schema '{}': {}", resource, e.getMessage());
            return Optional.empty();
        }
    }
}
