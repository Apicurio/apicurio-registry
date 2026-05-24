package io.apicurio.registry.contracts.rules;

public interface ContractRuleExecutor {
    String getRuleType();

    ContractRuleResult execute(ContractRuleContext context);
}
