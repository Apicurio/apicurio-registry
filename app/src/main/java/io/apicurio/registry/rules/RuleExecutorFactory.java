package io.apicurio.registry.rules;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.apicurio.registry.rules.compatibility.CompatibilityRuleExecutor;
import io.apicurio.registry.rules.integrity.IntegrityRuleExecutor;
import io.apicurio.registry.rules.validity.ValidityRuleExecutor;
import io.apicurio.registry.types.RuleType;

/**
 * Creates a rule executor from a {@link RuleType}.
 */
@ApplicationScoped
public class RuleExecutorFactory {
    
    @Inject
    CompatibilityRuleExecutor compatibility;
    @Inject
    ValidityRuleExecutor validity;
    @Inject
    IntegrityRuleExecutor integrity;

    public RuleExecutor createExecutor(RuleType ruleType) {
        switch (ruleType) {
            case COMPATIBILITY:
                return compatibility;
            case VALIDITY:
                return validity;
            case INTEGRITY:
                return integrity;
            default:
                throw new RuntimeException("Rule type not supported");
        }
    }

}
