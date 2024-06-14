package io.apicurio.registry.storage.dto;

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
public class SearchedBranchDto {

    private String groupId;
    private String artifactId;
    private String branchId;
    private String description;
    private boolean systemDefined;
    private String owner;
    private long createdOn;
    private String modifiedBy;
    private long modifiedOn;

}
