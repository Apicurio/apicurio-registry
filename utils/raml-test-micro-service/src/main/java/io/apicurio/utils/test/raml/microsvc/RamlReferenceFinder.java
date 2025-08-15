package io.apicurio.utils.test.raml.microsvc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class RamlReferenceFinder implements ReferenceFinder {

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        Set<ExternalReference> externalReferences = new HashSet<>();
        try {
            JsonNode root = mapper.readTree(content.getContent().content());
            findRefs(root, externalReferences);
        } catch (JsonProcessingException e) {
        }
        return externalReferences;
    }

    private void findRefs(JsonNode node, Set<ExternalReference> externalReferences) {
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
                        externalReferences.add(new ExternalReference(includeName));
                    }
                } else {
                    findRefs(fieldNode, externalReferences);
                }
            }
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                JsonNode childNode = array.get(i);
                if (childNode != null && !childNode.isNull()) {
                    findRefs(childNode, externalReferences);
                }
            }
        }
    }

}
