package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class ContentKey implements MessageKey {

    private String contentHash;
    private long contentId;

    /**
     * Creator method.
     * @param contentId
     * @param contentHash
     */
    public static final ContentKey create(long contentId, String contentHash) {
        ContentKey key = new ContentKey();
        key.setContentId(contentId);
        key.setContentHash(contentHash);
        return key;
    }

    /**
     * @see MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.Content;
    }

    /**
     * @see MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return contentHash;
    }

    /**
     * @return the contentHash
     */
    public String getContentHash() {
        return contentHash;
    }

    /**
     * @param contentHash the contentHash to set
     */
    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    /**
     * @return the contentId
     */
    public long getContentId() {
        return contentId;
    }

    /**
     * @param contentId the contentId to set
     */
    public void setContentId(long contentId) {
        this.contentId = contentId;
    }

    /**
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return "ContentKey [contentHash=" + contentHash + ", contentId=" + contentId + "]";
    }

}
