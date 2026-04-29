package io.apicurio.registry.a2a.rest.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Represents an extension declaration for an A2A agent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentExtension {

    @JsonProperty("uri")
    private String uri;

    @JsonProperty("description")
    private String description;

    @JsonProperty("required")
    private Boolean required;

    @JsonProperty("params")
    private Map<String, Object> params;

    public AgentExtension() {
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AgentExtension agentExtension = new AgentExtension();

        public Builder uri(String uri) {
            agentExtension.setUri(uri);
            return this;
        }

        public Builder description(String description) {
            agentExtension.setDescription(description);
            return this;
        }

        public Builder required(Boolean required) {
            agentExtension.setRequired(required);
            return this;
        }

        public Builder params(Map<String, Object> params) {
            agentExtension.setParams(params);
            return this;
        }

        public AgentExtension build() {
            return agentExtension;
        }
    }
}
