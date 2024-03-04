package io.apicurio.registry.storage.dto;

import java.util.Map;

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
public class ArtifactMetaDataDto {

    private String groupId;
    private String artifactId;
    private String name;
    private String description;
    private String owner;
    private long createdOn;
    private String modifiedBy;
    private long modifiedOn;
    private String type;
    private Map<String, String> labels;
}
