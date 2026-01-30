package io.apicurio.registry.a2a.rest.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the capabilities of an A2A agent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentCapabilities {

    @JsonProperty("streaming")
    private Boolean streaming;

    @JsonProperty("pushNotifications")
    private Boolean pushNotifications;

    public AgentCapabilities() {
    }

    public AgentCapabilities(Boolean streaming, Boolean pushNotifications) {
        this.streaming = streaming;
        this.pushNotifications = pushNotifications;
    }

    public Boolean getStreaming() {
        return streaming;
    }

    public void setStreaming(Boolean streaming) {
        this.streaming = streaming;
    }

    public Boolean getPushNotifications() {
        return pushNotifications;
    }

    public void setPushNotifications(Boolean pushNotifications) {
        this.pushNotifications = pushNotifications;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AgentCapabilities capabilities = new AgentCapabilities();

        public Builder streaming(Boolean streaming) {
            capabilities.setStreaming(streaming);
            return this;
        }

        public Builder pushNotifications(Boolean pushNotifications) {
            capabilities.setPushNotifications(pushNotifications);
            return this;
        }

        public AgentCapabilities build() {
            return capabilities;
        }
    }
}
