package io.apicurio.registry.json.content.dereference;

import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.jsonorg.JsonOrgModule;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.json.content.refs.JsonSchemaRefScopes;
import io.apicurio.registry.json.content.refs.JsonSchemaRefScopes.RefSite;
import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.Draft;
import io.vertx.json.schema.JsonSchema;
import io.vertx.json.schema.JsonSchemaOptions;
import io.vertx.json.schema.impl.JsonRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class JsonSchemaDereferencer implements ContentDereferencer {

    private static final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(JsonSchemaDereferencer.class);
    private static final String idKey = "$id";
    private static final String schemaKey = "$schema";
    private static final String REF = "$ref";

    static {
        objectMapper = JsonMapper.builder()
                .addModule(new JsonOrgModule())
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
                .build();
    }

    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        // Here, when using rewrite, I need the new reference coordinates, using the full artifact coordinates
        // and not just the reference name and the old name, to be able to do the re-write.
        String id = null;
        String schema = null;

        try {
            JsonNode contentNode = objectMapper.readTree(content.getContent().content());
            JsonNode idNode = contentNode.path(idKey);
            JsonNode schemaNode = contentNode.path(schemaKey);

            if (!idNode.isMissingNode() && !idNode.isNull()) {
                id = idNode.asString();
            }
            if (!schemaNode.isMissingNode() && !schemaNode.isNull()) {
                schema = schemaNode.asString();
            }
        } catch (JacksonException e) {
            log.warn("Failed to parse JSON Schema content: {}", e.getMessage());
        }

        JsonSchemaOptions jsonSchemaOptions = new JsonSchemaOptions().setBaseUri("http://localhost");

        if (null != schema) {
            jsonSchemaOptions.setDraft(Draft.fromIdentifier(schema));
        }

        Map<String, JsonSchema> lookups = new HashMap<>();
        resolveReferences(resolvedReferences, lookups);
        String source = useResolvedRefsWhereRegistered(content.getContent().content(), lookups.keySet());
        JsonObject resolvedSchema = JsonRef.resolve(new JsonObject(source), lookups);

        if (null != id) {
            resolvedSchema.put(idKey, id);
        }

        if (schema != null) {
            resolvedSchema.put(schemaKey, schema);
        }

        return TypedContent.create(ContentHandle.create(resolvedSchema.encodePrettily()),
                content.getContentType());
    }

    private void resolveReferences(Map<String, TypedContent> resolvedReferences,
                                   Map<String, JsonSchema> lookups) {
        resolvedReferences.forEach((referenceName, schema) -> {
            JsonPointerExternalReference externalRef = new JsonPointerExternalReference(referenceName);
            // Note: when adding to 'lookups', strip away the "component" part of the reference, because the
            // vertx library is going to do the lookup ONLY by the resource name, excluding the component
            lookups.computeIfAbsent(externalRef.getResource(), (key) -> {
                JsonObject resolvedSchema = JsonRef.resolve(new JsonObject(schema.getContent().content()),
                        lookups);
                return JsonSchema.of(resolvedSchema);
            });
        });
    }

    /**
     * @see io.apicurio.registry.content.dereference.ContentDereferencer#rewriteReferences(io.apicurio.registry.content.TypedContent,
     * java.util.Map)
     */
    @Override
    public TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls) {
        try {
            JsonNode tree = objectMapper.readTree(content.getContent().content());
            for (RefSite site : JsonSchemaRefScopes.find(tree)) {
                String rewritten = rewrite(site.resolved(), resolvedReferenceUrls);
                if (rewritten == null) {
                    rewritten = rewrite(site.ref(), resolvedReferenceUrls);
                }
                if (rewritten != null) {
                    site.node().put(REF, rewritten);
                }
            }
            String converted = objectMapper.writeValueAsString(objectMapper.treeToValue(tree, Object.class));
            return TypedContent.create(ContentHandle.create(converted), content.getContentType());
        } catch (Exception e) {
            return content;
        }
    }

    /**
     * Rewrites $ref values that resolve, against the base URI of their $id scope, to a reference registered
     * under that absolute URI. The JSON Schema library looks references up by their literal value, so this
     * makes references registered under the resolved URI dereferenceable, and keeps identical relative
     * references in different $id scopes apart. Values matching no registered resource are left as they are.
     *
     * @param content the schema content
     * @param registeredResources the resources (reference names without the JSON pointer) available
     * @return the content with matching $ref values made absolute, or the original content if none match
     */
    private static String useResolvedRefsWhereRegistered(String content, Set<String> registeredResources) {
        try {
            JsonNode tree = objectMapper.readTree(content);
            boolean changed = false;
            for (RefSite site : JsonSchemaRefScopes.find(tree)) {
                if (site.resolved() != null && registeredResources
                        .contains(new JsonPointerExternalReference(site.resolved()).getResource())) {
                    site.node().put(REF, site.resolved());
                    changed = true;
                }
            }
            return changed ? objectMapper.writeValueAsString(objectMapper.treeToValue(tree, Object.class))
                    : content;
        } catch (JacksonException e) {
            return content;
        }
    }

    /**
     * Looks a $ref up in the rewrite map, first as a whole and then by its resource, keeping its JSON pointer.
     *
     * @param ref the $ref value, may be null
     * @param resolvedReferenceUrls map of reference names to their rewritten values
     * @return the rewritten $ref, or null if the map has no entry for it
     */
    private static String rewrite(String ref, Map<String, String> resolvedReferenceUrls) {
        if (ref == null) {
            return null;
        }
        if (resolvedReferenceUrls.containsKey(ref)) {
            return resolvedReferenceUrls.get(ref);
        }
        // The reference in the file might be using just a component, use just the resource for the lookup.
        JsonPointerExternalReference externalReference = new JsonPointerExternalReference(ref);
        if (externalReference.getResource() != null
                && resolvedReferenceUrls.containsKey(externalReference.getResource())) {
            return new JsonPointerExternalReference(resolvedReferenceUrls.get(externalReference.getResource()),
                    externalReference.getComponent()).getFullReference();
        }
        return null;
    }
}
