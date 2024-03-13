package io.apicurio.registry.storage.dto;

import java.util.Date;

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
public class SearchedArtifactDto {

    private String groupId;
    private String artifactId;
    private String name;
    private String description;
    private Date createdOn;
    private String owner;
    private String type;
    private Date modifiedOn;
    private String modifiedBy;
}
