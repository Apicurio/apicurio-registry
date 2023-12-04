package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.ToString;


@RegisterForReflection
@ToString
public class GlobalIdValue extends AbstractMessageValue {

    /**
     * Creator method.
     * @param action
     */
    public static final GlobalIdValue create(ActionType action) {
        GlobalIdValue value = new GlobalIdValue();
        value.setAction(action);
        return value;
    }

    /**
     * @see MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.GlobalId;
    }

}
