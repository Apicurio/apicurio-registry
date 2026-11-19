package io.apicurio.registry.serde;

import io.apicurio.registry.contracts.rules.RuleExecutionResult;
import io.apicurio.registry.contracts.rules.RuleViolation;
import io.apicurio.registry.serde.config.SerdeConfig;

import java.util.stream.Collectors;

/**
 * Helpers shared by the serializers and deserializers that apply data contract rules.
 */
final class ContractRuleOutcomes {

    static final String DLQ = "DLQ";

    private ContractRuleOutcomes() {
    }

    static boolean hasDeadLetterViolation(RuleExecutionResult result) {
        return result.getViolations() != null
                && result.getViolations().stream().anyMatch(v -> DLQ.equals(v.getAction()));
    }

    /**
     * Fails with a clear message when a rule asks for the dead letter queue but none is configured.
     */
    static void requireDeadLetterQueue(DeadLetterQueue deadLetterQueue, RuleExecutionResult result) {
        if (deadLetterQueue == null) {
            String rules = result.getViolations().stream().filter(v -> DLQ.equals(v.getAction()))
                    .map(RuleViolation::getRuleName).collect(Collectors.joining(", "));
            throw new IllegalStateException("Contract rule(s) " + rules
                    + " failed with onFailure DLQ, but no dead letter queue topic is configured. Set "
                    + SerdeConfig.CONTRACT_RULES_DLQ_TOPIC + ".");
        }
    }
}
