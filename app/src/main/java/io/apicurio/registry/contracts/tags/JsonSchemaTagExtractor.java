package io.apicurio.registry.contracts.tags;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.types.ArtifactType;
import jakarta.enterprise.context.ApplicationScoped;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class JsonSchemaTagExtractor implements TagExtractor {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaTagExtractor.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> TAG_PROPERTY_NAMES = List.of("x-tags", "x-confluent-tags");

    @Override
    public String getArtifactType() {
        return ArtifactType.JSON;
    }

    @Override
    public Map<String, Set<String>> extractTags(ContentHandle content) {
        try {
            JsonNode root = MAPPER.readTree(content.content());
            Map<String, Set<String>> result = new LinkedHashMap<>();
            Set<String> visited = new HashSet<>();
            extractTagsFromNode(root, root, "", result, visited);
            return result;
        } catch (Exception e) {
            log.debug("Failed to extract tags from JSON Schema: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private void extractTagsFromNode(JsonNode root, JsonNode node, String pathPrefix,
            Map<String, Set<String>> result, Set<String> visited) {
        if (node == null || !node.isObject()) {
            return;
        }

        String id = nodeIdentity(node);
        if (id == null) {
            id = "node@" + System.identityHashCode(node);
        }
        if (visited.contains(id)) {
            return;
        }
        visited.add(id);

        JsonNode properties = node.get("properties");
        if (properties != null && properties.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                JsonNode fieldSchema = entry.getValue();
                String fieldPath = pathPrefix.isEmpty() ? fieldName : pathPrefix + "." + fieldName;

                Set<String> tags = extractFieldTags(fieldSchema);
                if (!tags.isEmpty()) {
                    result.put(fieldPath, tags);
                }

                extractTagsFromNode(root, fieldSchema, fieldPath, result, visited);
            }
        }

        JsonNode items = node.get("items");
        if (items != null && items.isObject()) {
            extractTagsFromNode(root, items, pathPrefix + "[]", result, visited);
        }

        JsonNode additionalProperties = node.get("additionalProperties");
        if (additionalProperties != null && additionalProperties.isObject()) {
            String mapPath = pathPrefix.isEmpty() ? "values" : pathPrefix + ".values";
            extractTagsFromNode(root, additionalProperties, mapPath, result, visited);
        }

        for (String compositionKey : List.of("allOf", "oneOf", "anyOf")) {
            JsonNode composition = node.get(compositionKey);
            if (composition != null && composition.isArray()) {
                for (JsonNode subSchema : composition) {
                    extractTagsFromNode(root, subSchema, pathPrefix, result, visited);
                }
            }
        }

        JsonNode ref = node.get("$ref");
        if (ref != null && ref.isTextual()) {
            String refPath = ref.asText();
            if (refPath.startsWith("#/")) {
                JsonNode resolved = resolveLocalRef(root, refPath);
                if (resolved != null) {
                    extractTagsFromNode(root, resolved, pathPrefix, result, visited);
                }
            }
        }
    }

    private Set<String> extractFieldTags(JsonNode fieldSchema) {
        Set<String> tags = new HashSet<>();
        if (fieldSchema == null || !fieldSchema.isObject()) {
            return tags;
        }
        for (String propertyName : TAG_PROPERTY_NAMES) {
            JsonNode value = fieldSchema.get(propertyName);
            if (value != null && value.isArray()) {
                for (JsonNode item : value) {
                    if (item != null && item.isTextual()) {
                        tags.add(item.asText());
                    }
                }
            }
        }
        return tags;
    }

    private String nodeIdentity(JsonNode node) {
        JsonNode id = node.get("$id");
        if (id != null && id.isTextual()) {
            return id.asText();
        }
        JsonNode title = node.get("title");
        if (title != null && title.isTextual()) {
            return title.asText();
        }
        return null;
    }

    private JsonNode resolveLocalRef(JsonNode root, String refPath) {
        try {
            String path = refPath.substring(2);
            String[] segments = path.split("/");
            JsonNode current = root;
            for (String segment : segments) {
                if (current == null) {
                    return null;
                }
                current = current.get(segment);
            }
            return current;
        } catch (Exception e) {
            return null;
        }
    }
}
