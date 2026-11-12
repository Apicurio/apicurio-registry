package io.apicurio.registry.util;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

public class YAMLObjectMapper {

    public static final ObjectMapper YAML_MAPPER;

    static {
        YAML_MAPPER = YAMLMapper.builder()
                .findAndAddModules()
                .build();
    }
}
