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

/**
 * Data transfer object that wraps artifact content together with its associated metadata, including the
 * content ID, content hash, and any artifact references contained within the content.
 */
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
