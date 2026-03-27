package io.apicurio.registry.openapi.content.canon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.BaseContentCanonicalizer;
import io.apicurio.registry.content.canon.ContentCanonicalizer;

import java.util.Map;

/**
 * An OpenAPI content canonicalizer. This will remove any extra formatting such as whitespace and also sort
 * all fields/properties for all objects (because ordering of properties does not matter).
 */
public class OpenApiContentCanonicalizer extends BaseContentCanonicalizer {

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .configure(com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    /**
     * @see ContentCanonicalizer#canonicalize(TypedContent, Map)
     */
    @Override
    protected TypedContent doCanonicalize(TypedContent content,
                                          Map<String, TypedContent> refs) throws Exception {
        JsonNode jsonNode = mapper.readTree(content.getContent().content());
        String canonical = mapper.writeValueAsString(jsonNode);
        return TypedContent.create(ContentHandle.create(canonical), content.getContentType());
    }

}
