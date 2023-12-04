package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.apicurio.registry.types.ArtifactState;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.ToString;


@RegisterForReflection
@ToString
public class ArtifactVersionValue extends AbstractMessageValue {

    private ArtifactState state;
    private EditableArtifactMetaDataDto metaData;

    /**
     * Creator method.
     * @param action
     * @param state
     * @param metaData
     */
    public static final ArtifactVersionValue create(ActionType action, ArtifactState state, EditableArtifactMetaDataDto metaData) {
        ArtifactVersionValue value = new ArtifactVersionValue();
        value.setAction(action);
        value.setState(state);
        value.setMetaData(metaData);
        return value;
    }
    
    /**
     * @see MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.ArtifactVersion;
    }
    
    /**
     * @return the metaData
     */
    public EditableArtifactMetaDataDto getMetaData() {
        return metaData;
    }

    /**
     * @param metaData the metaData to set
     */
    public void setMetaData(EditableArtifactMetaDataDto metaData) {
        this.metaData = metaData;
    }

    /**
     * @return the state
     */
    public ArtifactState getState() {
        return state;
    }

    /**
     * @param state the state to set
     */
    public void setState(ArtifactState state) {
        this.state = state;
    }

}
