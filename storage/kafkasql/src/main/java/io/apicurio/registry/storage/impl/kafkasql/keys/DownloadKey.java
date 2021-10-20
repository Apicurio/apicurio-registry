/*
 * Copyright 2020 Red Hat
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

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * @author eric.wittmann@gmail.com
 */
@RegisterForReflection
public class DownloadKey extends AbstractMessageKey {

    private static final String DOWNLOAD_PARTITION_KEY = "__apicurio_registry_download__";

    private String downloadId;

    /**
     * Creator method.
     * @param tenantId
     * @param downloadId
     */
    public static final DownloadKey create(String tenantId, String downloadId) {
        DownloadKey key = new DownloadKey();
        key.setTenantId(tenantId);
        key.setDownloadId(downloadId);
        return key;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.Download;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return getTenantId() + DOWNLOAD_PARTITION_KEY;
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
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "DownloadKey [downloadId=" + downloadId + "]";
    }

}
