package io.apicurio.utils.test.raml.microsvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.types.ContentTypes;
import tools.jackson.core.JacksonException;

import java.util.Iterator;
import java.util.Map;

public class RamlContentDereferencer implements ContentDereferencer {

    private static final ObjectMapper mapper = new YAMLMapper();

    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            JsonNode root = mapper.readTree(content.getContent().content());
            deref(root, resolvedReferences);
            String dereferencedContent = mapper.writeValueAsString(mapper.treeToValue(root, Object.class));
            return TypedContent.create(ContentHandle.create(dereferencedContent), ContentTypes.APPLICATION_YAML);
        } catch (JacksonException e) {
        }

        return content;
    }

    private void deref(JsonNode node, Map<String, TypedContent> resolvedReferences) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<String> stringIterator = objectNode.propertyNames().iterator();
            while (stringIterator.hasNext()) {
                String fieldName = stringIterator.next();
                JsonNode fieldNode = objectNode.get(fieldName);
                if (fieldNode.isString()) {
                    String textValue = fieldNode.stringValue();
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
        } catch (JacksonException e) {
        }

        return content;
    }

    private void rewrite(JsonNode node, Map<String, String> resolvedReferenceUrls) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<String> stringIterator = objectNode.propertyNames().iterator();
            while (stringIterator.hasNext()) {
                String fieldName = stringIterator.next();
                JsonNode fieldNode = objectNode.get(fieldName);
                if (fieldNode.isString()) {
                    String textValue = fieldNode.stringValue();
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
