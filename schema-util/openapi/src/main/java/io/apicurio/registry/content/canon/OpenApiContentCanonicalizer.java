package io.apicurio.registry.content.canon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.types.ContentTypes;

import java.util.Map;

/**
 * An OpenAPI content canonicalizer. This will remove any extra formatting such as whitespace and also sort
 * all fields/properties for all objects (because ordering of properties does not matter).
 */
public class OpenApiContentCanonicalizer implements ContentCanonicalizer {

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    /**
     * @see ContentCanonicalizer#canonicalize(TypedContent, Map)
     */
    @Override
    public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            JsonNode root = ContentTypeUtil.parseJsonOrYaml(content);
            String converted = mapper.writeValueAsString(mapper.treeToValue(root, Object.class));
            return TypedContent.create(ContentHandle.create(converted), ContentTypes.APPLICATION_JSON);
        } catch (Throwable t) {
            return content;
        }
    }

}
