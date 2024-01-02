package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class ArtifactReferenceDto {

    private String groupId;
    private String artifactId;
    private String version;
    private String name;

    /**
     * Constructor
     */
    public ArtifactReferenceDto() {
    }

    /**
     * @return the groupId
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * @param groupId to set
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    /**
     * @return the artifactId
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * @param artifactId to set
     */
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    /**
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * @param version to set
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name to set
     */
    public void setName(String name) {
        this.name = name;
    }
}