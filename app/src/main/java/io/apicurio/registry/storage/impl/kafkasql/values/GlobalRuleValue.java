package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.ToString;


@RegisterForReflection
@ToString
public class GlobalRuleValue extends AbstractMessageValue {

    private RuleConfigurationDto config;

    /**
     * Creator method.
     * @param action
     * @param config
     */
    public static final GlobalRuleValue create(ActionType action, RuleConfigurationDto config) {
        GlobalRuleValue value = new GlobalRuleValue();
        value.setAction(action);
        value.setConfig(config);
        return value;
    }
    
    /**
     * @see MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.GlobalRule;
    }

    /**
     * @return the config
     */
    public RuleConfigurationDto getConfig() {
        return config;
    }

    /**
     * @param config the config to set
     */
    public void setConfig(RuleConfigurationDto config) {
        this.config = config;
    }

}
