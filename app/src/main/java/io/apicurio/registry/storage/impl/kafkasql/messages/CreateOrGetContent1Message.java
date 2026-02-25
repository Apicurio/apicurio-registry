package io.apicurio.registry.storage.impl.kafkasql.messages;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
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
public class CreateOrGetContent1Message extends AbstractMessage {

    private String artifactType;
    private String contentType;
    private String content;
    private List<ArtifactReferenceDto> references;

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage#dispatchTo(RegistryStorage)
     */
    @Override
    public Object dispatchTo(RegistryStorage storage) {
        ContentHandle handle = ContentHandle.create(content);
        ContentWrapperDto contentDto = ContentWrapperDto.builder()
                .contentType(contentType)
                .content(handle)
                .references(references)
                .build();
        return storage.createOrGetContent(artifactType, contentDto);
    }

}
