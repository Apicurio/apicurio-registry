package io.apicurio.registry.content;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.util.ContentTypeUtil;

import java.util.Map;

/**
 * Content accepter for AI/ML Model Schema artifacts.
 *
 * A Model Schema defines input/output schemas for AI/ML models. Accepts content that either has a
 * {@code $schema} field containing "model-schema", or has a {@code modelId} field combined with
 * at least one of {@code input} or {@code output}.
 */
public class ModelSchemaContentAccepter implements ContentAccepter {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            String contentType = content.getContentType();
            if (contentType != null && !isJsonOrYaml(contentType)) {
                return false;
            }

            JsonNode tree = ContentTypeUtil.parseJsonOrYaml(content);

            if (!tree.isObject()) {
                return false;
            }

            if (tree.has("$schema") && tree.get("$schema").isTextual()
                    && tree.get("$schema").asText().contains("model-schema")) {
                return true;
            }

            if (tree.has("modelId") && tree.get("modelId").isTextual()
                    && (tree.has("input") || tree.has("output"))) {
                return true;
            }
        } catch (Exception e) {
            // Error - invalid syntax
        }
        return false;
    }

    private static boolean isJsonOrYaml(String contentType) {
        String ct = contentType.toLowerCase(java.util.Locale.ROOT);
        return ct.contains("json") || ct.contains("yaml");
    }
}
