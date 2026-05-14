package io.apicurio.registry.content.dereference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
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

    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static final ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper yamlWriter = new ObjectMapper(
            new YAMLFactory()
                    .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            JsonNode tree = parseContent(content);
            if (tree.isObject()) {
                ObjectNode obj = (ObjectNode) tree;
                if (obj.has("variables")) {
                    JsonNode resolved = resolveRefsRecursive(obj.get("variables"), resolvedReferences);
                    obj.set("variables", resolved);
                }
                if (obj.has("outputSchema")) {
                    JsonNode resolved = resolveRefsRecursive(obj.get("outputSchema"), resolvedReferences);
                    obj.set("outputSchema", resolved);
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
                if (obj.has("variables")) {
                    rewriteRefsRecursive(obj.get("variables"), resolvedReferenceUrls);
                }
                if (obj.has("outputSchema")) {
                    rewriteRefsRecursive(obj.get("outputSchema"), resolvedReferenceUrls);
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
            if (obj.has("$ref") && obj.get("$ref").isTextual()) {
                String ref = obj.get("$ref").asText();
                if (!ref.startsWith("#")) {
                    TypedContent resolved = resolvedReferences.get(ref);
                    if (resolved != null) {
                        return jsonMapper.readTree(resolved.getContent().content());
                    }
                }
            }
            Iterator<String> fieldNames = obj.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode child = obj.get(fieldName);
                JsonNode resolvedChild = resolveRefsRecursive(child, resolvedReferences);
                if (resolvedChild != child) {
                    obj.set(fieldName, resolvedChild);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode resolvedChild = resolveRefsRecursive(arr.get(i), resolvedReferences);
                if (resolvedChild != arr.get(i)) {
                    arr.set(i, resolvedChild);
                }
            }
        }
        return node;
    }

    private void rewriteRefsRecursive(JsonNode node, Map<String, String> resolvedReferenceUrls) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            if (obj.has("$ref") && obj.get("$ref").isTextual()) {
                String ref = obj.get("$ref").asText();
                if (!ref.startsWith("#")) {
                    if (resolvedReferenceUrls.containsKey(ref)) {
                        obj.put("$ref", resolvedReferenceUrls.get(ref));
                    } else {
                        JsonPointerExternalReference extRef = new JsonPointerExternalReference(ref);
                        if (resolvedReferenceUrls.containsKey(extRef.getResource())) {
                            JsonPointerExternalReference rewritten = new JsonPointerExternalReference(
                                    resolvedReferenceUrls.get(extRef.getResource()),
                                    extRef.getComponent());
                            obj.put("$ref", rewritten.getFullReference());
                        }
                    }
                }
            }
            Iterator<String> fieldNames = obj.fieldNames();
            while (fieldNames.hasNext()) {
                rewriteRefsRecursive(obj.get(fieldNames.next()), resolvedReferenceUrls);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                rewriteRefsRecursive(element, resolvedReferenceUrls);
            }
        }
    }
}
