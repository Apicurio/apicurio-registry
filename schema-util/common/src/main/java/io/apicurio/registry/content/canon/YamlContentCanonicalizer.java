package io.apicurio.registry.content.canon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;

import java.util.Map;

/**
 * A YAML content canonicalizer. Sorts all object keys and outputs as canonical YAML.
 * This is useful for content types like prompt templates that are naturally expressed in YAML
 * and benefit from preserving multiline string formatting.
 */
public class YamlContentCanonicalizer implements ContentCanonicalizer {

    private static final ObjectMapper jsonMapper = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final ObjectMapper yamlMapper = new ObjectMapper(
            new YAMLFactory()
                    .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @Override
    public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            JsonNode root = readAsJsonNode(content);
            Object ordered = jsonMapper.treeToValue(root, Object.class);
            String converted = yamlMapper.writeValueAsString(ordered);
            return TypedContent.create(ContentHandle.create(converted), ContentTypes.APPLICATION_YAML);
        } catch (Throwable t) {
            return content;
        }
    }

    private JsonNode readAsJsonNode(TypedContent content) throws Exception {
        String raw = content.getContent().content();
        String ct = content.getContentType();
        if (ct != null && (ct.contains("yaml") || ct.contains("yml")
                || ct.equalsIgnoreCase("text/x-prompt-template"))) {
            return new ObjectMapper(new YAMLFactory()).readTree(raw);
        }
        return jsonMapper.readTree(raw);
    }
}
