package io.apicurio.registry.content.refs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.TypedContent;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class OdcsContractReferenceFinder implements ReferenceFinder {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        Set<ExternalReference> refs = new LinkedHashSet<>();
        try {
            JsonNode tree = YAML_MAPPER.readTree(content.getContent().content());
            findReferences(tree, refs);
        } catch (Exception e) {
            // return empty set on parse failure
        }
        return refs;
    }

    private void findReferences(JsonNode node, Set<ExternalReference> refs) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            JsonNode location = node.get("location");
            if (location != null && location.isTextual() && !location.asText().isEmpty()) {
                refs.add(new ExternalReference(location.asText()));
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                findReferences(fields.next().getValue(), refs);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                findReferences(element, refs);
            }
        }
    }
}
