package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;


@RegisterForReflection
public class ConfigPropertyKey implements MessageKey {

    private String propertyName;

    /**
     * Creator method.
     * @param propertyName
     */
    public static final ConfigPropertyKey create(String propertyName) {
        ConfigPropertyKey key = new ConfigPropertyKey();
        key.setPropertyName(propertyName);
        return key;
    }

    /**
     * @see MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.ConfigProperty;
    }

    /**
     * @see MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return getPropertyName();
    }

    /**
     * @return the propertyName
     */
    public String getPropertyName() {
        return propertyName;
    }

    /**
     * @param propertyName the propertyName to set
     */
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    /**
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return "ConfigPropertyKey [propertyName=" + propertyName + "]";
    }

}
