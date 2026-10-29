package io.apicurio.registry.content.dereference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;

import java.util.Locale;
import java.util.Map;

/**
 * Reference handling for ODCS (Open Data Contract Standard) contracts. ODCS contracts reference
 * external schemas via the <code>schemas[].location</code> field (see
 * {@link io.apicurio.registry.content.refs.OdcsContractReferenceFinder}). Both DEREFERENCE and
 * REWRITE are supported: DEREFERENCE inlines the resolved schema content directly into the
 * {@code schemas[]} entry (under a new {@value #SCHEMA_DEFINITION_FIELD} field), while REWRITE
 * replaces the {@code location} value with a Registry API URL.
 */
public class OdcsContractDereferencer implements ContentDereferencer {

    /**
     * Field name used to hold inlined schema content on DEREFERENCE. Namespaced with an
     * {@code x-apicurio-} prefix (the conventional vendor-extension prefix) to avoid colliding with
     * user-supplied properties, since ODCS schema entries permit arbitrary additional properties.
     */
    private static final String SCHEMA_DEFINITION_FIELD = "x-apicurio-schemaDefinition";

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * Inlines the resolved content of every reference whose name matches a {@code schemas[].location}
     * value into that schema entry, under a new {@value #SCHEMA_DEFINITION_FIELD} field. The
     * {@code location} field is left in place (for traceability) alongside the inlined content. A
     * schema entry that already defines {@value #SCHEMA_DEFINITION_FIELD} (e.g. a user-supplied
     * value) is left untouched rather than overwritten. Returns the original content if the document
     * cannot be parsed or no references match.
     *
     * @see ContentDereferencer#dereference(TypedContent, Map)
     */
    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        if (resolvedReferences == null || resolvedReferences.isEmpty()) {
            return content;
        }
        try {
            JsonNode tree = YAML_MAPPER.readTree(content.getContent().content());
            JsonNode schemas = tree == null ? null : tree.get("schemas");
            if (schemas == null || !schemas.isArray()) {
                return content;
            }
            boolean changed = false;
            for (JsonNode schema : schemas) {
                if (!schema.isObject()) {
                    continue;
                }
                if (schema.has(SCHEMA_DEFINITION_FIELD)) {
                    // Do not clobber an existing (likely user-supplied) value.
                    continue;
                }
                JsonNode location = schema.get("location");
                if (location == null || !location.isTextual()) {
                    continue;
                }
                TypedContent resolved = resolvedReferences.get(location.asText());
                if (resolved == null) {
                    continue;
                }
                JsonNode inlined = parseResolvedContent(resolved);
                ((ObjectNode) schema).set(SCHEMA_DEFINITION_FIELD, inlined);
                changed = true;
            }
            if (!changed) {
                return content;
            }
            String rewritten = writeTree(tree, content.getContentType());
            return TypedContent.create(ContentHandle.create(rewritten), content.getContentType());
        } catch (Exception e) {
            return content;
        }
    }

    /**
     * Rewrites {@code schemas[].location} values that match a resolved reference name to the given
     * Registry API URL. Returns the original content when nothing changes.
     *
     * @see ContentDereferencer#rewriteReferences(TypedContent, Map)
     */
    @Override
    public TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls) {
        if (resolvedReferenceUrls == null || resolvedReferenceUrls.isEmpty()) {
            return content;
        }
        try {
            JsonNode tree = YAML_MAPPER.readTree(content.getContent().content());
            JsonNode schemas = tree == null ? null : tree.get("schemas");
            if (schemas == null || !schemas.isArray()) {
                return content;
            }
            boolean changed = false;
            for (JsonNode schema : schemas) {
                if (!schema.isObject()) {
                    continue;
                }
                JsonNode location = schema.get("location");
                if (location == null || !location.isTextual()) {
                    continue;
                }
                String url = resolvedReferenceUrls.get(location.asText());
                if (url == null) {
                    continue;
                }
                ((ObjectNode) schema).set("location", new TextNode(url));
                changed = true;
            }
            if (!changed) {
                return content;
            }
            String rewritten = writeTree(tree, content.getContentType());
            return TypedContent.create(ContentHandle.create(rewritten), content.getContentType());
        } catch (Exception e) {
            return content;
        }
    }

    /**
     * Parses the resolved reference content so it can be embedded as structured YAML/JSON. Falls back
     * to a plain text node when the content is not parseable (e.g. Avro IDL, Protobuf).
     */
    private JsonNode parseResolvedContent(TypedContent resolved) {
        String raw = resolved.getContent().content();
        String ct = resolved.getContentType();
        try {
            if (isYaml(ct)) {
                return YAML_MAPPER.readTree(raw);
            }
            return JSON_MAPPER.readTree(raw);
        } catch (Exception e) {
            return new TextNode(raw);
        }
    }

    /**
     * Serializes the given tree back to a string, matching the serialization format (JSON vs YAML) of
     * the given content type, so the output remains parseable as that same content type. ODCS content
     * is normally YAML, but this guards against future/contributed JSON support for the type.
     */
    private String writeTree(JsonNode tree, String contentType) throws Exception {
        if (isYaml(contentType)) {
            return YAML_MAPPER.writeValueAsString(tree);
        }
        return JSON_MAPPER.writeValueAsString(tree);
    }

    private boolean isYaml(String contentType) {
        if (contentType == null) {
            return true;
        }
        if (contentType.contains(ContentTypes.APPLICATION_YAML)) {
            return true;
        }
        if (contentType.contains(ContentTypes.APPLICATION_JSON)) {
            return false;
        }
        return contentType.toLowerCase(Locale.ROOT).contains("yaml");
    }
}
