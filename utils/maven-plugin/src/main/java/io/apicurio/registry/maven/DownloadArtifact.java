package io.apicurio.registry.maven;

import java.io.File;
import java.util.List;


public class DownloadArtifact {

    private String groupId;
    private String artifactId;
    private String version;
    private File file;
    private Boolean overwrite;
    private List<DownloadArtifact> artifactReferences;

    /**
     * Constructor.
     */
    public DownloadArtifact() {
    }

    /**
     * @return the groupId
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * @param groupId the groupId to set
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
     * @param artifactId the artifactId to set
     */
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    /**
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * @param file the file to set
     */
    public void setFile(File file) {
        this.file = file;
    }

    /**
     * @return the overwrite
     */
    public Boolean getOverwrite() {
        return overwrite;
    }

    /**
     * @param overwrite the overwrite to set
     */
    public void setOverwrite(Boolean overwrite) {
        this.overwrite = overwrite;
    }

    /**
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * @return the artifactReferences
     */
    public List<DownloadArtifact> getArtifactReferences() {
        return artifactReferences;
    }

    /**
     * @param artifactReferences the references to set
     */
    public void setArtifactReferences(List<DownloadArtifact> artifactReferences) {
        this.artifactReferences = artifactReferences;
    }
}
