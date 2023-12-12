package io.apicurio.registry.storage.impl.kafkasql.values;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;

public interface MessageValue {

    /**
     * Returns the message type.
     */
    @JsonIgnore
    public MessageType getType();

}
