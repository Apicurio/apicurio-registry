package io.apicurio.registry.content.canon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.content.ContentHandle;
import org.apache.avro.Schema;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * An Avro implementation of a content Canonicalizer that handles avro references.
 */
public class AvroContentCanonicalizer implements ContentCanonicalizer {

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private final Comparator<JsonNode> fieldComparator = (n1, n2) -> {
        String name1 = n1.get("name").textValue();
        String name2 = n2.get("name").textValue();
        return name1.compareTo(name2);
    };

    /**
     * @see ContentCanonicalizer#canonicalize(io.apicurio.registry.content.ContentHandle, Map)
     */
    @Override
    public ContentHandle canonicalize(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        try {
            JsonNode root = mapper.readTree(content.content());

            // reorder "fields" property
            JsonNode fieldsNode = root.get("fields");
            if (fieldsNode != null) {
                Set<JsonNode> fields = new TreeSet<>(fieldComparator);
                Iterator<JsonNode> elements = fieldsNode.elements();
                while (elements.hasNext()) {
                    fields.add(elements.next());
                }
                ArrayNode array = new ArrayNode(mapper.getNodeFactory());
                fields.forEach(array::add);
                ObjectNode.class.cast(root).replace("fields", array);
            }
            String converted = mapper.writeValueAsString(mapper.treeToValue(root, Object.class));
            return ContentHandle.create(converted);
        } catch (Throwable t) {
            // best effort
            final Schema.Parser parser = new Schema.Parser();
            final List<Schema> schemaRefs = new ArrayList<>();
            for (ContentHandle referencedContent : resolvedReferences.values()) {
                Schema schemaRef = parser.parse(referencedContent.content());
                schemaRefs.add(schemaRef);
            }
            final Schema schema = parser.parse(content.content());
            return ContentHandle.create(schema.toString(schemaRefs, false));
        }
    }
}
