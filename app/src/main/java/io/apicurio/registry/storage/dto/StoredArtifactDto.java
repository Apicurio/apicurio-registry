package io.apicurio.registry.storage.dto;

import io.apicurio.registry.content.ContentHandle;
import lombok.Builder;
import lombok.Value;

import java.util.List;


@Value
@Builder
public class StoredArtifactDto {

    private Long globalId;

    // TODO add artifactId

    private String version;

    private int versionId;

    private Long contentId;

    // TODO Can the CH be used multiple times?
    private ContentHandle content;

    private List<ArtifactReferenceDto> references; //TODO create a new class StoredArtifactReference?

}
