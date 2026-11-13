package io.apicurio.registry.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.util.Map;

public class OdcsContractContentAccepter implements ContentAccepter {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            JsonNode tree = YAML_MAPPER.readTree(content.getContent().content());
            if (tree == null || !tree.isObject()) {
                return false;
            }

            if (!hasTextValue(tree, "kind", "DataContract")) {
                return false;
            }

            JsonNode apiVersion = tree.get("apiVersion");
            if (apiVersion == null || !apiVersion.isTextual()
                    || !apiVersion.asText().startsWith("v3")) {
                return false;
            }

            JsonNode info = tree.get("info");
            if (info == null || !info.isObject()) {
                return false;
            }

            return info.has("title") && info.has("version");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasTextValue(JsonNode tree, String field, String expected) {
        JsonNode node = tree.get(field);
        return node != null && node.isTextual() && expected.equals(node.asText());
    }
}
