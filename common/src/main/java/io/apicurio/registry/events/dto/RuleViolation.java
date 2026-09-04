package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Event data payload for {@link CloudEventType#RULE_VIOLATED}, describing a rule that failed
 * validation during a registry operation (e.g. content compatibility or validity check). This is
 * distinct from {@link ArtifactRuleChange}, which describes a rule *configuration* change rather
 * than a validation failure.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "groupId", "artifactId", "version", "type", "ruleType", "violations" })
@RegisterForReflection
public class RuleViolation extends ArtifactId {

    /**
     * (Required)
     */
    @JsonProperty("ruleType")
    private String ruleType;

    /**
     * Human-readable descriptions of the individual constraints that failed.
     */
    @JsonProperty("violations")
    private List<String> violations;

    @JsonProperty("ruleType")
    public String getRuleType() {
        return ruleType;
    }

    @JsonProperty("ruleType")
    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public List<String> getViolations() {
        return violations;
    }

    public void setViolations(List<String> violations) {
        this.violations = violations;
    }
}
