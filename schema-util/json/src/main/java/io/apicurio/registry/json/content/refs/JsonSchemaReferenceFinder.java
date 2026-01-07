package io.apicurio.registry.json.content.refs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinderException;
import io.apicurio.registry.content.refs.ReferenceFinder;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A JSON Schema implementation of a reference finder.
 */
public class JsonSchemaReferenceFinder implements ReferenceFinder {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * @see io.apicurio.registry.content.refs.ReferenceFinder#findExternalReferences(TypedContent)
     */
    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        try {
            JsonNode tree = mapper.readTree(content.getContent().content());
            Set<String> externalTypes = new HashSet<>();
            findExternalTypesIn(tree, externalTypes);

            return externalTypes.stream().map(type -> new JsonPointerExternalReference(type))
                    .filter(ref -> ref.getResource() != null).collect(Collectors.toSet());
        } catch (Exception e) {
            throw new ReferenceFinderException("Error finding external references in a JSON Schema file.", e);
        }
    }

    private static void findExternalTypesIn(JsonNode schema, Set<String> externalTypes) {
        if (schema.isObject()) {
            if (schema.has("$ref")) {
                String ref = schema.get("$ref").asText(null);
                if (ref != null) {
                    // TODO: the value of the ref should be resolved against the $id in this schema if it has
                    // one
                    externalTypes.add(ref);
                }
            }
            Iterator<Entry<String, JsonNode>> fields = schema.fields();
            while (fields.hasNext()) {
                Entry<String, JsonNode> field = fields.next();
                findExternalTypesIn(field.getValue(), externalTypes);
            }
        } else if (schema.isArray()) {
            schema.forEach(innerNode -> {
                findExternalTypesIn(innerNode, externalTypes);
            });
        }
    }

}
