package io.apicurio.registry.storage.dto;

import io.apicurio.registry.content.ContentHandle;
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
 * DTO bundling version metadata and content for streaming reindex. Combines the fields from
 * {@link ArtifactVersionMetaDataDto} with the artifact content, allowing the startup reindexer
 * to process each version in a single pass without additional queries.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class VersionContentDto {

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
    private ContentHandle content;

    /**
     * Converts this DTO to an {@link ArtifactVersionMetaDataDto}, which is the format expected
     * by the search index document builder.
     *
     * @return a new ArtifactVersionMetaDataDto populated from this DTO's fields
     */
    public ArtifactVersionMetaDataDto toMetaDataDto() {
        return ArtifactVersionMetaDataDto.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .versionOrder(versionOrder)
                .globalId(globalId)
                .contentId(contentId)
                .name(name)
                .description(description)
                .owner(owner)
                .createdOn(createdOn)
                .modifiedBy(modifiedBy)
                .modifiedOn(modifiedOn)
                .artifactType(artifactType)
                .state(state)
                .labels(labels)
                .build();
    }
}
