package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class ArtifactVersionKey implements MessageKey {

    private String groupId;
    private String artifactId;
    private String version;

    /**
     * Creator method.
     * @param groupId
     * @param artifactId
     * @param version
     */
    public static final ArtifactVersionKey create(String groupId, String artifactId, String version) {
        ArtifactVersionKey key = new ArtifactVersionKey();
        key.setGroupId(groupId);
        key.setArtifactId(artifactId);
        key.setVersion(version);
        return key;
    }

    /**
     * @see MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.ArtifactVersion;
    }

    /**
     * @see MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return groupId + "/" + artifactId;
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
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return "ArtifactVersionKey [groupId=" + groupId + ", artifactId=" + artifactId + ", version="
                + version + "]";
    }

}
