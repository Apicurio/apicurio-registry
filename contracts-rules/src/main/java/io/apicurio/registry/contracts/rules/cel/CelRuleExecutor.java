package io.apicurio.registry.contracts.rules.cel;

import io.apicurio.registry.contracts.rules.ContractRuleContext;
import io.apicurio.registry.contracts.rules.ContractRuleExecutor;
import io.apicurio.registry.contracts.rules.ContractRuleResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@ApplicationScoped
public class CelRuleExecutor implements ContractRuleExecutor {

    private static final Logger log = LoggerFactory.getLogger(CelRuleExecutor.class);

    @Inject
    CelExpressionEvaluator evaluator;

    @Override
    public String getRuleType() {
        return "CEL";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ContractRuleResult execute(ContractRuleContext context) {
        String expr = context.getRule().getExpr();
        if (expr == null || expr.isBlank()) {
            return ContractRuleResult.pass();
        }

        try {
            Object result = evaluator.evaluate(expr, context.getRecord());

            if (context.getRule().isCondition()) {
                boolean passed = Boolean.TRUE.equals(result);
                return passed ? ContractRuleResult.pass()
                        : ContractRuleResult.fail(
                                "Condition failed: " + context.getRule().getName(),
                                context.getRule().getOnFailure());
            } else {
                Map<String, Object> transformed = result instanceof Map
                        ? (Map<String, Object>) result
                        : context.getRecord();
                return ContractRuleResult.transform(transformed);
            }
        } catch (Exception e) {
            log.warn("CEL evaluation failed for rule {}: {}",
                    context.getRule().getName(), e.getMessage());
            return ContractRuleResult.fail(
                    "CEL evaluation error: " + e.getMessage(),
                    context.getRule().getOnFailure());
        }
    }
}
