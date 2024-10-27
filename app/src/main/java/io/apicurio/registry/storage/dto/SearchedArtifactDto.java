package io.apicurio.registry.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class SearchedArtifactDto {

    private String groupId;
    private String artifactId;
    private String name;
    private String description;
    private Date createdOn;
    private String owner;
    private String artifactType;
    private Date modifiedOn;
    private String modifiedBy;
    private Map<String, String> labels;

}
