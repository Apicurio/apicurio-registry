package io.apicurio.registry.json.content.refs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.SpecVersion.VersionFlag;
import io.apicurio.registry.json.rules.validity.JsonSchemaDocumentValidator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Finds every {@code $ref} in a JSON Schema together with the absolute URI it resolves to, following the
 * base URI scoping rules of the schema's draft ({@code id} in draft 4, {@code $id} later).
 * <p>
 * Registry reference names are the literal {@code $ref} values, so callers keep using {@link RefSite#ref()}
 * as the reference name. {@link RefSite#resolved()} lets callers additionally match references that were
 * registered under the {@code $id}-resolved URI, and tell apart identical relative references in different
 * {@code $id} scopes.
 */
public final class JsonSchemaRefScopes {

    private static final String REF = "$ref";

    /** Keywords whose value is a map from names to subschemas, so their keys are not keywords. */
    private static final Set<String> SCHEMA_MAP_KEYWORDS = Set.of("properties", "patternProperties",
            "definitions", "$defs", "dependentSchemas", "dependencies");

    private static final JsonSchemaDocumentValidator DRAFT_DETECTOR = JsonSchemaDocumentValidator.builder()
            .draftDetector(JsonSchemaDocumentValidator.dataModelsDraftDetector())
            .treatUnrecognisedDraftAs(VersionFlag.V7).build();

    /**
     * A {@code $ref} occurrence in a schema.
     *
     * @param node the object holding the {@code $ref}
     * @param ref the literal {@code $ref} value
     * @param resolved the {@code $ref} resolved against the enclosing base URI, or null when there is no base
     *         URI, the value is a fragment-only (internal) reference, the value is not a valid URI reference,
     *         or resolution does not change it
     */
    public record RefSite(ObjectNode node, String ref, String resolved) {
    }

    private JsonSchemaRefScopes() {
    }

    /**
     * Finds all textual {@code $ref} values in the schema, in document order.
     *
     * @param schema the root of the JSON Schema
     * @return the {@code $ref} occurrences
     */
    public static List<RefSite> find(JsonNode schema) {
        if (schema == null) {
            return Collections.emptyList();
        }
        VersionFlag draft = DRAFT_DETECTOR.draftOf(schema).orElse(VersionFlag.V7);
        Walker walker = new Walker(draft);
        walker.walk(schema, null, true);
        return walker.sites;
    }

    private static final class Walker {

        private final String idKeyword;
        private final boolean refOverridesSiblings;
        private final List<RefSite> sites = new ArrayList<>();

        private Walker(VersionFlag draft) {
            this.idKeyword = draft == VersionFlag.V4 ? "id" : "$id";
            // Up to draft 7, keywords next to $ref (including the id) are ignored
            this.refOverridesSiblings = draft == VersionFlag.V4 || draft == VersionFlag.V6
                    || draft == VersionFlag.V7;
        }

        /**
         * @param node the node to visit
         * @param base the base URI in scope, or null
         * @param isSchema whether the node is a schema (as opposed to a map of names or instance data)
         */
        private void walk(JsonNode node, URI base, boolean isSchema) {
            if (node.isArray()) {
                for (JsonNode item : node) {
                    walk(item, base, isSchema);
                }
                return;
            }
            if (!node.isObject()) {
                return;
            }
            JsonNode refNode = node.get(REF);
            boolean hasRef = refNode != null && refNode.isTextual();
            URI scope = base;
            if (isSchema && !(hasRef && refOverridesSiblings)) {
                scope = withId(base, node.get(idKeyword));
            }
            if (hasRef) {
                String ref = refNode.asText();
                sites.add(new RefSite((ObjectNode) node, ref, resolve(scope, ref)));
            }
            Iterator<Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Entry<String, JsonNode> field = fields.next();
                if (isSchema && SCHEMA_MAP_KEYWORDS.contains(field.getKey()) && field.getValue().isObject()) {
                    // The map's keys are names, its values are schemas
                    for (JsonNode subschema : field.getValue()) {
                        walk(subschema, scope, true);
                    }
                } else {
                    walk(field.getValue(), scope, isSchema);
                }
            }
        }

        private static URI withId(URI base, JsonNode idNode) {
            if (idNode == null || !idNode.isTextual() || idNode.asText().startsWith("#")) {
                // No id, or a fragment-only id, which is a plain-name anchor and does not change the base
                return base;
            }
            try {
                URI id = new URI(idNode.asText());
                return base == null ? id : base.resolve(id);
            } catch (URISyntaxException | IllegalArgumentException e) {
                return base;
            }
        }

        private static String resolve(URI base, String ref) {
            // A fragment-only $ref points into the current document, whatever its base URI
            if (base == null || ref.startsWith("#")) {
                return null;
            }
            try {
                String resolved = base.resolve(new URI(ref)).toString();
                return resolved.equals(ref) ? null : resolved;
            } catch (URISyntaxException | IllegalArgumentException e) {
                return null;
            }
        }
    }
}
