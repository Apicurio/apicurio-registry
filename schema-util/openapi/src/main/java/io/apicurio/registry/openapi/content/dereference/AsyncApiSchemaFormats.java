package io.apicurio.registry.openapi.content.dereference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AsyncAPI {@code schemaFormat} values (on 3.x Multi-Format Schema Objects and 2.x Message Objects) for
 * the schema types the registry inlines when dereferencing, and the logic that keeps them in the versioned form the AsyncAPI parsers
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

    /** Name of the property holding the schema format, on 3.x Multi-Format Schema and 2.x Message Objects. */
    static final String SCHEMA_FORMAT = "schemaFormat";

    private static final String COMPONENT_SCHEMA_REF_PREFIX = "#/components/schemas/";

    // The syntax or edition declaration, which must be the first statement of a .proto file
    private static final Pattern PROTO_DECLARATION = Pattern
            .compile("(?:syntax\\s*=\\s*[\"']proto([23])[\"']|edition\\s*=\\s*[\"'][^\"']*[\"'])\\s*;");

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
     * Returns whether the value is a media type of the Avro family (versioned or not).
     *
     * @param schemaFormat the schemaFormat value, may be null
     * @return true if the value is an Avro schemaFormat
     */
    static boolean isAvro(String schemaFormat) {
        return isVersionedAvro(schemaFormat)
                || (schemaFormat != null && VERSIONLESS_AVRO.matcher(schemaFormat).matches());
    }

    /**
     * Returns whether the value is a media type of the Protobuf family (versioned or not).
     *
     * @param schemaFormat the schemaFormat value, may be null
     * @return true if the value is a Protobuf schemaFormat
     */
    static boolean isProtobuf(String schemaFormat) {
        return isVersionedProtobuf(schemaFormat)
                || (schemaFormat != null && VERSIONLESS_PROTOBUF.matcher(schemaFormat).matches());
    }

    /**
     * Returns the schemaFormat to use for an inlined Avro schema: the declared value if it is a versioned
     * Avro format, otherwise the registered default.
     *
     * @param declared the schemaFormat declared by the document, may be null
     * @return a versioned Avro schemaFormat
     */
    static String forAvro(String declared) {
        return isVersionedAvro(declared) ? declared : AVRO;
    }

    /**
     * Returns the schemaFormat to use for an inlined Protobuf schema: the declared value if it is a
     * versioned Protobuf format, otherwise the version inferred from the schema.
     *
     * @param declared the schemaFormat declared by the document, may be null
     * @param protoContent the Protobuf schema
     * @return a versioned Protobuf schemaFormat
     */
    static String forProtobuf(String declared, String protoContent) {
        return isVersionedProtobuf(declared) ? declared : detectProtobuf(protoContent);
    }

    /**
     * Detects the Protobuf schemaFormat from the file's syntax or edition declaration. Only the first
     * statement of the file (after leading whitespace and comments) is considered, since that is the only
     * place the declaration may appear; this avoids matching similar text in comments or string literals.
     * A file with no declaration is proto2 (the language default). Editions map to proto3, the newest
     * version AsyncAPI registers.
     *
     * @param protoContent the Protobuf schema
     * @return the schemaFormat matching the schema's language version
     */
    static String detectProtobuf(String protoContent) {
        int start = skipLeadingTrivia(protoContent);
        Matcher declaration = PROTO_DECLARATION.matcher(protoContent).region(start, protoContent.length());
        if (!declaration.lookingAt()) {
            return PROTOBUF_2;
        }
        // group(1) is null for an edition declaration
        return "2".equals(declaration.group(1)) ? PROTOBUF_2 : PROTOBUF_3;
    }

    /**
     * Returns the index of the first character that is not whitespace or part of a comment.
     *
     * @param text the text to scan
     * @return the index of the first significant character, or the text length if there is none
     */
    private static int skipLeadingTrivia(String text) {
        int i = 0;
        int length = text.length();
        while (i < length) {
            if (Character.isWhitespace(text.charAt(i))) {
                i++;
            } else if (text.startsWith("//", i)) {
                int end = text.indexOf('\n', i);
                i = end < 0 ? length : end + 1;
            } else if (text.startsWith("/*", i)) {
                int end = text.indexOf("*/", i + 2);
                i = end < 0 ? length : end + 2;
            } else {
                break;
            }
        }
        return i;
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
            String format = entry.getValue().path(SCHEMA_FORMAT).asText(null);
            if (isVersionedAvro(format) || isVersionedProtobuf(format)) {
                componentFormats.put(COMPONENT_SCHEMA_REF_PREFIX + entry.getKey(), format);
            }
        }
        return !componentFormats.isEmpty() && normalizeNode(document, componentFormats);
    }

    private static boolean normalizeNode(JsonNode node, Map<String, String> componentFormats) {
        boolean changed = false;
        if (node.isObject()) {
            String declared = node.path(SCHEMA_FORMAT).asText(null);
            String ref = node.path("schema").path("$ref").asText(null);
            String target = ref != null ? componentFormats.get(ref) : null;
            if (declared != null && target != null && sameFamilyVersionless(declared, target)) {
                ((ObjectNode) node).put(SCHEMA_FORMAT, target);
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
