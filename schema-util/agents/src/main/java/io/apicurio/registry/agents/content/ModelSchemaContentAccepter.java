package io.apicurio.registry.agents.content;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.TypedContent;

import tools.jackson.databind.JsonNode;
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

    private static final String SCHEMA_KEY = "$schema";

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

            if (tree.has(SCHEMA_KEY) && tree.get(SCHEMA_KEY).isString()
                    && tree.get(SCHEMA_KEY).asString().contains("model-schema")) {
                return true;
            }

            if (tree.has("modelId") && tree.get("modelId").isString()
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
