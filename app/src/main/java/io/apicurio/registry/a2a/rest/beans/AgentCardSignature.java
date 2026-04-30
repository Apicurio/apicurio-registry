package io.apicurio.registry.a2a.rest.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Represents a signature on an A2A agent card.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentCardSignature {

    @JsonProperty("protected")
    private String protectedHeader;

    @JsonProperty("signature")
    private String signature;

    @JsonProperty("header")
    private Map<String, Object> header;

    public AgentCardSignature() {
    }

    public String getProtectedHeader() {
        return protectedHeader;
    }

    public void setProtectedHeader(String protectedHeader) {
        this.protectedHeader = protectedHeader;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public Map<String, Object> getHeader() {
        return header;
    }

    public void setHeader(Map<String, Object> header) {
        this.header = header;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AgentCardSignature agentCardSignature = new AgentCardSignature();

        public Builder protectedHeader(String protectedHeader) {
            agentCardSignature.setProtectedHeader(protectedHeader);
            return this;
        }

        public Builder signature(String signature) {
            agentCardSignature.setSignature(signature);
            return this;
        }

        public Builder header(Map<String, Object> header) {
            agentCardSignature.setHeader(header);
            return this;
        }

        public AgentCardSignature build() {
            return agentCardSignature;
        }
    }
}
