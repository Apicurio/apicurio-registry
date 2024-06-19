package io.apicurio.registry.storage.impl.kafkasql.messages;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.impl.kafkasql.AbstractMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@ToString
public class CreateArtifact8Message extends AbstractMessage {

    private String groupId;
    private String artifactId;
    private String artifactType;
    private EditableArtifactMetaDataDto artifactMetaDataDto;
    private String version;
    private String contentType;
    private String content;
    private List<ArtifactReferenceDto> references;
    private EditableVersionMetaDataDto versionMetaData;
    private List<String> versionBranches;

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage#dispatchTo(io.apicurio.registry.storage.RegistryStorage)
     */
    @Override
    public Object dispatchTo(RegistryStorage storage) {
        ContentHandle handle = content != null ? ContentHandle.create(content) : null;
        ContentWrapperDto versionContent = content != null ? ContentWrapperDto.builder()
                .contentType(contentType).content(handle).references(references).build()
            : null;
        return storage.createArtifact(groupId, artifactId, artifactType, artifactMetaDataDto, version,
                versionContent, versionMetaData, versionBranches);
    }

}
