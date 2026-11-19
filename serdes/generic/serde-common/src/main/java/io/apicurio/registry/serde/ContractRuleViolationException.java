package io.apicurio.registry.serde;

import io.apicurio.registry.contracts.rules.RuleViolation;

import java.util.List;

/**
 * Thrown when a record fails the data contract rules applied by a serializer or deserializer. When a failed
 * rule has {@code onFailure: DLQ}, the record has been sent to the dead letter queue topic returned by
 * {@link #getDeadLetterTopic()} before this exception is thrown.
 */
public class ContractRuleViolationException extends RuntimeException {

    private static final long serialVersionUID = -3121664357213084907L;

    private final String mode;
    private final transient List<RuleViolation> violations;
    private final String deadLetterTopic;

    public ContractRuleViolationException(String mode, List<RuleViolation> violations, String deadLetterTopic) {
        super(message(mode, violations, deadLetterTopic));
        this.mode = mode;
        this.violations = violations;
        this.deadLetterTopic = deadLetterTopic;
    }

    private static String message(String mode, List<RuleViolation> violations, String deadLetterTopic) {
        String message = "Contract rule validation failed (" + mode + "): " + violations;
        return deadLetterTopic == null ? message
            : message + ". The record was sent to the dead letter queue topic '" + deadLetterTopic + "'.";
    }

    /**
     * The rule mode that failed, {@code WRITE} or {@code READ}.
     */
    public String getMode() {
        return mode;
    }

    public List<RuleViolation> getViolations() {
        return violations;
    }

    /**
     * The dead letter queue topic the record was sent to, or {@code null} if it was not sent to one.
     */
    public String getDeadLetterTopic() {
        return deadLetterTopic;
    }
}
