package io.apicurio.utils.test.raml.microsvc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.types.ContentTypes;

import java.util.Iterator;
import java.util.Map;

public class RamlContentDereferencer implements ContentDereferencer {

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            JsonNode root = mapper.readTree(content.getContent().content());
            deref(root, resolvedReferences);
            String dereferencedContent = mapper.writeValueAsString(mapper.treeToValue(root, Object.class));
            return TypedContent.create(ContentHandle.create(dereferencedContent), ContentTypes.APPLICATION_YAML);
        } catch (JsonProcessingException e) {
        }

        return content;
    }

    private void deref(JsonNode node, Map<String, TypedContent> resolvedReferences) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<String> stringIterator = objectNode.fieldNames();
            while (stringIterator.hasNext()) {
                String fieldName = stringIterator.next();
                JsonNode fieldNode = objectNode.get(fieldName);
                if (fieldNode.isTextual()) {
                    String textValue = fieldNode.textValue();
                    if (textValue.startsWith("~include")) {
                        String includeName = textValue.substring("~include ".length());
                        if (resolvedReferences.containsKey(includeName)) {
                            TypedContent includeContent = resolvedReferences.get(includeName);
                            objectNode.put(fieldName, includeContent.getContent().content());
                        }
                    }
                } else {
                    deref(fieldNode, resolvedReferences);
                }
            }
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                JsonNode childNode = array.get(i);
                if (childNode != null && !childNode.isNull()) {
                    deref(childNode, resolvedReferences);
                }
            }
        }
    }

    @Override
    public TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls) {
        try {
            JsonNode root = mapper.readTree(content.getContent().content());
            rewrite(root, resolvedReferenceUrls);
            String dereferencedContent = mapper.writeValueAsString(mapper.treeToValue(root, Object.class));
            return TypedContent.create(ContentHandle.create(dereferencedContent), ContentTypes.APPLICATION_YAML);
        } catch (JsonProcessingException e) {
        }

        return content;
    }

    private void rewrite(JsonNode node, Map<String, String> resolvedReferenceUrls) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<String> stringIterator = objectNode.fieldNames();
            while (stringIterator.hasNext()) {
                String fieldName = stringIterator.next();
                JsonNode fieldNode = objectNode.get(fieldName);
                if (fieldNode.isTextual()) {
                    String textValue = fieldNode.textValue();
                    if (textValue.startsWith("~include")) {
                        String includeName = textValue.substring("~include ".length());
                        if (resolvedReferenceUrls.containsKey(includeName)) {
                            String refUrl = resolvedReferenceUrls.get(includeName);
                            objectNode.put(fieldName, "~include " + refUrl);
                        }
                    }
                } else {
                    rewrite(fieldNode, resolvedReferenceUrls);
                }
            }
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                JsonNode childNode = array.get(i);
                if (childNode != null && !childNode.isNull()) {
                    rewrite(childNode, resolvedReferenceUrls);
                }
            }
        }
    }
}
