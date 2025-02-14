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

    public static String toYAML(Object value) {
        try {
            return YAML_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new OperatorException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T copy(T original) {
        try {
            var serialized = MAPPER.writeValueAsString(original);
            return MAPPER.readValue(serialized, (Class<T>) original.getClass());
        } catch (JsonProcessingException e) {
            throw new OperatorException(e);
        }
    }
}
