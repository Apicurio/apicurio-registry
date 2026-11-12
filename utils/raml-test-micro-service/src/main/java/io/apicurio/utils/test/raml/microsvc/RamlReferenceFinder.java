package io.apicurio.utils.test.raml.microsvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinderException;
import tools.jackson.core.JacksonException;
import io.apicurio.registry.content.refs.ReferenceFinder;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class RamlReferenceFinder implements ReferenceFinder {

    private static final ObjectMapper mapper = new YAMLMapper();

    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        try {
            Set<ExternalReference> externalReferences = new HashSet<>();
            JsonNode root = mapper.readTree(content.getContent().content());
            findRefs(root, externalReferences);
            return externalReferences;
        } catch (JacksonException e) {
            throw new ReferenceFinderException("Error finding external references in a RAML file.", e);
        }
    }

    private void findRefs(JsonNode node, Set<ExternalReference> externalReferences) {
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
