package io.apicurio.registry.contracts.rules;

import java.util.List;
import java.util.Map;

public class RuleExecutionResult {
    private boolean passed;
    private Map<String, Object> transformedRecord;
    private List<RuleViolation> violations;
    private int executedRules;
    private int failedRules;

    public RuleExecutionResult() {
    }

    public RuleExecutionResult(boolean passed, Map<String, Object> transformedRecord,
            List<RuleViolation> violations, int executedRules, int failedRules) {
        this.passed = passed;
        this.transformedRecord = transformedRecord;
        this.violations = violations;
        this.executedRules = executedRules;
        this.failedRules = failedRules;
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

    public List<RuleViolation> getViolations() {
        return violations;
    }

    public void setViolations(List<RuleViolation> violations) {
        this.violations = violations;
    }

    public int getExecutedRules() {
        return executedRules;
    }

    public void setExecutedRules(int executedRules) {
        this.executedRules = executedRules;
    }

    public int getFailedRules() {
        return failedRules;
    }

    public void setFailedRules(int failedRules) {
        this.failedRules = failedRules;
    }
}
