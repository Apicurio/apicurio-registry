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
public class AgentSkill {
    private String id;
    private String name;
    private String description;
    private List<String> tags;
}
