package io.apicurio.registry.contracts.rules;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ContractRuleExecutorFactory {

    @Inject
    Instance<ContractRuleExecutor> executors;

    private List<ContractRuleExecutor> standaloneExecutors;

    public ContractRuleExecutorFactory() {
    }

    private ContractRuleExecutorFactory(List<ContractRuleExecutor> executors) {
        this.standaloneExecutors = executors;
    }

    public static ContractRuleExecutorFactory createStandalone(List<ContractRuleExecutor> executors) {
        return new ContractRuleExecutorFactory(executors);
    }

    public Optional<ContractRuleExecutor> getExecutor(String ruleType) {
        if (ruleType == null) {
            return Optional.empty();
        }
        if (standaloneExecutors != null) {
            return standaloneExecutors.stream()
                    .filter(e -> ruleType.equals(e.getRuleType()))
                    .findFirst();
        }
        return executors.stream()
                .filter(e -> ruleType.equals(e.getRuleType()))
                .findFirst();
    }
}
