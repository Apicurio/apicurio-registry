package io.apicurio.registry.content.refs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.apicurio.registry.content.TypedContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An Apache Avro implementation of a reference finder.
 */
public class AvroReferenceFinder implements ReferenceFinder {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(AvroReferenceFinder.class);

    private static final Set<String> PRIMITIVE_TYPES = Set.of("null", "boolean", "int", "long", "float",
            "double", "bytes", "string");

    /**
     * @see io.apicurio.registry.content.refs.ReferenceFinder#findExternalReferences(TypedContent)
     */
    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        try {
            JsonNode tree = mapper.readTree(content.getContent().content());
            Set<String> externalTypes = new HashSet<>();
            findExternalTypesIn(tree, externalTypes);
            return externalTypes.stream().map(type -> new ExternalReference(type))
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Error finding external references in an Avro file.", e);
            return Collections.emptySet();
        }
    }

    private static void findExternalTypesIn(JsonNode schema, Set<String> externalTypes) {
        // Null check
        if (schema == null || schema.isNull()) {
            return;
        }

        // Handle primitive/external types
        if (schema.isTextual()) {
            String type = schema.asText();
            if (!PRIMITIVE_TYPES.contains(type)) {
                externalTypes.add(type);
            }
        }

        // Handle unions
        if (schema.isArray()) {
            ArrayNode schemas = (ArrayNode) schema;
            schemas.forEach(s -> findExternalTypesIn(s, externalTypes));
        }

        // Handle records
        if (schema.isObject() && schema.has("type") && !schema.get("type").isNull()
                && schema.get("type").asText().equals("record")) {
            JsonNode fieldsNode = schema.get("fields");
            if (fieldsNode != null && fieldsNode.isArray()) {
                ArrayNode fields = (ArrayNode) fieldsNode;
                fields.forEach(fieldNode -> {
                    if (fieldNode.isObject()) {
                        JsonNode typeNode = fieldNode.get("type");
                        findExternalTypesIn(typeNode, externalTypes);
                    }
                });
            }
        }
        // Handle arrays
        if (schema.has("type") && !schema.get("type").isNull()
                && schema.get("type").asText().equals("array")) {
            JsonNode items = schema.get("items");
            findExternalTypesIn(items, externalTypes);
        }
        // Handle maps
        if (schema.has("type") && !schema.get("type").isNull() && schema.get("type").asText().equals("map")) {
            JsonNode values = schema.get("values");
            findExternalTypesIn(values, externalTypes);
        }
    }

}
