package io.apicurio.registry.content.canon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;

/**
 * Utility class for canonicalizing JSON and YAML content.
 * Provides common functionality for artifact types that support both formats.
 */
public class JsonYamlCanonicalizer {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    private JsonYamlCanonicalizer() {
        // Utility class
    }

    /**
     * Canonicalizes content that can be in either JSON or YAML format.
     * The canonical form is always returned as sorted JSON.
     *
     * @param content the content to canonicalize
     * @return the canonicalized content as JSON
     * @throws ContentCanonicalizationException if canonicalization fails
     */
    public static TypedContent canonicalize(TypedContent content) throws ContentCanonicalizationException {
        try {
            ObjectMapper mapper = isYamlContentType(content.getContentType()) ? YAML_MAPPER : JSON_MAPPER;
            JsonNode jsonNode = mapper.readTree(content.getContent().content());
            // Always return canonical form as JSON, regardless of input format
            String canonical = JSON_MAPPER.writeValueAsString(JSON_MAPPER.treeToValue(jsonNode, Object.class));
            return TypedContent.create(ContentHandle.create(canonical), ContentTypes.APPLICATION_JSON);
        } catch (Exception e) {
            throw new ContentCanonicalizationException("Failed to canonicalize content", e);
        }
    }

    /**
     * Determines if the given content type represents YAML format.
     *
     * @param contentType the content type to check
     * @return true if the content type is YAML, false otherwise
     */
    public static boolean isYamlContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lowerContentType = contentType.toLowerCase();
        return lowerContentType.contains("yaml") || lowerContentType.contains("yml");
    }
}
