package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class ConfigPropertyValue extends AbstractMessageValue {

    private String value;

    /**
     * Creator method.
     * @param action
     * @param downloadContext
     */
    public static final ConfigPropertyValue create(ActionType action, String value) {
        ConfigPropertyValue cpv = new ConfigPropertyValue();
        cpv.setAction(action);
        cpv.setValue(value);
        return cpv;
    }

    /**
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * @param value the value to set
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * @see MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.ConfigProperty;
    }

    /**
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return "ConfigPropertyValue [value=" + value + "]";
    }

}
