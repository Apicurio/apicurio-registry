package io.apicurio.registry.content.refs;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reference finder for AI/ML Model Schema artifacts.
 *
 * Recursively traverses the document to find all external {@code $ref} references.
 */
public class ModelSchemaReferenceFinder implements ReferenceFinder {

    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        try {
            JsonNode tree = ContentTypeUtil.parseJsonOrYaml(content);
            Set<String> externalRefs = new HashSet<>();
            findRefsRecursive(tree, externalRefs);

            return externalRefs.stream()
                    .map(JsonPointerExternalReference::new)
                    .filter(ref -> ref.getResource() != null)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            throw new ReferenceFinderException("Error finding external references in a Model Schema file.", e);
        }
    }

    private static void findRefsRecursive(JsonNode node, Set<String> refs) {
        if (node.isObject()) {
            if (node.has("$ref")) {
                String ref = node.get("$ref").asText(null);
                if (ref != null && !ref.startsWith("#")) {
                    refs.add(ref);
                }
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                findRefsRecursive(fields.next().getValue(), refs);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                findRefsRecursive(element, refs);
            }
        }
    }
}
