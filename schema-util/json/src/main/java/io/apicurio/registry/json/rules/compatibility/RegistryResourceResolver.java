package io.apicurio.registry.json.rules.compatibility;

import io.apicurio.registry.content.TypedContent;
import io.apitomy.datamodels.Library;
import io.apitomy.datamodels.jsonschema.ref.RefResolutionContext;
import io.apitomy.datamodels.jsonschema.ref.ResourceResolver;
import io.apitomy.datamodels.models.jsonschema.JsonSchema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves external JSON Schema documents from the Registry's
 * pre-resolved reference map ({@code Map<String, TypedContent>}).
 * <p>
 * Each document is parsed once and the same instance returned for every later lookup, as
 * {@link ResourceResolver} requires: the dereferencer recognises a cycle between referenced
 * artifacts by the identity of the documents it is already inside, and a fresh parse per lookup
 * would recurse until its depth limit instead.
 */
public class RegistryResourceResolver implements ResourceResolver {

    private static final Logger log = LoggerFactory.getLogger(RegistryResourceResolver.class);

    private final Map<String, TypedContent> resolvedReferences;

    private final Map<String, Optional<JsonSchema>> parsed = new HashMap<>();

    public RegistryResourceResolver(Map<String, TypedContent> resolvedReferences) {
        this.resolvedReferences = resolvedReferences != null ? resolvedReferences : Map.of();
    }

    @Override
    public Optional<JsonSchema> resolveResource(String resource, RefResolutionContext context) {
        return parsed.computeIfAbsent(resource, this::parse);
    }

    private Optional<JsonSchema> parse(String resource) {
        var content = resolvedReferences.get(resource);
        if (content == null) {
            return Optional.empty();
        }

        try {
            // readDocumentFromJSONString is deprecated in Data Models 4.0 and throws for JSON
            // Schema, whose root is a schema rather than a Document. readRootFromJSONString is
            // the replacement that works for every model type. A referenced schema may also be
            // the literal true or false, which is a JsonSchema but not a full schema.
            var root = Library.readRootFromJSONString(content.getContent().content());
            if (root instanceof JsonSchema schema) {
                return Optional.of(schema);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.debug("Failed to parse referenced schema '{}': {}", resource, e.getMessage());
            return Optional.empty();
        }
    }
}
