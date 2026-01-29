package io.apicurio.registry.a2a.rest.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents an A2A Agent Card - a JSON metadata document describing an AI agent
 * following the A2A (Agent2Agent) protocol specification.
 *
 * @see <a href="https://a2a-protocol.org/">A2A Protocol</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentCard {

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("version")
    private String version;

    @JsonProperty("url")
    private String url;

    @JsonProperty("provider")
    private AgentProvider provider;

    @JsonProperty("capabilities")
    private AgentCapabilities capabilities;

    @JsonProperty("skills")
    private List<AgentSkill> skills;

    @JsonProperty("defaultInputModes")
    private List<String> defaultInputModes;

    @JsonProperty("defaultOutputModes")
    private List<String> defaultOutputModes;

    @JsonProperty("authentication")
    private AgentAuthentication authentication;

    @JsonProperty("supportsExtendedAgentCard")
    private Boolean supportsExtendedAgentCard;

    public AgentCard() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public AgentProvider getProvider() {
        return provider;
    }

    public void setProvider(AgentProvider provider) {
        this.provider = provider;
    }

    public AgentCapabilities getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(AgentCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    public List<AgentSkill> getSkills() {
        return skills;
    }

    public void setSkills(List<AgentSkill> skills) {
        this.skills = skills;
    }

    public List<String> getDefaultInputModes() {
        return defaultInputModes;
    }

    public void setDefaultInputModes(List<String> defaultInputModes) {
        this.defaultInputModes = defaultInputModes;
    }

    public List<String> getDefaultOutputModes() {
        return defaultOutputModes;
    }

    public void setDefaultOutputModes(List<String> defaultOutputModes) {
        this.defaultOutputModes = defaultOutputModes;
    }

    public AgentAuthentication getAuthentication() {
        return authentication;
    }

    public void setAuthentication(AgentAuthentication authentication) {
        this.authentication = authentication;
    }

    public Boolean getSupportsExtendedAgentCard() {
        return supportsExtendedAgentCard;
    }

    public void setSupportsExtendedAgentCard(Boolean supportsExtendedAgentCard) {
        this.supportsExtendedAgentCard = supportsExtendedAgentCard;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AgentCard agentCard = new AgentCard();

        public Builder name(String name) {
            agentCard.setName(name);
            return this;
        }

        public Builder description(String description) {
            agentCard.setDescription(description);
            return this;
        }

        public Builder version(String version) {
            agentCard.setVersion(version);
            return this;
        }

        public Builder url(String url) {
            agentCard.setUrl(url);
            return this;
        }

        public Builder provider(AgentProvider provider) {
            agentCard.setProvider(provider);
            return this;
        }

        public Builder capabilities(AgentCapabilities capabilities) {
            agentCard.setCapabilities(capabilities);
            return this;
        }

        public Builder skills(List<AgentSkill> skills) {
            agentCard.setSkills(skills);
            return this;
        }

        public Builder defaultInputModes(List<String> defaultInputModes) {
            agentCard.setDefaultInputModes(defaultInputModes);
            return this;
        }

        public Builder defaultOutputModes(List<String> defaultOutputModes) {
            agentCard.setDefaultOutputModes(defaultOutputModes);
            return this;
        }

        public Builder authentication(AgentAuthentication authentication) {
            agentCard.setAuthentication(authentication);
            return this;
        }

        public Builder supportsExtendedAgentCard(Boolean supportsExtendedAgentCard) {
            agentCard.setSupportsExtendedAgentCard(supportsExtendedAgentCard);
            return this;
        }

        public AgentCard build() {
            return agentCard;
        }
    }
}
