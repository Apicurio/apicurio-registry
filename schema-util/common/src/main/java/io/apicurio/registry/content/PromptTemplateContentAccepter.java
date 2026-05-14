package io.apicurio.registry.content;

import com.fasterxml.jackson.databind.JsonNode;
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

            if (tree.has("$schema") && tree.get("$schema").isTextual()
                    && tree.get("$schema").asText().contains("prompt-template")) {
                return true;
            }

            if (tree.has("templateId") && tree.get("templateId").isTextual()
                    && tree.has("template") && tree.get("template").isTextual()) {
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
