package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;


@RegisterForReflection
public class CommentIdKey implements MessageKey {

    private static final String COMMENT_ID_PARTITION_KEY = "__apicurio_registry_comment_id__";

    private final String uuid = UUID.randomUUID().toString();

    /**
     * Creator method.
     *
     */
    public static final CommentIdKey create() {
        return new CommentIdKey();
    }

    /**
     * @see MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.CommentId;
    }

    /**
     * @see MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return COMMENT_ID_PARTITION_KEY;
    }

    public String getUuid() {
        return uuid;
    }

    /**
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return String.format("CommentIdKey(super = %s)", super.toString());
    }

}
