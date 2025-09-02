package io.apicurio.registry.cli.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;

@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ConfigModel {

    @JsonProperty("config")
    private Map<String, String> config = new HashMap<>();

    @JsonProperty("context")
    private Map<String, Context> context = new HashMap<>();

    @JsonProperty("currentContext")
    private String currentContext;

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    @Setter
    @ToString
    public static class Context {

        @JsonProperty("registryUrl")
        private String registryUrl;
    }
}
