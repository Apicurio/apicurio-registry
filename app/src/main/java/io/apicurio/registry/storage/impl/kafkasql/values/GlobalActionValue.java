package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.ToString;

/**
 * Value that carries no additional information.
 * Does not apply to a specific resource, but to the entire node.
 * The only content is the action being performed.
 *
 */
@RegisterForReflection
@ToString
public class GlobalActionValue extends AbstractMessageValue {

    /**
     * Creator method.
     * @param action
     */
    public static GlobalActionValue create(ActionType action) {
        GlobalActionValue value = new GlobalActionValue();
        value.setAction(action);
        return value;
    }

    /**
     * @see MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.GlobalAction;
    }
}
