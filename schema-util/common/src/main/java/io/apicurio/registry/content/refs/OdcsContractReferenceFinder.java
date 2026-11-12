package io.apicurio.registry.content.refs;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import io.apicurio.registry.content.TypedContent;

import java.util.LinkedHashSet;
import java.util.Set;

public class OdcsContractReferenceFinder implements ReferenceFinder {

    private static final ObjectMapper YAML_MAPPER = new YAMLMapper();

    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        Set<ExternalReference> refs = new LinkedHashSet<>();
        try {
            JsonNode tree = YAML_MAPPER.readTree(content.getContent().content());
            JsonNode schemas = tree.get("schemas");
            if (schemas != null && schemas.isArray()) {
                for (JsonNode schema : schemas) {
                    JsonNode location = schema.get("location");
                    if (location != null && location.isString()
                            && !location.asString().isEmpty()) {
                        refs.add(new ExternalReference(location.asString()));
                    }
                }
            }
        } catch (Exception e) {
            // return empty set on parse failure
        }
        return refs;
    }
}
