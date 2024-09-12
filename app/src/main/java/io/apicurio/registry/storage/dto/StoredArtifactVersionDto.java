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
public class StoredArtifactVersionDto {

    private Long globalId;

    // TODO add artifactId

    private String version;

    private int versionOrder;

    private Long contentId;

    private ContentHandle content;

    private String contentType;

    private List<ArtifactReferenceDto> references;
}
