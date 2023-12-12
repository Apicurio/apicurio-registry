package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class BootstrapKey implements MessageKey {

    private String bootstrapId;

    /**
     * Creator method.
     * 
     * @param bootstrapId
     */
    public static final BootstrapKey create(String bootstrapId) {
        BootstrapKey key = new BootstrapKey();
        key.setBootstrapId(bootstrapId);
        return key;
    }

    /**
     * @see MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.Bootstrap;
    }

    /**
     * @see MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return "__bootstrap_message__";
    }

    /**
     * @return the bootstrapId
     */
    public String getBootstrapId() {
        return bootstrapId;
    }

    /**
     * @param bootstrapId the bootstrapId to set
     */
    public void setBootstrapId(String bootstrapId) {
        this.bootstrapId = bootstrapId;
    }

}
