package io.apicurio.registry.content;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.util.ContentTypeUtil;

import java.util.Map;

public class JsonSchemaContentAccepter implements ContentAccepter {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            if (content.getContentType() != null && content.getContentType().toLowerCase().contains("json")
                    && !ContentTypeUtil.isParsableJson(content.getContent())) {
                return false;
            }
            JsonNode tree = ContentTypeUtil.parseJson(content.getContent());
            if (tree.has("$schema") && tree.get("$schema").asText().contains("json-schema.org")
                    || tree.has("properties")) {
                return true;
            }
        } catch (Exception e) {
            // Error - invalid syntax
        }
        return false;
    }

}
