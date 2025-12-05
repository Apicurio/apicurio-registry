package io.apicurio.registry.avro.content.refs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;
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
            String rootNamespace = extractNamespace(tree);
            findExternalTypesIn(tree, externalTypes, rootNamespace);
            return externalTypes.stream().map(type -> new ExternalReference(type))
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Error finding external references in an Avro file.", e);
            return Collections.emptySet();
        }
    }

    /**
     * Extracts the namespace from a schema node if present.
     *
     * @param schema The schema node
     * @return The namespace or null if not present
     */
    private static String extractNamespace(JsonNode schema) {
        if (schema != null && schema.isObject() && schema.has("namespace")
                && !schema.get("namespace").isNull()) {
            return schema.get("namespace").asText();
        }
        return null;
    }

    /**
     * Qualifies a type name with the given namespace if it's a relative name.
     * According to the Avro specification, a simple name (without dots) should be
     * qualified with the enclosing namespace.
     *
     * @param typeName The type name to qualify
     * @param namespace The current namespace context
     * @return The fully qualified type name
     */
    private static String qualifyTypeName(String typeName, String namespace) {
        // If the type name contains a dot, it's already fully qualified
        if (typeName.contains(".")) {
            return typeName;
        }
        // If we have a namespace, qualify the relative name
        if (namespace != null && !namespace.isEmpty()) {
            return namespace + "." + typeName;
        }
        // No namespace context, return as-is
        return typeName;
    }

    private static void findExternalTypesIn(JsonNode schema, Set<String> externalTypes, String namespace) {
        // Null check
        if (schema == null || schema.isNull()) {
            return;
        }

        // Handle primitive/external types
        if (schema.isTextual()) {
            String type = schema.asText();
            if (!PRIMITIVE_TYPES.contains(type)) {
                // Qualify the type name with the current namespace if it's a relative name
                String qualifiedType = qualifyTypeName(type, namespace);
                externalTypes.add(qualifiedType);
            }
        }

        // Handle unions
        else if (schema.isArray()) {
            ArrayNode schemas = (ArrayNode) schema;
            schemas.forEach(s -> findExternalTypesIn(s, externalTypes, namespace));
        }

        // Handle records
        else if (schema.isObject() && schema.has("type") && schema.get("type").isTextual()) {
            String type = schema.get("type").asText();
            switch (type) {
                case "record":
                {
                    // Records can define their own namespace, which becomes the enclosing namespace
                    // for nested types according to Avro specification
                    String recordNamespace = extractNamespace(schema);
                    String effectiveNamespace = recordNamespace != null ? recordNamespace : namespace;

                    JsonNode fieldsNode = schema.get("fields");
                    if (fieldsNode != null && fieldsNode.isArray()) {
                        ArrayNode fields = (ArrayNode) fieldsNode;
                        fields.forEach(fieldNode -> {
                            findExternalTypesIn(fieldNode, externalTypes, effectiveNamespace);
                        });
                    }

                    return;
                }
                case "array":
                {
                    JsonNode items = schema.get("items");
                    findExternalTypesIn(items, externalTypes, namespace);
                    return;
                }
                case "map":
                {
                    JsonNode values = schema.get("values");
                    findExternalTypesIn(values, externalTypes, namespace);
                    return;
                }
                case "enum":
                {
                    // Do nothing for enums
                    return;
                }
                default:
                {
                    findExternalTypesIn(schema.get("type"), externalTypes, namespace);
                }
            }
        }

        // If the schema has a "type" property that is not textual, process the type (object or array)
        else if (schema.isObject() && schema.has("type") && !schema.get("type").isTextual()) {
            findExternalTypesIn(schema.get("type"), externalTypes, namespace);
        }
    }

}
