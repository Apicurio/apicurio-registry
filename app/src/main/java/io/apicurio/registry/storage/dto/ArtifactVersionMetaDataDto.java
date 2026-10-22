package io.apicurio.registry.storage.dto;

import io.apicurio.registry.types.VersionState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

/**
 * Data transfer object representing the metadata of a specific artifact version. This includes the version
 * identifier, globalId, contentId, version state, artifact type, labels, and timestamps. Each artifact
 * version is an immutable snapshot of an artifact's content.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class ArtifactVersionMetaDataDto {

    private String groupId;
    private String artifactId;
    private String version;
    private int versionOrder;
    private long globalId;
    private long contentId;
    private String name;
    private String description;
    private String owner;
    private long createdOn;
    private String modifiedBy;
    private long modifiedOn;
    private String artifactType;
    private VersionState state;
    private Map<String, String> labels;
}
