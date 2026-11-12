package io.apicurio.registry.cli.utils;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.jsonorg.JsonOrgModule;
import io.apicurio.registry.cli.common.CliException;
import tools.jackson.core.JacksonException;

import static tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static tools.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;

public class Mapper {

    public static final ObjectMapper MAPPER;

    static {
        MAPPER = JsonMapper.builder()
                .addModule(new JsonOrgModule())
                .configure(FAIL_ON_UNKNOWN_PROPERTIES, true)
                .configure(INDENT_OUTPUT, true)
                .build();
    }

    @SuppressWarnings("unchecked")
    public static <T> T copy(T original) {
        try {
            var serialized = MAPPER.writeValueAsString(original);
            return MAPPER.readValue(serialized, (Class<T>) original.getClass());
        } catch (JacksonException ex) {
            throw new CliException("Could not copy value.", ex, APPLICATION_ERROR_RETURN_CODE);
        }
    }
}
