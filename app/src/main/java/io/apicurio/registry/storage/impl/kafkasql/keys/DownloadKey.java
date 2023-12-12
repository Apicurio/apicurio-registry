package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class DownloadKey implements MessageKey {

    private static final String DOWNLOAD_PARTITION_KEY = "__apicurio_registry_download__";

    private String downloadId;

    /**
     * Creator method.
     * 
     * @param downloadId
     */
    public static final DownloadKey create(String downloadId) {
        DownloadKey key = new DownloadKey();
        key.setDownloadId(downloadId);
        return key;
    }

    /**
     * @see MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.Download;
    }

    /**
     * @see MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return DOWNLOAD_PARTITION_KEY;
    }

    /**
     * @return the downloadId
     */
    public String getDownloadId() {
        return downloadId;
    }

    /**
     * @param downloadId the downloadId to set
     */
    public void setDownloadId(String downloadId) {
        this.downloadId = downloadId;
    }

    /**
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return "DownloadKey [downloadId=" + downloadId + "]";
    }

}
