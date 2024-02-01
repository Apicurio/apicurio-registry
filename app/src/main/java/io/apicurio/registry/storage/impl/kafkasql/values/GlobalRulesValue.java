package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.ToString;

@RegisterForReflection
@ToString
public class GlobalRulesValue extends AbstractMessageValue {

    /**
     * Creator method.
     * @param action
     */
    public static final GlobalRulesValue create(ActionType action) {
        GlobalRulesValue value = new GlobalRulesValue();
        value.setAction(action);
        return value;
    }
    
    /**
     * @see MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.GlobalRules;
    }

}
