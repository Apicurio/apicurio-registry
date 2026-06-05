package io.apicurio.registry.cli.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigModel {

    public static final int CURRENT_INSTALLATION_VERSION = 1;

    public static final String AUTH_TYPE_BASIC = "basic";
    public static final String AUTH_TYPE_OAUTH2 = "oauth2";

    public static final String CREDENTIAL_KEY_PASSWORD = "password";
    public static final String CREDENTIAL_KEY_CLIENT_SECRET = "client-secret";

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

        @ToString.Exclude
        @JsonProperty("authType")
        private String authType;

        @ToString.Exclude
        @JsonProperty("username")
        private String username;

        @ToString.Exclude
        @JsonProperty("tokenEndpoint")
        private String tokenEndpoint;

        @ToString.Exclude
        @JsonProperty("clientId")
        private String clientId;

        @ToString.Exclude
        @JsonProperty("scope")
        private String scope;

        public void clearAuth() {
            authType = null;
            username = null;
            tokenEndpoint = null;
            clientId = null;
            scope = null;
        }
    }
}
