/*
 * Copyright 2025 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.registry.contracts.tags;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.types.ArtifactType;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class JsonSchemaTagExtractor implements TagExtractor {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaTagExtractor.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TAGS_PROPERTY = "x-tags";
    private static final String REF_PROPERTY = "$ref";

    @Override
    public String getArtifactType() {
        return ArtifactType.JSON;
    }

    @Override
    public Map<String, Set<String>> extractTags(ContentHandle content) {
        try {
            JsonNode root = MAPPER.readTree(content.content());
            Map<String, Set<String>> result = new LinkedHashMap<>();
            visitSchema(root, "", root, result, new HashSet<>());
            return result;
        } catch (Exception e) {
            log.debug("Failed to extract tags from JSON schema: {}", e.getMessage());
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
        visitArrayItems(node.path("items"), path, root, result, refStack);
        visitArrayItems(node.path("prefixItems"), path, root, result, refStack);
        visitCompositionKeyword(node.path("allOf"), path, root, result, refStack);
        visitCompositionKeyword(node.path("anyOf"), path, root, result, refStack);
        visitCompositionKeyword(node.path("oneOf"), path, root, result, refStack);

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

    private static void visitArrayItems(JsonNode schema, String path, JsonNode root,
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

    private static void visitCompositionKeyword(JsonNode schema, String path, JsonNode root,
            Map<String, Set<String>> result, Set<String> refStack) {
        if (schema == null || schema.isMissingNode() || schema.isNull()) {
            return;
        }
        if (schema.isArray()) {
            schema.forEach(item -> visitSchema(item, path, root, result, refStack));
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

    /**
     * Resolves a JSON Pointer or fragment reference against the schema root.
     * <p>
     * Note: external references (e.g. {@code other.json#/definitions/Foo}) are currently
     * handled by extracting the fragment and resolving it against the current document
     * root, which may produce wrong results. For robust handling, only local
     * {@code #-prefixed} references are fully reliable.
     */
    private static JsonNode resolveReference(JsonNode root, String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }

        if (!ref.startsWith("#")) {
            log.debug("Skipping non-local $ref (external references are not supported): {}", ref);
            return null;
        }

        String pointer = ref.substring(1);
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
