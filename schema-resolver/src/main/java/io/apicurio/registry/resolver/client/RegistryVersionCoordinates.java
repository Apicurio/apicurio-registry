package io.apicurio.registry.resolver.client;

public class RegistryVersionCoordinates {

    public static RegistryVersionCoordinates create(Long globalId, Long contentId, String groupId, String artifactId, String version) {
        RegistryVersionCoordinates rvc = new RegistryVersionCoordinates();
        rvc.setGlobalId(globalId);
        rvc.setContentId(contentId);
        rvc.setGroupId(groupId);
        rvc.setArtifactId(artifactId);
        rvc.setVersion(version);
        return rvc;
    }

    private Long globalId;
    private Long contentId;
    private String groupId;
    private String artifactId;
    private String version;

    protected RegistryVersionCoordinates() {
    }

    public Long getGlobalId() {
        return globalId;
    }
    public Long getContentId() {
        return contentId;
    }
    public String getGroupId() {
        return groupId;
    }
    public String getArtifactId() {
        return artifactId;
    }
    public String getVersion() {
        return version;
    }

    public void setGlobalId(Long globalId) {
        this.globalId = globalId;
    }
    public void setContentId(Long contentId) {
        this.contentId = contentId;
    }
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }
    public void setVersion(String version) {
        this.version = version;
    }

}