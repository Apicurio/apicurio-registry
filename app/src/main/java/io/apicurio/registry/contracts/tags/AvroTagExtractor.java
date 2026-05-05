package io.apicurio.registry.contracts.tags;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.types.ArtifactType;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class AvroTagExtractor implements TagExtractor {

    private static final Logger log = LoggerFactory.getLogger(AvroTagExtractor.class);

    private static final List<String> TAG_PROPERTY_NAMES = List.of("tags", "confluent:tags");

    @Override
    public String getArtifactType() {
        return ArtifactType.AVRO;
    }

    @Override
    public Map<String, Set<String>> extractTags(ContentHandle content) {
        try {
            Schema schema = new Schema.Parser().parse(content.content());
            Map<String, Set<String>> result = new LinkedHashMap<>();
            Set<String> visited = new HashSet<>();
            extractTagsFromSchema(schema, "", result, visited);
            return result;
        } catch (Exception e) {
            log.debug("Failed to extract tags from Avro schema: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private void extractTagsFromSchema(Schema schema, String pathPrefix,
            Map<String, Set<String>> result, Set<String> visited) {
        if (schema == null) {
            return;
        }

        switch (schema.getType()) {
        case RECORD:
            String fullName = schema.getFullName();
            if (visited.contains(fullName)) {
                return;
            }
            visited.add(fullName);

            for (Schema.Field field : schema.getFields()) {
                String fieldPath = pathPrefix.isEmpty() ? field.name()
                        : pathPrefix + "." + field.name();

                Set<String> tags = extractFieldTags(field);
                if (!tags.isEmpty()) {
                    result.put(fieldPath, tags);
                }

                extractTagsFromSchema(field.schema(), fieldPath, result, visited);
            }
            break;

        case ARRAY:
            extractTagsFromSchema(schema.getElementType(), pathPrefix + "[]", result, visited);
            break;

        case MAP:
            String mapPath = pathPrefix.isEmpty() ? "values" : pathPrefix + ".values";
            extractTagsFromSchema(schema.getValueType(), mapPath, result, visited);
            break;

        case UNION:
            for (Schema member : schema.getTypes()) {
                if (member.getType() != Schema.Type.NULL) {
                    extractTagsFromSchema(member, pathPrefix, result, visited);
                }
            }
            break;

        default:
            break;
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractFieldTags(Schema.Field field) {
        Set<String> tags = new HashSet<>();
        for (String propertyName : TAG_PROPERTY_NAMES) {
            Object value = field.getObjectProp(propertyName);
            if (value instanceof Collection) {
                for (Object item : (Collection<Object>) value) {
                    if (item != null) {
                        tags.add(item.toString());
                    }
                }
            }
        }
        return tags;
    }
}
