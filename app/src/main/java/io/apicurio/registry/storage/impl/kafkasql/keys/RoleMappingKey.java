package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class RoleMappingKey implements MessageKey {

    private static final String ROLE_MAPPING_PARTITION_KEY = "__apicurio_registry_role_mapping__";

    private String principalId;

    /**
     * Creator method.
     * @param principalId
     */
    public static final RoleMappingKey create(String principalId) {
        RoleMappingKey key = new RoleMappingKey();
        key.setString(principalId);
        return key;
    }

    /**
     * @see MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.RoleMapping;
    }

    /**
     * @see MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return ROLE_MAPPING_PARTITION_KEY;
    }

    /**
     * @return the principalId
     */
    public String getPrincipalId() {
        return principalId;
    }

    /**
     * @param principalId the principalId to set
     */
    public void setString(String principalId) {
        this.principalId = principalId;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.AbstractMessageKey#toString()
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[principalId=" + getPrincipalId() + "]";
    }

}
