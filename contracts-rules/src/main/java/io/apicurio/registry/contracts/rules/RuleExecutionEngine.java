package io.apicurio.registry.contracts.rules;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class RuleExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleExecutionEngine.class);

    @Inject
    ContractRuleExecutorFactory executorFactory;

    public RuleExecutionEngine() {
    }

    public RuleExecutionEngine(ContractRuleExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
    }

    public static RuleExecutionEngine createStandalone() {
        var celEvaluator = new io.apicurio.registry.contracts.rules.cel.CelExpressionEvaluator();
        var celExecutor = new io.apicurio.registry.contracts.rules.cel.CelRuleExecutor(celEvaluator);
        var celFieldExecutor = new io.apicurio.registry.contracts.rules.cel.CelFieldRuleExecutor(celEvaluator);
        var jsonataEvaluator = new io.apicurio.registry.contracts.rules.jsonata.JsonataExpressionEvaluator();
        var jsonataExecutor = new io.apicurio.registry.contracts.rules.jsonata.JsonataRuleExecutor(jsonataEvaluator);
        var factory = ContractRuleExecutorFactory.createStandalone(java.util.List.of(celExecutor, celFieldExecutor, jsonataExecutor));
        return new RuleExecutionEngine(factory);
    }

    public RuleExecutionResult execute(List<RuleDefinition> rules, String mode,
            Map<String, Object> dataRecord) {
        return execute(rules, mode, dataRecord, null);
    }

    public RuleExecutionResult execute(List<RuleDefinition> rules, String mode,
            Map<String, Object> dataRecord, Map<String, Set<String>> fieldTags) {
        List<RuleDefinition> applicable = rules.stream()
                .filter(r -> !r.isDisabled())
                .filter(r -> matchesMode(r.getMode(), mode))
                .sorted(Comparator.comparingInt(RuleDefinition::getOrderIndex))
                .toList();

        List<RuleViolation> violations = new ArrayList<>();
        Map<String, Object> current = dataRecord;
        int executed = 0;
        int failed = 0;

        for (RuleDefinition rule : applicable) {
            var executor = executorFactory.getExecutor(rule.getType());
            if (executor.isEmpty()) {
                log.warn("No executor for rule type: {}", rule.getType());
                continue;
            }

            var context = new ContractRuleContext(rule, current, fieldTags);
            ContractRuleResult result = executor.get().execute(context);
            executed++;

            if (!result.isPassed()) {
                failed++;
                violations.add(new RuleViolation(rule.getName(), result.getMessage(),
                        result.getSuggestedAction()));

                if ("ERROR".equals(result.getSuggestedAction())) {
                    break;
                }
            }

            if (rule.isTransform() && result.getTransformedRecord() != null) {
                current = result.getTransformedRecord();
            }
        }

        return new RuleExecutionResult(failed == 0,
                current != dataRecord ? current : null, violations, executed, failed);
    }

    private boolean matchesMode(String ruleMode, String requestedMode) {
        if (ruleMode == null || requestedMode == null) {
            return true;
        }
        if ("WRITEREAD".equals(ruleMode)) {
            return true;
        }
        return ruleMode.equals(requestedMode);
    }
}
