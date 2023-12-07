package io.apicurio.registry.storage.dto;

import io.apicurio.registry.types.ArtifactState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class ArtifactMetaDataDto {

    private String groupId;
    private String id;
    private String name;
    private String description;
    private String createdBy;
    private long createdOn;
    private String modifiedBy;
    private long modifiedOn;
    private String version;
    private int versionId;
    private long globalId;
    private long contentId;
    private String type;
    private ArtifactState state;
    private List<String> labels;
    private Map<String, String> properties;
    private List<ArtifactReferenceDto> references;

    public ArtifactMetaDataDto() {
    }
}
