package io.apicurio.registry.contracts.tags;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.avro.util.AvroParserAccessor;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.types.ArtifactType;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> TAG_PROPERTY_NAMES = List.of("tags", "confluent:tags");

    @Override
    public String getArtifactType() {
        return ArtifactType.AVRO;
    }

    @Override
    public Map<String, Set<String>> extractTags(ContentHandle content) {
        try {
            Schema schema = AvroParserAccessor.newParser().parse(content.content());
            Map<String, Set<String>> result = new LinkedHashMap<>();
            Set<String> visited = new HashSet<>();
            extractTagsFromSchema(schema, "", result, visited);
            return result;
        } catch (RuntimeException e) {
            // Typically a reference to a named type in another artifact, which this parser does
            // not know: read the tags from the JSON instead, skipping only the referenced types
            log.debug("Reading Avro tags without a full parse: {}", e.getMessage());
            return extractTagsFromJson(content);
        }
    }

    private Map<String, Set<String>> extractTagsFromJson(ContentHandle content) {
        try {
            Map<String, Set<String>> result = new LinkedHashMap<>();
            extractTagsFromJson(MAPPER.readTree(content.content()), "", result);
            return result;
        } catch (IOException e) {
            log.debug("Failed to read tags from Avro schema JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Mirrors {@link #extractTagsFromSchema} on the JSON form. A type given by name (a string) is
     * either primitive or defined elsewhere, and has no field tags here.
     */
    private void extractTagsFromJson(JsonNode type, String pathPrefix, Map<String, Set<String>> result) {
        if (type == null) {
            return;
        }
        if (type.isArray()) {
            for (JsonNode member : type) {
                extractTagsFromJson(member, pathPrefix, result);
            }
            return;
        }
        if (!type.isObject()) {
            return;
        }
        switch (type.path("type").asText()) {
        case "record":
        case "error":
            for (JsonNode field : type.path("fields")) {
                String name = field.path("name").asText();
                String fieldPath = pathPrefix.isEmpty() ? name : pathPrefix + "." + name;
                Set<String> tags = new HashSet<>();
                for (String propertyName : TAG_PROPERTY_NAMES) {
                    for (JsonNode tag : field.path(propertyName)) {
                        if (tag.isTextual()) {
                            tags.add(tag.asText());
                        }
                    }
                }
                if (!tags.isEmpty()) {
                    result.put(fieldPath, tags);
                }
                extractTagsFromJson(field.get("type"), fieldPath, result);
            }
            break;
        case "array":
            extractTagsFromJson(type.get("items"), pathPrefix + "[]", result);
            break;
        case "map":
            extractTagsFromJson(type.get("values"), pathPrefix.isEmpty() ? "values" : pathPrefix + ".values",
                    result);
            break;
        default:
            break;
        }
    }

    private void extractTagsFromSchema(Schema schema, String pathPrefix,
            Map<String, Set<String>> result, Set<String> visited) {
        if (schema == null) {
            return;
        }

        switch (schema.getType()) {
        case RECORD:
            extractTagsFromRecord(schema, pathPrefix, result, visited);
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

    private void extractTagsFromRecord(Schema schema, String pathPrefix,
            Map<String, Set<String>> result, Set<String> visited) {
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
