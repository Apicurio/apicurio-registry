package io.apicurio.registry.operator.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.apicurio.registry.operator.OperatorException;

public class Mapper {

    private static final ObjectMapper MAPPER;

    public static final ObjectMapper YAML_MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());

        YAML_MAPPER = new YAMLMapper();
        YAML_MAPPER.registerModule(new JavaTimeModule());
    }

    public static String toYAML(Object value) {
        try {
            return YAML_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new OperatorException(e);
        }
    }
}
