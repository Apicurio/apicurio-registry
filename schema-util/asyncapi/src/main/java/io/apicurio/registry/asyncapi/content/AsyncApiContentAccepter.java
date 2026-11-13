package io.apicurio.registry.asyncapi.content;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;

import java.util.Locale;
import java.util.Map;

public class AsyncApiContentAccepter implements ContentAccepter {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            String contentType = content.getContentType();
            String lc = contentType != null ? contentType.toLowerCase(Locale.ROOT) : null;
            JsonNode tree = null;
            // If the content is YAML, then convert it to JSON first (the data-models library only accepts
            // JSON).
            if (lc != null && (lc.contains("yml") || lc.contains("yaml"))) {
                tree = ContentTypeUtil.parseYaml(content.getContent());
            } else {
                tree = ContentTypeUtil.parseJson(content.getContent());
            }
            if (tree != null && tree.has("asyncapi")) {
                return true;
            }
        } catch (Exception e) {
            // Error - invalid syntax
        }
        return false;
    }

}
