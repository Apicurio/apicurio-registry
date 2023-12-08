package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class GroupKey implements MessageKey {

    private String groupId;

    /**
     * Creator method.
     * @param groupId
     */
    public static final GroupKey create(String groupId) {
        GroupKey key = new GroupKey();
        key.setGroupId(groupId);
        return key;
    }

    /**
     * @see MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.Group;
    }

    /**
     * @see MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return groupId;
    }

    /**
     * @return the groupId
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * @param groupId the groupId to set
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    /**
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return "GroupKey [groupId=" + groupId + "]";
    }

}
