package io.apicurio.registry.avro.content.canon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.BaseContentCanonicalizer;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import org.apache.avro.Schema;

import java.util.Comparator;
import java.util.Map;

/**
 * An Avro implementation of a content Canonicalizer that handles avro references.
 */
public class AvroContentCanonicalizer extends BaseContentCanonicalizer {

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private final Comparator<JsonNode> fieldComparator = (n1, n2) -> {
        String name1 = n1.get("name").textValue();
        String name2 = n2.get("name").textValue();
        return name1.compareTo(name2);
    };

    /**
     * @see ContentCanonicalizer#canonicalize(io.apicurio.registry.content.TypedContent, Map)
     */
    @Override
    protected TypedContent doCanonicalize(TypedContent content,
                                          Map<String, TypedContent> refs) throws Exception {
        Schema schema = new Schema.Parser().parse(content.getContent().content());
        String canonical = schema.toString();
        return TypedContent.create(ContentHandle.create(canonical), content.getContentType());
    }
}

