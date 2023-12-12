package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Key that carries no additional information. Does not apply to a specific resource, but to the entire node.
 */
@RegisterForReflection
public class GlobalActionKey implements MessageKey {

    private static final String EMPTY_PARTITION_KEY = "__apicurio_registry_global_action__";

    public static GlobalActionKey create() {
        return new GlobalActionKey();
    }

    /**
     * @see MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.GlobalAction;
    }

    /**
     * @see MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return EMPTY_PARTITION_KEY;
    }

    /**
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return String.format("GlobalKey(super = %s)", super.toString());
    }
}
