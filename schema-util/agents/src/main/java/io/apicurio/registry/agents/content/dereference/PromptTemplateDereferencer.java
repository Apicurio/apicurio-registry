package io.apicurio.registry.agents.content.dereference;

import io.apicurio.registry.content.dereference.ContentDereferencer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
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
 * Content dereferencer for Prompt Template artifacts.
 *
 * Resolves {@code $ref} references in the {@code variables} and {@code outputSchema} sections
 * by replacing them with the referenced content. Outputs as YAML to preserve multiline template strings.
 */
public class PromptTemplateDereferencer implements ContentDereferencer {

    private static final String VARIABLES = "variables";
    private static final String OUTPUT_SCHEMA = "outputSchema";
    private static final String REF = "$ref";
    private static final ObjectMapper jsonMapper = new JsonMapper();
    private static final ObjectMapper yamlReader = new YAMLMapper();
    private static final ObjectMapper yamlWriter = YAMLMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            JsonNode tree = parseContent(content);
            if (tree.isObject()) {
                ObjectNode obj = (ObjectNode) tree;
                if (obj.has(VARIABLES)) {
                    JsonNode resolved = resolveRefsRecursive(obj.get(VARIABLES), resolvedReferences);
                    obj.set(VARIABLES, resolved);
                }
                if (obj.has(OUTPUT_SCHEMA)) {
                    JsonNode resolved = resolveRefsRecursive(obj.get(OUTPUT_SCHEMA), resolvedReferences);
                    obj.set(OUTPUT_SCHEMA, resolved);
                }
            }
            String result = yamlWriter.writeValueAsString(tree);
            return TypedContent.create(ContentHandle.create(result), ContentTypes.APPLICATION_YAML);
        } catch (Exception e) {
            return content;
        }
    }

    @Override
    public TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls) {
        try {
            JsonNode tree = parseContent(content);
            if (tree.isObject()) {
                ObjectNode obj = (ObjectNode) tree;
                if (obj.has(VARIABLES)) {
                    rewriteRefsRecursive(obj.get(VARIABLES), resolvedReferenceUrls);
                }
                if (obj.has(OUTPUT_SCHEMA)) {
                    rewriteRefsRecursive(obj.get(OUTPUT_SCHEMA), resolvedReferenceUrls);
                }
            }
            String result = yamlWriter.writeValueAsString(tree);
            return TypedContent.create(ContentHandle.create(result), ContentTypes.APPLICATION_YAML);
        } catch (Exception e) {
            return content;
        }
    }

    private JsonNode parseContent(TypedContent content) throws Exception {
        String raw = content.getContent().content();
        String ct = content.getContentType();
        if (ct != null && (ct.contains("yaml") || ct.contains("yml")
                || ct.equalsIgnoreCase("text/x-prompt-template"))) {
            return yamlReader.readTree(raw);
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            return jsonMapper.readTree(raw);
        }
        return yamlReader.readTree(raw);
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
