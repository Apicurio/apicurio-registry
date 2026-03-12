package io.apicurio.registry.a2a.rest.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents the authentication configuration of an A2A agent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentAuthentication {

    @JsonProperty("schemes")
    private List<String> schemes;

    public AgentAuthentication() {
    }

    public AgentAuthentication(List<String> schemes) {
        this.schemes = schemes;
    }

    public List<String> getSchemes() {
        return schemes;
    }

    public void setSchemes(List<String> schemes) {
        this.schemes = schemes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AgentAuthentication authentication = new AgentAuthentication();

        public Builder schemes(List<String> schemes) {
            authentication.setSchemes(schemes);
            return this;
        }

        public AgentAuthentication build() {
            return authentication;
        }
    }
}
