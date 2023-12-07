package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

@RegisterForReflection
public class ArtifactKey implements MessageKey {

    private String groupId;
    private String artifactId;
    private final String uuid = UUID.randomUUID().toString();

    /**
     * Creator method.
     * @param groupId
     * @param artifactId
     */
    public static final ArtifactKey create(String groupId, String artifactId) {
        ArtifactKey key = new ArtifactKey();
        key.setGroupId(groupId);
        key.setArtifactId(artifactId);
        return key;
    }

    /**
     * @see MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.Artifact;
    }

    /**
     * @see MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return groupId + "/" + artifactId;
    }

    /**
     * @return the uuid
     */
    public String getUuid() {
        return uuid;
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
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return "ArtifactKey [groupId=" + groupId + ", artifactId=" + artifactId + "]";
    }

}
