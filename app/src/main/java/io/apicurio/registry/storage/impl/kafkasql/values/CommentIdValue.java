package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.ToString;

@RegisterForReflection
@ToString
public class CommentIdValue extends AbstractMessageValue {

    /**
     * Creator method.
     * @param action
     */
    public static final CommentIdValue create(ActionType action) {
        CommentIdValue value = new CommentIdValue();
        value.setAction(action);
        return value;
    }

    /**
     * @see MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.CommentId;
    }

}
