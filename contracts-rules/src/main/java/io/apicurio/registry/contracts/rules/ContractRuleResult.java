package io.apicurio.registry.contracts.rules;

import java.util.Map;

public class ContractRuleResult {
    private boolean passed;
    private Map<String, Object> transformedRecord;
    private String message;
    private String suggestedAction;

    public ContractRuleResult() {
    }

    public static ContractRuleResult pass() {
        ContractRuleResult r = new ContractRuleResult();
        r.passed = true;
        return r;
    }

    public static ContractRuleResult fail(String message, String action) {
        ContractRuleResult r = new ContractRuleResult();
        r.passed = false;
        r.message = message;
        r.suggestedAction = action;
        return r;
    }

    public static ContractRuleResult transform(Map<String, Object> record) {
        ContractRuleResult r = new ContractRuleResult();
        r.passed = true;
        r.transformedRecord = record;
        return r;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    public void setSuggestedAction(String suggestedAction) {
        this.suggestedAction = suggestedAction;
    }
}
