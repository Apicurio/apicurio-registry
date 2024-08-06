package io.apicurio.registry.content.refs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
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
    private static final Logger log = LoggerFactory.getLogger(JsonSchemaReferenceFinder.class);

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
            log.error("Error finding external references in an Avro file.", e);
            return Collections.emptySet();
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
        }
    }

}
