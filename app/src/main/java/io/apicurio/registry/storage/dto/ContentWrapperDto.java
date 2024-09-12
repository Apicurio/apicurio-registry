package io.apicurio.registry.storage.dto;

import io.apicurio.registry.content.ContentHandle;
import lombok.*;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class ContentWrapperDto {

    private String contentType;
    private ContentHandle content;
    private List<ArtifactReferenceDto> references;
}
