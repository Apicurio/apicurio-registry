package io.apicurio.registry.json.content.canon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.BaseContentCanonicalizer;
import io.apicurio.registry.content.canon.ContentCanonicalizationException;
import io.apicurio.registry.types.ContentTypes;

import java.util.Map;

/**
 * A common JSON content canonicalizer. This will remove any extra formatting such as whitespace and also sort
 * all fields/properties for all objects (because ordering of properties does not matter in JSON).
 */
public class JsonContentCanonicalizer extends BaseContentCanonicalizer {

    private final ObjectMapper mapper = new ObjectMapper()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    /**
     * @see BaseContentCanonicalizer#canonicalize(TypedContent, Map)
     */
    @Override
    protected TypedContent doCanonicalize(TypedContent content,
            Map<String, TypedContent> refs) throws ContentCanonicalizationException {
        try {
            JsonNode jsonNode = mapper.readTree(content.getContent().content());
            processJsonNode(jsonNode);
            String canonical = mapper.writeValueAsString(mapper.treeToValue(jsonNode, Object.class));
            return TypedContent.create(ContentHandle.create(canonical), ContentTypes.APPLICATION_JSON);
        } catch (Exception e) {
            throw new ContentCanonicalizationException("Failed to canonicalize JSON content", e);
        }
    }

    /**
     * Perform any additional processing on the JSON node. The base JSON canonicalizer does nothing extra.
     *
     * @param node
     */
    protected void processJsonNode(JsonNode node) {
    }
}
