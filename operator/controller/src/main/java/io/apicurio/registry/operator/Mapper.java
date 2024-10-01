package io.apicurio.registry.operator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public class Mapper {

    public static final ObjectMapper YAML_MAPPER;

    static {
        YAML_MAPPER = new YAMLMapper();
    }

    public static String toYAML(Object value) {
        try {
            return YAML_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new OperatorException(e);
        }
    }
}
