package io.apicurio.registry.a2a.rest.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Represents a security requirement for an A2A agent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityRequirement {

    @JsonProperty("schemes")
    private Map<String, List<String>> schemes;

    public SecurityRequirement() {
    }

    public Map<String, List<String>> getSchemes() {
        return schemes;
    }

    public void setSchemes(Map<String, List<String>> schemes) {
        this.schemes = schemes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final SecurityRequirement securityRequirement = new SecurityRequirement();

        public Builder schemes(Map<String, List<String>> schemes) {
            securityRequirement.setSchemes(schemes);
            return this;
        }

        public SecurityRequirement build() {
            return securityRequirement;
        }
    }
}
