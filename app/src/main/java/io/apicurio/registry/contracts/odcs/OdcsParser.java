package io.apicurio.registry.contracts.odcs;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import io.apicurio.registry.content.ContentHandle;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OdcsParser {

    private static final ObjectMapper YAML_MAPPER = new YAMLMapper();

    public OdcsContract parse(ContentHandle content) {
        try {
            return YAML_MAPPER.readValue(content.content(), OdcsContract.class);
        } catch (Exception e) {
            throw new OdcsParseException("Failed to parse ODCS contract: " + e.getMessage(), e);
        }
    }

    public OdcsContract parse(String content) {
        return parse(ContentHandle.create(content));
    }

    public String serialize(OdcsContract contract) {
        try {
            return YAML_MAPPER.writeValueAsString(contract);
        } catch (Exception e) {
            throw new OdcsParseException(
                    "Failed to serialize ODCS contract: " + e.getMessage(), e);
        }
    }
}
