package io.apicurio.registry.json.content.canon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.BaseContentCanonicalizer;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import org.apache.avro.Schema;

import java.io.IOException;
import java.util.Map;

/**
 * A common JSON content canonicalizer. This will remove any extra formatting such as whitespace and also sort
 * all fields/properties for all objects (because ordering of properties does not matter in JSON).
 */
public class JsonContentCanonicalizer extends BaseContentCanonicalizer {

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    /**
     * @see ContentCanonicalizer#canonicalize(TypedContent, Map)
     */
    @Override
    public TypedContent doCanonicalize(TypedContent content,
                                          Map<String, TypedContent> refs) throws Exception {
        Schema schema = new Schema.Parser().parse(content.getContent().content());
        String canonical = schema.toString();
        return TypedContent.create(ContentHandle.create(canonical), content.getContentType());
    }

    /**
     * Perform any additional processing on the JSON node. The base JSON canonicalizer does nothing extra.
     * 
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
