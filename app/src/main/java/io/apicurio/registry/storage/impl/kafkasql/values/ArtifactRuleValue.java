package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.ToString;


@RegisterForReflection
@ToString
public class ArtifactRuleValue extends AbstractMessageValue {

    private RuleConfigurationDto config;

    /**
     * Creator method.
     * @param action
     * @param config
     */
    public static final ArtifactRuleValue create(ActionType action, RuleConfigurationDto config) {
        ArtifactRuleValue value = new ArtifactRuleValue();
        value.setAction(action);
        value.setConfig(config);
        return value;
    }
    
    /**
     * @see MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.ArtifactRule;
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
