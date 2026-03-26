package io.apicurio.registry.openapi.content.canon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.BaseContentCanonicalizer;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import org.apache.avro.Schema;

import java.util.Map;

/**
 * An OpenAPI content canonicalizer. This will remove any extra formatting such as whitespace and also sort
 * all fields/properties for all objects (because ordering of properties does not matter).
 */
public class OpenApiContentCanonicalizer extends BaseContentCanonicalizer {

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

}
