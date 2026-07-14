package io.apicurio.registry.a2a.rest.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Structured filters for agent search requests.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentSearchFilters {

    @JsonProperty("capabilities")
    private Map<String, Boolean> capabilities;

    @JsonProperty("skills")
    private List<String> skills;

    @JsonProperty("labels")
    private Map<String, String> labels;

    @JsonProperty("inputModes")
    private List<String> inputModes;

    @JsonProperty("outputModes")
    private List<String> outputModes;

    @JsonProperty("protocolBindings")
    private List<String> protocolBindings;

    public AgentSearchFilters() {
    }

    public Map<String, Boolean> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Map<String, Boolean> capabilities) {
        this.capabilities = capabilities;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public List<String> getInputModes() {
        return inputModes;
    }

    public void setInputModes(List<String> inputModes) {
        this.inputModes = inputModes;
    }

    public List<String> getOutputModes() {
        return outputModes;
    }

    public void setOutputModes(List<String> outputModes) {
        this.outputModes = outputModes;
    }

    public List<String> getProtocolBindings() {
        return protocolBindings;
    }

    public void setProtocolBindings(List<String> protocolBindings) {
        this.protocolBindings = protocolBindings;
    }
}
