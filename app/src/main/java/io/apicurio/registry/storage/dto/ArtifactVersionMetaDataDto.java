package io.apicurio.registry.storage.dto;

import java.util.Map;

import io.apicurio.registry.types.ArtifactState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class ArtifactVersionMetaDataDto {

    private String version;
    private int versionOrder;
    private long globalId;
    private long contentId;
    private String name;
    private String description;
    private String owner;
    private long createdOn;
    private String type;
    private ArtifactState state;
    private Map<String, String> labels;
}
