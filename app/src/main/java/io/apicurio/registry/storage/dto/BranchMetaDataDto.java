package io.apicurio.registry.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Data transfer object representing the metadata of an artifact branch. A branch is a named, ordered sequence
 * of artifact versions within an artifact, used to track parallel evolution paths (e.g. {@code latest},
 * {@code 1.x}).
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class BranchMetaDataDto {

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
