package io.apicurio.registry.content.dereference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
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

    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

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
