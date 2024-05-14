package io.apicurio.registry.content.dereference;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.apicurio.registry.content.ContentHandle;

import java.util.Iterator;
import java.util.Map;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

public class JsonSchemaDereferencer implements ContentDereferencer {

    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new ParameterNamesModule());
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModule(new JsonOrgModule());
        objectMapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        objectMapper.disable(FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
    }

    @Override
    public ContentHandle dereference(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        throw new DereferencingNotSupportedException("Content dereferencing is not supported for JSON Schema");
    }

    /**
     * @see io.apicurio.registry.content.dereference.ContentDereferencer#rewriteReferences(io.apicurio.registry.content.ContentHandle, java.util.Map)
     */
    @Override
    public ContentHandle rewriteReferences(ContentHandle content, Map<String, String> resolvedReferenceUrls) {
        try {
            JsonNode tree = objectMapper.readTree(content.content());
            rewriteIn(tree, resolvedReferenceUrls);
            String converted = objectMapper.writeValueAsString(objectMapper.treeToValue(tree, Object.class));
            return ContentHandle.create(converted);
        } catch (Exception e) {
            return content;
        }
    }

    private void rewriteIn(JsonNode node, Map<String, String> resolvedReferenceUrls) {
        if (node.isObject()) {
            rewriteInObject((ObjectNode) node, resolvedReferenceUrls);
        }
    }

    private void rewriteInObject(ObjectNode node, Map<String, String> resolvedReferenceUrls) {
        if (node.hasNonNull("$ref")) {
            String $ref = node.get("$ref").asText();
            if (resolvedReferenceUrls.containsKey($ref)) {
                node.put("$ref", resolvedReferenceUrls.get($ref));
            }
        }
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode fieldValue = node.get(fieldName);
            if (fieldValue.isObject()) {
                rewriteInObject((ObjectNode) fieldValue, resolvedReferenceUrls);
            }
        }
    }
}
