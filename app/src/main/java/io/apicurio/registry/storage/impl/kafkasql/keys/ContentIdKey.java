package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;


@RegisterForReflection
public class ContentIdKey implements MessageKey {

    private static final String CONTENT_ID_PARTITION_KEY = "__apicurio_registry_content_id__";

    private final String uuid = UUID.randomUUID().toString();

    /**
     * Creator method.
     *
     */
    public static final ContentIdKey create() {
        ContentIdKey key = new ContentIdKey();
        return key;
    }

    /**
     * @see MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.ContentId;
    }

    /**
     * @see MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return CONTENT_ID_PARTITION_KEY;
    }

    public String getUuid() {
        return uuid;
    }

    /**
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return String.format("ContentIdKey(super = %s)", super.toString());
    }

}
