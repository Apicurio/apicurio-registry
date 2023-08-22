package io.apicurio.registry.content.normalize;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.JsonContentCanonicalizer;
import io.apicurio.registry.content.normalization.ContentNormalizer;
import io.apicurio.registry.rules.UnprocessableSchemaException;

import java.util.Map;
import java.util.TreeMap;

public class JsonContentNormalizer implements ContentNormalizer {

    private static final ObjectMapper jsonMapperWithOrderedProps =
            JsonMapper.builder()
                    .nodeFactory(new SortingNodeFactory(false))
                    .build();

    private static final JsonContentCanonicalizer jsonContentCanonicalizer = new JsonContentCanonicalizer();

    static class SortingNodeFactory extends JsonNodeFactory {
        public SortingNodeFactory(boolean bigDecimalExact) {
            super(bigDecimalExact);
        }

        @Override
        public ObjectNode objectNode() {
            return new ObjectNode(this, new TreeMap<>());
        }
    }

    @Override
    public ContentHandle normalize(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        ContentHandle canonicalized = jsonContentCanonicalizer.canonicalize(content, resolvedReferences);
        try {
            JsonNode jsonNode = jsonMapperWithOrderedProps.readTree(canonicalized.content());

            return ContentHandle.create(jsonNode.asText());

        } catch (JsonProcessingException e) {
            throw new UnprocessableSchemaException(content.content(), e);
        }
    }
}
