package io.apicurio.registry.maven;

import io.apicurio.registry.rest.v3.beans.IfArtifactExists;

import java.io.File;
import java.util.List;

public class RegisterArtifact {

    private String groupId;
    private String artifactId;
    private String version;
    private String artifactType;
    private File file;
    private IfArtifactExists ifExists;
    private Boolean canonicalize;
    private Boolean minify;
    private Boolean analyzeDirectory;
    private Boolean autoRefs;
    private String contentType;
    private List<RegisterArtifactReference> references;
    private List<ExistingReference> existingReferences;

    /**
     * Constructor.
     */
    public RegisterArtifact() {
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
     * @return the ifExists
     */
    public IfArtifactExists getIfExists() {
        return ifExists;
    }

    /**
     * @param ifExists the ifExists to set
     */
    public void setIfExists(IfArtifactExists ifExists) {
        this.ifExists = ifExists;
    }

    /**
     * @return the canonicalize
     */
    public Boolean getCanonicalize() {
        return canonicalize;
    }

    /**
     * @param canonicalize the canonicalize to set
     */
    public void setCanonicalize(Boolean canonicalize) {
        this.canonicalize = canonicalize;
    }

    /**
     * @return the minify
     */
    public Boolean getMinify() {
        return minify;
    }

    /**
     * @param minify the minify to set
     */
    public void setMinify(Boolean minify) {
        this.minify = minify;
    }

    /**
     * @return the artifactType
     */
    public String getArtifactType() {
        return artifactType;
    }

    /**
     * @param artifactType the artifact type to set
     */
    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
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
     * @return the content type
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * @param contentType the contentType to set
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * @return the referenced artifacts
     */
    public List<RegisterArtifactReference> getReferences() {
        return references;
    }

    /**
     * @param references the references to set
     */
    public void setReferences(List<RegisterArtifactReference> references) {
        this.references = references;
    }

    public Boolean getAnalyzeDirectory() {
        return analyzeDirectory;
    }

    public void setAnalyzeDirectory(Boolean analyzeDirectory) {
        this.analyzeDirectory = analyzeDirectory;
    }

    public Boolean getAutoRefs() {
        return autoRefs;
    }

    public void setAutoRefs(Boolean autoRefs) {
        this.autoRefs = autoRefs;
    }

    public List<ExistingReference> getExistingReferences() {
        return existingReferences;
    }

    public void setExistingReferences(List<ExistingReference> existingReferences) {
        this.existingReferences = existingReferences;
    }
}
