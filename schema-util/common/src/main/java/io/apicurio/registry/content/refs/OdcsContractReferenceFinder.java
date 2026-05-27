package io.apicurio.registry.content.refs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.TypedContent;

import java.util.LinkedHashSet;
import java.util.Set;

public class OdcsContractReferenceFinder implements ReferenceFinder {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        Set<ExternalReference> refs = new LinkedHashSet<>();
        try {
            JsonNode tree = YAML_MAPPER.readTree(content.getContent().content());
            JsonNode schemas = tree.get("schemas");
            if (schemas != null && schemas.isArray()) {
                for (JsonNode schema : schemas) {
                    JsonNode location = schema.get("location");
                    if (location != null && location.isTextual()
                            && !location.asText().isEmpty()) {
                        refs.add(new ExternalReference(location.asText()));
                    }
                }
            }
        } catch (Exception e) {
            // return empty set on parse failure
        }
        return refs;
    }
}
