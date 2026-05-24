package io.apicurio.registry.contracts.rules;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class ContractRuleExecutorFactory {

    @Inject
    Instance<ContractRuleExecutor> executors;

    public Optional<ContractRuleExecutor> getExecutor(String ruleType) {
        if (ruleType == null) {
            return Optional.empty();
        }
        return executors.stream()
                .filter(e -> ruleType.equals(e.getRuleType()))
                .findFirst();
    }
}
