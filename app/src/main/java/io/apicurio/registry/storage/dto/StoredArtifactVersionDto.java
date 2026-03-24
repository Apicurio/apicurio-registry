package io.apicurio.registry.storage.dto;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils.HasReferences;
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
@EqualsAndHashCode
@ToString
public class StoredArtifactVersionDto implements HasReferences {

    private Long globalId;

    // TODO add artifactId

    private String version;

    private int versionOrder;

    private Long contentId;

    private ContentHandle content;

    private String contentType;

    private List<ArtifactReferenceDto> references;
}
