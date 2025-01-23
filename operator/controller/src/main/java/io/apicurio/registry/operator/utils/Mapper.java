package io.apicurio.registry.operator.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.apicurio.registry.operator.OperatorException;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

public class Mapper {

    private static final ObjectMapper MAPPER;

    public static final ObjectMapper YAML_MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.configure(FAIL_ON_UNKNOWN_PROPERTIES, true);

        YAML_MAPPER = new YAMLMapper();
        YAML_MAPPER.registerModule(new JavaTimeModule());
        YAML_MAPPER.configure(FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public static <T> T deserialize(String data, Class<T> klass) {
        try {
            return YAML_MAPPER.readValue(data, klass);
        } catch (JsonProcessingException ex) {
            throw new OperatorException("Could not deserialize resource.", ex);
        }
    }

    public static String toYAML(Object value) {
        try {
            return YAML_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new OperatorException(e);
        }
    }
}
