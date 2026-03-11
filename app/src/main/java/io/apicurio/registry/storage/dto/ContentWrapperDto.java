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
public class ContentWrapperDto implements HasReferences {

    private String contentType;
    private ContentHandle content;
    private List<ArtifactReferenceDto> references;
    private String artifactType;
    private transient String contentHash;
}
