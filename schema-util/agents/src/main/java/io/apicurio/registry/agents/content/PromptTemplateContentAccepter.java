package io.apicurio.registry.agents.content;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.TypedContent;

import tools.jackson.databind.JsonNode;
import io.apicurio.registry.content.util.ContentTypeUtil;

import java.util.Map;

/**
 * Content accepter for Prompt Template artifacts.
 *
 * A Prompt Template is a version-controlled template with variable schemas for LLMOps. Accepts content
 * that either has a {@code $schema} field containing "prompt-template", or has both {@code templateId}
 * and {@code template} fields.
 */
public class PromptTemplateContentAccepter implements ContentAccepter {

    private static final String SCHEMA_KEY = "$schema";

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            String contentType = content.getContentType();
            if (contentType != null && !isAcceptedContentType(contentType)) {
                return false;
            }

            JsonNode tree = ContentTypeUtil.parseJsonOrYaml(content);

            if (!tree.isObject()) {
                return false;
            }

            if (tree.has(SCHEMA_KEY) && tree.get(SCHEMA_KEY).isString()
                    && tree.get(SCHEMA_KEY).asString().contains("prompt-template")) {
                return true;
            }

            if (tree.has("templateId") && tree.get("templateId").isString()
                    && tree.has("template") && tree.get("template").isString()) {
                return true;
            }
        } catch (Exception e) {
            // Error - invalid syntax
        }
        return false;
    }

    private static boolean isAcceptedContentType(String contentType) {
        String ct = contentType.toLowerCase(java.util.Locale.ROOT);
        return ct.contains("json") || ct.contains("yaml")
                || ct.equals("text/x-prompt-template");
    }
}
