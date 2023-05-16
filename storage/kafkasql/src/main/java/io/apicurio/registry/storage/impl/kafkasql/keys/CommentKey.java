/*
 * Copyright 2023 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl.kafkasql.keys;

import java.util.UUID;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * @author eric.wittmann@gmail.com
 */
@RegisterForReflection
public class CommentKey extends AbstractMessageKey {

    private String groupId;
    private String artifactId;
    private String version;
    private String commentId;
    // Note: we never want to compact comments, so add a unique UUID to avoid log compaction.  However, we COULD implement
    //       log compaction for comments if we include the createdBy and createdOn fields for Update messages.  It would 
    //       require a change in the SQL layer to treat Updates as CreateOrUpdate.  My theory is that comments will not
    //       often be edited or deleted, which makes this a largely useless optimization.
    private final String uuid = UUID.randomUUID().toString();

    /**
     * Creator method.
     * @param tenantId
     * @param groupId
     * @param artifactId
     * @param version
     * @param commentId
     */
    public static final CommentKey create(String tenantId, String groupId, String artifactId, String version, String commentId) {
        CommentKey key = new CommentKey();
        key.setTenantId(tenantId);
        key.setGroupId(groupId);
        key.setArtifactId(artifactId);
        key.setVersion(version);
        key.setCommentId(commentId);
        return key;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.Comment;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return getTenantId() + "/" + groupId + "/" + artifactId + "/" + version;
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
     * @see java.lang.Object#toString()
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
