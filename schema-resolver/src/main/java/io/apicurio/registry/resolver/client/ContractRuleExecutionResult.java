package io.apicurio.registry.resolver.client;

import java.util.List;
import java.util.Map;

public class ContractRuleExecutionResult {

    private boolean passed;
    private Map<String, Object> transformedRecord;
    private List<String> violations;

    public ContractRuleExecutionResult() {
    }

    public ContractRuleExecutionResult(boolean passed, Map<String, Object> transformedRecord,
            List<String> violations) {
        this.passed = passed;
        this.transformedRecord = transformedRecord;
        this.violations = violations;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public Map<String, Object> getTransformedRecord() {
        return transformedRecord;
    }

    public void setTransformedRecord(Map<String, Object> transformedRecord) {
        this.transformedRecord = transformedRecord;
    }

    public List<String> getViolations() {
        return violations;
    }

    public void setViolations(List<String> violations) {
        this.violations = violations;
    }
}
