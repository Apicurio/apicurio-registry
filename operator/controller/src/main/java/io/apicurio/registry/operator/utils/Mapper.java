package io.apicurio.registry.operator.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.apicurio.registry.operator.OperatorException;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

public class Mapper {

    private static final ObjectMapper MAPPER;

    public static final ObjectMapper YAML_MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());

        YAML_MAPPER = new YAMLMapper();
        YAML_MAPPER.registerModule(new JavaTimeModule());
    }

    public static <T> T duplicate(T source, Class<T> type) {
        requireNonNull(source);
        requireNonNull(type);
        try {
            var json = MAPPER.writeValueAsString(source);
            return MAPPER.readValue(json, type);
        } catch (IOException ex) {
            throw new OperatorException("Could not duplicate value of type " + type.getCanonicalName(), ex);
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
