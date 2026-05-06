package io.apicurio.registry.cli.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigModel {

    public static final int CURRENT_INSTALLATION_VERSION = 1;

    @JsonProperty("installation-version")
    private int installationVersion = CURRENT_INSTALLATION_VERSION;

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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Context {

        @JsonProperty("registryUrl")
        private String registryUrl;

        @JsonProperty("groupId")
        private String groupId;

        @JsonProperty("artifactId")
        private String artifactId;
    }
}
