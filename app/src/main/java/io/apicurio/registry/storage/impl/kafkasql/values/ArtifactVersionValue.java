package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.ToString;

@RegisterForReflection
@ToString
public class ArtifactVersionValue extends AbstractMessageValue {

    private EditableVersionMetaDataDto metaData;

    /**
     * Creator method.
     * @param action
     * @param metaData
     */
    public static final ArtifactVersionValue create(ActionType action, EditableVersionMetaDataDto metaData) {
        ArtifactVersionValue value = new ArtifactVersionValue();
        value.setAction(action);
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
    public EditableVersionMetaDataDto getMetaData() {
        return metaData;
    }

    /**
     * @param metaData the metaData to set
     */
    public void setMetaData(EditableVersionMetaDataDto metaData) {
        this.metaData = metaData;
    }

}
