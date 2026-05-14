package io.apicurio.registry.a2a.rest.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

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

    @JsonProperty("protocolVersion")
    private String protocolVersion;

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

    @JsonProperty("supportedInterfaces")
    private List<AgentInterface> supportedInterfaces;

    @JsonProperty("securitySchemes")
    private Map<String, SecurityScheme> securitySchemes;

    @JsonProperty("securityRequirements")
    private List<SecurityRequirement> securityRequirements;

    @JsonProperty("iconUrl")
    private String iconUrl;

    @JsonProperty("documentationUrl")
    private String documentationUrl;

    @JsonProperty("signatures")
    private List<AgentCardSignature> signatures;

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

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
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

    public List<AgentInterface> getSupportedInterfaces() {
        return supportedInterfaces;
    }

    public void setSupportedInterfaces(List<AgentInterface> supportedInterfaces) {
        this.supportedInterfaces = supportedInterfaces;
    }

    public Map<String, SecurityScheme> getSecuritySchemes() {
        return securitySchemes;
    }

    public void setSecuritySchemes(Map<String, SecurityScheme> securitySchemes) {
        this.securitySchemes = securitySchemes;
    }

    public List<SecurityRequirement> getSecurityRequirements() {
        return securityRequirements;
    }

    public void setSecurityRequirements(List<SecurityRequirement> securityRequirements) {
        this.securityRequirements = securityRequirements;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public String getDocumentationUrl() {
        return documentationUrl;
    }

    public void setDocumentationUrl(String documentationUrl) {
        this.documentationUrl = documentationUrl;
    }

    public List<AgentCardSignature> getSignatures() {
        return signatures;
    }

    public void setSignatures(List<AgentCardSignature> signatures) {
        this.signatures = signatures;
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

        public Builder protocolVersion(String protocolVersion) {
            agentCard.setProtocolVersion(protocolVersion);
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

        public Builder supportedInterfaces(List<AgentInterface> supportedInterfaces) {
            agentCard.setSupportedInterfaces(supportedInterfaces);
            return this;
        }

        public Builder securitySchemes(Map<String, SecurityScheme> securitySchemes) {
            agentCard.setSecuritySchemes(securitySchemes);
            return this;
        }

        public Builder securityRequirements(List<SecurityRequirement> securityRequirements) {
            agentCard.setSecurityRequirements(securityRequirements);
            return this;
        }

        public Builder iconUrl(String iconUrl) {
            agentCard.setIconUrl(iconUrl);
            return this;
        }

        public Builder documentationUrl(String documentationUrl) {
            agentCard.setDocumentationUrl(documentationUrl);
            return this;
        }

        public Builder signatures(List<AgentCardSignature> signatures) {
            agentCard.setSignatures(signatures);
            return this;
        }

        public AgentCard build() {
            return agentCard;
        }
    }
}
