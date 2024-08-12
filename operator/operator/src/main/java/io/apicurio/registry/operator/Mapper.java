package io.apicurio.registry.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public class Mapper {

    public static ObjectMapper YAML_MAPPER;

    static {
        YAML_MAPPER = new YAMLMapper();
    }
}
