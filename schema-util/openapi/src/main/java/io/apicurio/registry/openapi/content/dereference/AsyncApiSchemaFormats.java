package io.apicurio.registry.openapi.content.dereference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * AsyncAPI 3.x Multi-Format Schema Object {@code schemaFormat} values for the schema types the registry
 * inlines when dereferencing, and the logic that keeps them in the versioned form the AsyncAPI parsers
 * register. A versionless value such as {@code application/vnd.apache.avro+json} is rejected by those
 * parsers as an unknown schema format.
 */
final class AsyncApiSchemaFormats {

    /** Registered Avro schemaFormat used when the document does not declare a versioned one. */
    static final String AVRO = "application/vnd.apache.avro+json;version=1.9.0";

    /** Registered schemaFormat for proto2 schemas. */
    static final String PROTOBUF_2 = "application/vnd.google.protobuf;version=2";

    /** Registered schemaFormat for proto3 schemas. */
    static final String PROTOBUF_3 = "application/vnd.google.protobuf;version=3";

    private static final String COMPONENT_SCHEMA_REF_PREFIX = "#/components/schemas/";

    private static final Pattern VERSIONED_AVRO = Pattern.compile(
            "application/vnd\\.apache\\.avro(\\+json|\\+yaml)?\\s*;\\s*version\\s*=\\s*[^;\\s]+\\s*",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern VERSIONLESS_AVRO = Pattern
            .compile("application/vnd\\.apache\\.avro(\\+json|\\+yaml)?\\s*", Pattern.CASE_INSENSITIVE);

    private static final Pattern VERSIONED_PROTOBUF = Pattern.compile(
            "application/vnd\\.google\\.protobuf\\s*;\\s*version\\s*=\\s*[^;\\s]+\\s*",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern VERSIONLESS_PROTOBUF = Pattern
            .compile("application/vnd\\.google\\.protobuf\\s*", Pattern.CASE_INSENSITIVE);

    private AsyncApiSchemaFormats() {
    }

    /**
     * Returns whether the value is a registered Avro media type with a non-empty version parameter.
     *
     * @param schemaFormat the schemaFormat value, may be null
     * @return true if the value is a versioned Avro schemaFormat
     */
    static boolean isVersionedAvro(String schemaFormat) {
        return schemaFormat != null && VERSIONED_AVRO.matcher(schemaFormat).matches();
    }

    /**
     * Returns whether the value is a Protobuf media type with a non-empty version parameter.
     *
     * @param schemaFormat the schemaFormat value, may be null
     * @return true if the value is a versioned Protobuf schemaFormat
     */
    static boolean isVersionedProtobuf(String schemaFormat) {
        return schemaFormat != null && VERSIONED_PROTOBUF.matcher(schemaFormat).matches();
    }

    /**
     * Rewrites versionless Avro and Protobuf schemaFormat values in a dereferenced AsyncAPI 3.x document.
     * A Multi-Format Schema Object whose {@code schema} points at an inlined
     * {@code #/components/schemas/...} entry takes that entry's versioned schemaFormat, provided both are
     * of the same format family. Values of other families, and wrappers that do not point at a versioned
     * component, are left untouched.
     *
     * @param document the dereferenced document, modified in place
     * @return true if any value was changed
     */
    static boolean normalizeVersionless(ObjectNode document) {
        JsonNode version = document.get("asyncapi");
        if (version == null || !version.asText().startsWith("3.")) {
            return false;
        }
        Map<String, String> componentFormats = new HashMap<>();
        JsonNode schemas = document.path("components").path("schemas");
        Iterator<Map.Entry<String, JsonNode>> entries = schemas.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            String format = entry.getValue().path("schemaFormat").asText(null);
            if (isVersionedAvro(format) || isVersionedProtobuf(format)) {
                componentFormats.put(COMPONENT_SCHEMA_REF_PREFIX + entry.getKey(), format);
            }
        }
        return !componentFormats.isEmpty() && normalizeNode(document, componentFormats);
    }

    private static boolean normalizeNode(JsonNode node, Map<String, String> componentFormats) {
        boolean changed = false;
        if (node.isObject()) {
            String declared = node.path("schemaFormat").asText(null);
            String ref = node.path("schema").path("$ref").asText(null);
            String target = ref != null ? componentFormats.get(ref) : null;
            if (declared != null && target != null && sameFamilyVersionless(declared, target)) {
                ((ObjectNode) node).put("schemaFormat", target);
                changed = true;
            }
        }
        for (JsonNode child : node) {
            changed |= normalizeNode(child, componentFormats);
        }
        return changed;
    }

    private static boolean sameFamilyVersionless(String declared, String target) {
        return (VERSIONLESS_AVRO.matcher(declared).matches() && isVersionedAvro(target))
                || (VERSIONLESS_PROTOBUF.matcher(declared).matches() && isVersionedProtobuf(target));
    }
}
