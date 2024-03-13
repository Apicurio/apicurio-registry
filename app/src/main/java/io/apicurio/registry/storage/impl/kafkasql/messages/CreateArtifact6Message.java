package io.apicurio.registry.storage.impl.kafkasql.messages;

import java.util.List;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.impl.kafkasql.AbstractMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@ToString
public class CreateArtifact6Message extends AbstractMessage {

    private String groupId;
    private String artifactId;
    private String version;
    private String artifactType;
    private String content;
    private List<ArtifactReferenceDto> references;

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage#dispatchTo(io.apicurio.registry.storage.RegistryStorage)
     */
    @Override
    public Object dispatchTo(RegistryStorage storage) {
        ContentHandle handle = ContentHandle.create(content);
        return storage.createArtifact(groupId, artifactId, version, artifactType, handle, references);
    }

}
