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

    // TODO Can the CH be used multiple times?
    private ContentHandle content;

    private List<ArtifactReferenceDto> references; //TODO create a new class StoredArtifactReference?
}
