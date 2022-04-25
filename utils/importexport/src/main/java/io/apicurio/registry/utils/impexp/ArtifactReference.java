package io.apicurio.registry.utils.impexp;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ArtifactReference {

    private String groupId;
    private String artifactId;
    private String version;
    private String name;

    /**
     * Constructor
     */
    public ArtifactReference() {
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
