package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArtifactRuleChange extends ArtifactId {

    @JsonProperty("rule")
    private String rule;

    /**
     * @return the rule
     */
    @JsonProperty("rule")
    public String getRule() {
        return rule;
    }

    /**
     * @param rule the rule to set
     */
    @JsonProperty("rule")
    public void setRule(String rule) {
        this.rule = rule;
    }

}
