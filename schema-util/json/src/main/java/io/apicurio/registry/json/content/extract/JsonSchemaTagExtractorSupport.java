package io.apicurio.registry.json.content.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shared JSON Schema tag extraction logic.
 */
public final class JsonSchemaTagExtractorSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TAGS_PROPERTY = "x-tags";
    private static final String REF_PROPERTY = "$ref";

    private JsonSchemaTagExtractorSupport() {
    }

    public static Map<String, Set<String>> extractTags(ContentHandle content) {
        try {
            JsonNode root = MAPPER.readTree(content.content());
            Map<String, Set<String>> result = new LinkedHashMap<>();
            visitSchema(root, "", root, result, new HashSet<>());
            return result;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static void visitSchema(JsonNode node, String path, JsonNode root,
            Map<String, Set<String>> result, Set<String> refStack) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            visitObjectSchema(node, path, root, result, refStack);
        } else if (node.isArray()) {
            visitArraySchema(node, path, root, result, refStack);
        }
    }

    private static void visitObjectSchema(JsonNode node, String path, JsonNode root,
            Map<String, Set<String>> result, Set<String> refStack) {
        addTags(node.path(TAGS_PROPERTY), path, result);
        visitReference(node.get(REF_PROPERTY), path, root, result, refStack);

        visitProperties(node.path("properties"), path, root, result, refStack);
        visitArrayKeyword(node.path("items"), path, root, result, refStack);
        visitArrayKeyword(node.path("prefixItems"), path, root, result, refStack);
        visitArrayKeyword(node.path("allOf"), path, root, result, refStack);
        visitArrayKeyword(node.path("anyOf"), path, root, result, refStack);
        visitArrayKeyword(node.path("oneOf"), path, root, result, refStack);

        visitSchema(node.path("not"), path, root, result, refStack);
        visitSchema(node.path("if"), path, root, result, refStack);
        visitSchema(node.path("then"), path, root, result, refStack);
        visitSchema(node.path("else"), path, root, result, refStack);
    }

    private static void visitReference(JsonNode refNode, String path, JsonNode root,
            Map<String, Set<String>> result, Set<String> refStack) {
        if (refNode == null || !refNode.isTextual()) {
            return;
        }

        String ref = refNode.asText();
        if (!refStack.add(ref)) {
            return;
        }

        try {
            JsonNode resolved = resolveReference(root, ref);
            if (resolved != null && !resolved.isMissingNode() && !resolved.isNull()) {
                visitSchema(resolved, path, root, result, refStack);
            }
        } finally {
            refStack.remove(ref);
        }
    }

    private static void visitArraySchema(JsonNode node, String path, JsonNode root,
            Map<String, Set<String>> result, Set<String> refStack) {
        Iterator<JsonNode> it = node.elements();
        while (it.hasNext()) {
            visitSchema(it.next(), path, root, result, refStack);
        }
    }

    private static void visitProperties(JsonNode properties, String path, JsonNode root,
            Map<String, Set<String>> result, Set<String> refStack) {
        if (properties == null || !properties.isObject()) {
            return;
        }
        properties.fields().forEachRemaining(entry ->
                visitSchema(entry.getValue(), childPath(path, entry.getKey()), root, result, refStack));
    }

    private static void visitArrayKeyword(JsonNode schema, String path, JsonNode root,
            Map<String, Set<String>> result, Set<String> refStack) {
        if (schema == null || schema.isMissingNode() || schema.isNull()) {
            return;
        }
        if (schema.isArray()) {
            schema.forEach(item -> visitSchema(item, arrayPath(path), root, result, refStack));
        } else {
            visitSchema(schema, arrayPath(path), root, result, refStack);
        }
    }

    private static void addTags(JsonNode tagsNode, String path, Map<String, Set<String>> result) {
        if (path == null || path.isBlank() || tagsNode == null || !tagsNode.isArray()) {
            return;
        }
        Set<String> tags = new LinkedHashSet<>();
        for (JsonNode tagNode : tagsNode) {
            if (tagNode != null && tagNode.isTextual()) {
                tags.add(tagNode.asText());
            }
        }
        if (!tags.isEmpty()) {
            result.computeIfAbsent(path, p -> new LinkedHashSet<>()).addAll(tags);
        }
    }

    private static JsonNode resolveReference(JsonNode root, String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }

        String pointer = null;
        if (ref.startsWith("#")) {
            pointer = ref.substring(1);
        } else {
            int fragmentIdx = ref.indexOf('#');
            if (fragmentIdx >= 0 && fragmentIdx + 1 < ref.length()) {
                pointer = ref.substring(fragmentIdx + 1);
            }
        }

        if (pointer == null) {
            return null;
        }
        if (pointer.isEmpty()) {
            return root;
        }
        if (!pointer.startsWith("/")) {
            pointer = "/" + pointer;
        }
        return root.at(pointer);
    }

    private static String childPath(String parentPath, String child) {
        if (parentPath == null || parentPath.isBlank()) {
            return child;
        }
        return parentPath + "." + child;
    }

    private static String arrayPath(String parentPath) {
        if (parentPath == null || parentPath.isBlank()) {
            return "[]";
        }
        return parentPath + "[]";
    }
}
