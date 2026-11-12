package io.apicurio.registry.util;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class JsonObjectMapper {

    public static final ObjectMapper MAPPER;

    static {
        MAPPER = JsonMapper.builder()
                .findAndAddModules()
                .build();
    }
}
