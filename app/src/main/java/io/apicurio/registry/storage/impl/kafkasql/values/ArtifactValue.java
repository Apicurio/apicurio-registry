package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.apicurio.registry.types.ArtifactState;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@RegisterForReflection
@Getter
@Setter
@ToString
public class ArtifactValue extends ArtifactVersionValue {

    private Long globalId;
    private String version;
    private String artifactType;
    private String contentHash;
    private String owner;
    private Date createdOn;
    private Integer versionOrder;
    private Long contentId;


    public static ArtifactValue create(ActionType action, Long globalId, String version, String artifactType, String contentHash,
                                       String owner, Date createdOn, EditableArtifactMetaDataDto metaData, Integer versionOrder, ArtifactState state, Long contentId) {
        ArtifactValue value = new ArtifactValue();
        value.setAction(action);
        value.setGlobalId(globalId);
        value.setVersion(version);
        value.setArtifactType(artifactType);
        value.setContentHash(contentHash);
        value.setOwner(owner);
        value.setCreatedOn(createdOn);
        value.setMetaData(metaData);
        value.setVersionOrder(versionOrder);
        value.setState(state);
        value.setContentId(contentId);
        return value;
    }


    @Override
    public MessageType getType() {
        return MessageType.Artifact;
    }
}
