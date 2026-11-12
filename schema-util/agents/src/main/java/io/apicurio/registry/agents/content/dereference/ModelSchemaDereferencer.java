package io.apicurio.registry.agents.content.dereference;

import io.apicurio.registry.content.dereference.ContentDereferencer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.types.ContentTypes;

import java.util.Iterator;
import java.util.Map;

/**
 * Content dereferencer for AI/ML Model Schema artifacts.
 *
 * Resolves {@code $ref} references by replacing them with the referenced content, and supports
 * rewriting references to point to Registry API URLs.
 */
public class ModelSchemaDereferencer implements ContentDereferencer {

    private static final String REF = "$ref";
    private static final ObjectMapper jsonMapper = new JsonMapper();
    private static final ObjectMapper yamlMapper = new YAMLMapper();

    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            JsonNode tree = parseContent(content);
            JsonNode dereferenced = resolveRefsRecursive(tree, resolvedReferences);
            String result = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(dereferenced);
            return TypedContent.create(ContentHandle.create(result), ContentTypes.APPLICATION_JSON);
        } catch (Exception e) {
            return content;
        }
    }

    @Override
    public TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls) {
        try {
            JsonNode tree = parseContent(content);
            rewriteRefsRecursive(tree, resolvedReferenceUrls);
            String result = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
            return TypedContent.create(ContentHandle.create(result), content.getContentType());
        } catch (Exception e) {
            return content;
        }
    }

    private JsonNode parseContent(TypedContent content) throws Exception {
        String raw = content.getContent().content();
        String ct = content.getContentType();
        if (ct != null && (ct.contains("yaml") || ct.contains("yml"))) {
            return yamlMapper.readTree(raw);
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            return jsonMapper.readTree(raw);
        }
        return yamlMapper.readTree(raw);
    }

    private JsonNode resolveRefsRecursive(JsonNode node,
            Map<String, TypedContent> resolvedReferences) throws Exception {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            TypedContent resolved = findExternalReference(obj, resolvedReferences);
            if (resolved != null) {
                return jsonMapper.readTree(resolved.getContent().content());
            }
            resolveObjectFields(obj, resolvedReferences);
        } else if (node.isArray()) {
            resolveArrayElements((ArrayNode) node, resolvedReferences);
        }
        return node;
    }

    private TypedContent findExternalReference(ObjectNode obj,
            Map<String, TypedContent> resolvedReferences) {
        if (obj.has(REF) && obj.get(REF).isString()) {
            String ref = obj.get(REF).asString();
            if (!ref.startsWith("#")) {
                return resolvedReferences.get(ref);
            }
        }
        return null;
    }

    private void resolveObjectFields(ObjectNode obj,
            Map<String, TypedContent> resolvedReferences) throws Exception {
        Iterator<String> fieldNames = obj.propertyNames().iterator();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode child = obj.get(fieldName);
            JsonNode resolvedChild = resolveRefsRecursive(child, resolvedReferences);
            if (resolvedChild != child) {
                obj.set(fieldName, resolvedChild);
            }
        }
    }

    private void resolveArrayElements(ArrayNode arr,
            Map<String, TypedContent> resolvedReferences) throws Exception {
        for (int i = 0; i < arr.size(); i++) {
            JsonNode resolvedChild = resolveRefsRecursive(arr.get(i), resolvedReferences);
            if (resolvedChild != arr.get(i)) {
                arr.set(i, resolvedChild);
            }
        }
    }

    private void rewriteRefsRecursive(JsonNode node, Map<String, String> resolvedReferenceUrls) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            if (obj.has(REF) && obj.get(REF).isString()) {
                rewriteRef(obj, obj.get(REF).asString(), resolvedReferenceUrls);
            }
            Iterator<String> fieldNames = obj.propertyNames().iterator();
            while (fieldNames.hasNext()) {
                rewriteRefsRecursive(obj.get(fieldNames.next()), resolvedReferenceUrls);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                rewriteRefsRecursive(element, resolvedReferenceUrls);
            }
        }
    }

    private void rewriteRef(ObjectNode obj, String ref, Map<String, String> resolvedReferenceUrls) {
        if (ref.startsWith("#")) {
            return;
        }
        if (resolvedReferenceUrls.containsKey(ref)) {
            obj.put(REF, resolvedReferenceUrls.get(ref));
        } else {
            JsonPointerExternalReference extRef = new JsonPointerExternalReference(ref);
            if (resolvedReferenceUrls.containsKey(extRef.getResource())) {
                JsonPointerExternalReference rewritten = new JsonPointerExternalReference(
                        resolvedReferenceUrls.get(extRef.getResource()),
                        extRef.getComponent());
                obj.put(REF, rewritten.getFullReference());
            }
        }
    }
}
