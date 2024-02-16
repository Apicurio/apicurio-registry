package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

@RegisterForReflection
public class CommentKey implements MessageKey {

    private String groupId;
    private String artifactId;
    private String version;
    private String commentId;
    // Note: we never want to compact comments, so add a unique UUID to avoid log compaction.  However, we COULD implement
    //       log compaction for comments if we include the owner and createdOn fields for Update messages.  It would 
    //       require a change in the SQL layer to treat Updates as CreateOrUpdate.  My theory is that comments will not
    //       often be edited or deleted, which makes this a largely useless optimization.
    private final String uuid = UUID.randomUUID().toString();

    /**
     * Creator method.
     * @param groupId
     * @param artifactId
     * @param version
     * @param commentId
     */
    public static final CommentKey create(String groupId, String artifactId, String version, String commentId) {
        CommentKey key = new CommentKey();
        key.setGroupId(groupId);
        key.setArtifactId(artifactId);
        key.setVersion(version);
        key.setCommentId(commentId);
        return key;
    }

    /**
     * @see MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.Comment;
    }

    /**
     * @see MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return groupId + "/" + artifactId + "/" + version;
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
     * @return the commentId
     */
    public String getCommentId() {
        return commentId;
    }

    /**
     * @param commentId the commentId to set
     */
    public void setCommentId(String commentId) {
        this.commentId = commentId;
    }

    /**
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return "CommentKey [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + getVersion()
                + ", commentId=" + getCommentId() + "]";
    }

    /**
     * @return the uuid
     */
    public String getUuid() {
        return uuid;
    }

}
