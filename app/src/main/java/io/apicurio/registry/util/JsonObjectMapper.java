package io.apicurio.registry.util;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonObjectMapper {

    public static ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.findAndRegisterModules();
    }
}
