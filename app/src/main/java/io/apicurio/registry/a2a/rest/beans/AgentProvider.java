package io.apicurio.registry.a2a.rest.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the provider/organization that created an A2A agent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentProvider {

    @JsonProperty("organization")
    private String organization;

    @JsonProperty("url")
    private String url;

    public AgentProvider() {
    }

    public AgentProvider(String organization, String url) {
        this.organization = organization;
        this.url = url;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AgentProvider provider = new AgentProvider();

        public Builder organization(String organization) {
            provider.setOrganization(organization);
            return this;
        }

        public Builder url(String url) {
            provider.setUrl(url);
            return this;
        }

        public AgentProvider build() {
            return provider;
        }
    }
}
