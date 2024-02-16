package io.apicurio.registry.storage.dto;

import io.apicurio.registry.types.ArtifactState;
import lombok.*;

import java.util.List;
import java.util.Map;

@NoArgsConstructor
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
    private String owner;
    private long createdOn;
    private String modifiedBy;
    private long modifiedOn;
    private String version;
    private int versionOrder;
    private long globalId;
    private long contentId;
    private String type;
    private ArtifactState state;
    private Map<String, String> labels;
    private List<ArtifactReferenceDto> references;
}
