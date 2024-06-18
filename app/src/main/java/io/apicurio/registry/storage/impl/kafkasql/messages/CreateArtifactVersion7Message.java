package io.apicurio.registry.storage.impl.kafkasql.messages;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
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
public class CreateArtifactVersion7Message extends AbstractMessage {

    String groupId;
    String artifactId;
    String version;
    String artifactType;
    private String contentType;
    private String content;
    private List<ArtifactReferenceDto> references;
    EditableVersionMetaDataDto metaData;
    List<String> branches;

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage#dispatchTo(io.apicurio.registry.storage.RegistryStorage)
     */
    @Override
    public Object dispatchTo(RegistryStorage storage) {
        ContentHandle handle = content != null ? ContentHandle.create(content) : null;
        ContentWrapperDto contentDto = content != null ? ContentWrapperDto.builder().contentType(contentType)
                .content(handle).references(references).build()
            : null;
        return storage.createArtifactVersion(groupId, artifactId, version, artifactType, contentDto, metaData,
                branches);
    }

}
