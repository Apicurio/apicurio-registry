package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.ToString;

@RegisterForReflection
@ToString
public class ArtifactOwnerValue extends AbstractMessageValue {

    private String owner;

    /**
     * Creator method.
     * @param action
     * @param owner
     */
    public static final ArtifactOwnerValue create(ActionType action, String owner) {
        ArtifactOwnerValue value = new ArtifactOwnerValue();
        value.setAction(action);
        value.setOwner(owner);
        return value;
    }
    
    /**
     * @see MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.ArtifactOwner;
    }

    /**
     * @return the owner
     */
    public String getOwner() {
        return owner;
    }

    /**
     * @param owner the owner to set
     */
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
}
