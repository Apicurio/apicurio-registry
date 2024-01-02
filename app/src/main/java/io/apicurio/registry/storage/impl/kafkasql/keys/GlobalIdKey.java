package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

@RegisterForReflection
public class GlobalIdKey implements MessageKey {

    private static final String GLOBAL_ID_PARTITION_KEY = "__apicurio_registry_global_id__";

    private final String uuid = UUID.randomUUID().toString();

    /**
     * Creator method.
     */
    public static final GlobalIdKey create() {
        return new GlobalIdKey();
    }

    /**
     * @see MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.GlobalId;
    }

    /**
     * @see MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return GLOBAL_ID_PARTITION_KEY;
    }

    public String getUuid() {
        return uuid;
    }

    /**
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return String.format("GlobalIdKey(super = %s)", super.toString());
    }

}
