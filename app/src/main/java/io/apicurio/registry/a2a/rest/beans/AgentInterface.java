package io.apicurio.registry.a2a.rest.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an interface endpoint for an A2A agent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentInterface {

    @JsonProperty("url")
    private String url;

    @JsonProperty("protocolBinding")
    private String protocolBinding;

    @JsonProperty("protocolVersion")
    private String protocolVersion;

    @JsonProperty("tenant")
    private String tenant;

    public AgentInterface() {
    }

    public AgentInterface(String url, String protocolBinding, String protocolVersion) {
        this.url = url;
        this.protocolBinding = protocolBinding;
        this.protocolVersion = protocolVersion;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getProtocolBinding() {
        return protocolBinding;
    }

    public void setProtocolBinding(String protocolBinding) {
        this.protocolBinding = protocolBinding;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AgentInterface agentInterface = new AgentInterface();

        public Builder url(String url) {
            agentInterface.setUrl(url);
            return this;
        }

        public Builder protocolBinding(String protocolBinding) {
            agentInterface.setProtocolBinding(protocolBinding);
            return this;
        }

        public Builder protocolVersion(String protocolVersion) {
            agentInterface.setProtocolVersion(protocolVersion);
            return this;
        }

        public Builder tenant(String tenant) {
            agentInterface.setTenant(tenant);
            return this;
        }

        public AgentInterface build() {
            return agentInterface;
        }
    }
}
