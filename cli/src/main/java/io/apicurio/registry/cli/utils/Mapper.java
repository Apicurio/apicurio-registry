package io.apicurio.registry.cli.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.apicurio.registry.cli.common.CliException;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;

public class Mapper {

    public static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.registerModule(new JsonOrgModule());
        MAPPER.configure(FAIL_ON_UNKNOWN_PROPERTIES, true);
        MAPPER.configure(INDENT_OUTPUT, true);
    }

    @SuppressWarnings("unchecked")
    public static <T> T copy(T original) {
        try {
            var serialized = MAPPER.writeValueAsString(original);
            return MAPPER.readValue(serialized, (Class<T>) original.getClass());
        } catch (JsonProcessingException ex) {
            throw new CliException("Could not copy value.", ex, APPLICATION_ERROR_RETURN_CODE);
        }
    }
}
