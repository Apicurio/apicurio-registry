package io.apicurio.registry.contracts.rules;

import java.util.Map;
import java.util.Set;

public class ContractRuleContext {
    private RuleDefinition rule;
    private Map<String, Object> record;
    private Map<String, Set<String>> fieldTags;

    public ContractRuleContext() {
    }

    public ContractRuleContext(RuleDefinition rule, Map<String, Object> record,
            Map<String, Set<String>> fieldTags) {
        this.rule = rule;
        this.record = record;
        this.fieldTags = fieldTags;
    }

    public RuleDefinition getRule() {
        return rule;
    }

    public void setRule(RuleDefinition rule) {
        this.rule = rule;
    }

    public Map<String, Object> getRecord() {
        return record;
    }

    public void setRecord(Map<String, Object> record) {
        this.record = record;
    }

    public Map<String, Set<String>> getFieldTags() {
        return fieldTags;
    }

    public void setFieldTags(Map<String, Set<String>> fieldTags) {
        this.fieldTags = fieldTags;
    }
}
