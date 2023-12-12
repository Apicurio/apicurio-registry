package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.apicurio.registry.types.RuleType;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class GlobalRuleKey implements MessageKey {

    private static final String GLOBAL_RULE_PARTITION_KEY = "__apicurio_registry_global_rule__";

    private RuleType ruleType;

    /**
     * Creator method.
     * 
     * @param ruleType
     */
    public static final GlobalRuleKey create(RuleType ruleType) {
        GlobalRuleKey key = new GlobalRuleKey();
        key.setRuleType(ruleType);
        return key;
    }

    /**
     * @see MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.GlobalRule;
    }

    /**
     * @see MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return GLOBAL_RULE_PARTITION_KEY;
    }

    /**
     * @return the ruleType
     */
    public RuleType getRuleType() {
        return ruleType;
    }

    /**
     * @param ruleType the ruleType to set
     */
    public void setRuleType(RuleType ruleType) {
        this.ruleType = ruleType;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.AbstractMessageKey#toString()
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[ruleType=" + getRuleType() + "]";
    }

}
