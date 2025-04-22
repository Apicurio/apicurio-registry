package io.apicurio.utils.test.raml.microsvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.types.ContentTypes;

import java.util.Map;

public class RamlContentCanonicalizer implements ContentCanonicalizer {

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @Override
    public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            JsonNode root = mapper.readTree(content.getContent().content());
            String converted = mapper.writeValueAsString(mapper.treeToValue(root, Object.class));
            return TypedContent.create(ContentHandle.create(converted), ContentTypes.APPLICATION_YAML);
        } catch (Throwable t) {
            return content;
        }
    }

}
