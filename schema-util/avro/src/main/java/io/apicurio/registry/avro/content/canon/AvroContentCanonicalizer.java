package io.apicurio.registry.avro.content.canon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.avro.util.AvroParserAccessor;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.types.ContentTypes;
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
     * @see ContentCanonicalizer#canonicalize(io.apicurio.registry.content.TypedContent, Map)
     */
    @Override
    public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            JsonNode root = mapper.readTree(content.getContent().content());

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
            return TypedContent.create(ContentHandle.create(converted), ContentTypes.APPLICATION_JSON);
        } catch (Throwable t) {
            // Best effort. Reached when the content is valid Avro but not readable by the plain mapper
            // above, e.g. because it contains comments. The seeded parser resolves interdependent references
            // regardless of map iteration order (#9188); the types they define are snapshotted before the
            // main parse so that only those are treated as external by Schema#toString.
            final Schema.Parser parser = AvroParserAccessor.newParser(resolvedReferences);
            final List<Schema> schemaRefs = new ArrayList<>(parser.getTypes().values());
            final Schema schema = parser.parse(content.getContent().content());
            return TypedContent.create(ContentHandle.create(schema.toString(schemaRefs, false)),
                    ContentTypes.APPLICATION_JSON);
        }
    }
}
