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
 * <p>
 * Design note (producer and rollback semantics): the intended producer is the rule-enforcement
 * layer, at the point where it rejects an incoming write (e.g. a content validation or
 * compatibility check failure) &mdash; before any storage mutation is attempted. Because the
 * write was never committed, there is nothing to roll back or compensate: this event is purely
 * notificational, reporting a rejection that already left no trace in storage. This PR only
 * defines the payload shape and {@link CloudEventType#RULE_VIOLATED} type string; wiring an
 * actual producer into the rule-enforcement path is out of scope here.
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
