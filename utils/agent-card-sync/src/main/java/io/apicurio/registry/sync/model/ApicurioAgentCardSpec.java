package io.apicurio.registry.sync.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApicurioAgentCardSpec {
    private String name;
    private String description;
    private String version;
    private List<SupportedInterface> supportedInterfaces;
    private AgentCapabilities capabilities;
    private List<AgentSkill> skills;
    private List<String> defaultInputModes;
    private List<String> defaultOutputModes;
}
