package io.apicurio.registry.operator.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

public class ResourceUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static <T> T duplicate(T source, Class<T> type) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(type);
        try {
            var json = MAPPER.writeValueAsString(source);
            return MAPPER.readValue(json, type);
        } catch (IOException ex) {
            throw new RuntimeException(ex); // TODO
        }
    }
}
