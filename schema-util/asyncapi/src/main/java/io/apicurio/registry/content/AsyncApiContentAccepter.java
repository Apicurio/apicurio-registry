package io.apicurio.registry.content;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.util.ContentTypeUtil;

import java.util.Map;

public class AsyncApiContentAccepter implements ContentAccepter {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            String contentType = content.getContentType();
            JsonNode tree = null;
            // If the content is YAML, then convert it to JSON first (the data-models library only accepts
            // JSON).
            if (contentType.toLowerCase().contains("yml") || contentType.toLowerCase().contains("yaml")) {
                tree = ContentTypeUtil.parseYaml(content.getContent());
            } else {
                tree = ContentTypeUtil.parseJson(content.getContent());
            }
            if (tree.has("asyncapi")) {
                return true;
            }
        } catch (Exception e) {
            // Error - invalid syntax
        }
        return false;
    }

}
