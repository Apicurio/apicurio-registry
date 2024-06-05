package io.apicurio.registry.content.canon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;

import java.io.IOException;
import java.util.Map;

/**
 * A common JSON content canonicalizer.  This will remove any extra formatting such as whitespace
 * and also sort all fields/properties for all objects (because ordering of properties does not
 * matter in JSON).
 * 
 */
public class JsonContentCanonicalizer implements ContentCanonicalizer {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    /**
     * @see ContentCanonicalizer#canonicalize(TypedContent, Map)
     */
    @Override
    public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            JsonNode root = readAsJsonNode(content);
            processJsonNode(root);
            String converted = mapper.writeValueAsString(mapper.treeToValue(root, Object.class));
            return TypedContent.create(ContentHandle.create(converted), ContentTypes.APPLICATION_JSON);
        } catch (Throwable t) {
            return content;
        }
    }

    /**
     * Perform any additional processing on the JSON node.  The base JSON canonicalizer 
     * does nothing extra.
     * @param node
     */
    protected void processJsonNode(JsonNode node) {
    }

    /**
     * @param content
     * @return
     * @throws IOException
     */
    private JsonNode readAsJsonNode(TypedContent content) throws IOException {
        return mapper.readTree(content.getContent().content());
    }

}
