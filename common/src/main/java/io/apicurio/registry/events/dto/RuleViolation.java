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
 * Design note (producer and transaction semantics): the intended producer is the rule-enforcement
 * layer ({@code RulesServiceImpl}), wrapping the {@code applyRules()} call, catching
 * {@link io.apicurio.registry.rules.RuleViolationException}, firing the CDI event, and rethrowing
 * unchanged. No artifact mutation is committed when validation is rejected &mdash; but durable
 * recording of the rejection notification still requires a transaction strategy: if the
 * {@code webhook_delivery_logs} INSERT shares the enclosing transaction that is rolled back by the
 * rethrown exception, the notification record is silently lost. Whether to use a separate
 * transaction for the delivery log row, or fire the CDI event after the transaction boundary, is
 * an open design question to resolve before implementing the producer (tracked for #8568). No
 * producer or delivery implementation is in scope for this DTO PR.
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
