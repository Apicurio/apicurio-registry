/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.storage.impl;

import java.io.Serializable;

/**
 * A single piece of stored content.  We only store content once - so if the same artifact is uploaded
 * multiple times, we only store it once and reference it.
 * 
 * CREATE TABLE content (contentId BIGINT AUTO_INCREMENT NOT NULL, canonicalHash VARCHAR(64) NOT NULL, contentHash VARCHAR(64) NOT NULL, content BLOB NOT NULL);
 * 
 * @author eric.wittmann@gmail.com
 */
public class StoredContent implements Serializable {
    
    private static final long serialVersionUID = 308691740026159435L;
    
    private long contentId;
    private String contentHash;
    private String canonicalHash;
    private byte [] content;
    
    /**
     * Constructor.
     */
    public StoredContent() {
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
     * @return the canonicalHash
     */
    public String getCanonicalHash() {
        return canonicalHash;
    }

    /**
     * @param canonicalHash the canonicalHash to set
     */
    public void setCanonicalHash(String canonicalHash) {
        this.canonicalHash = canonicalHash;
    }

    /**
     * @return the content
     */
    public byte[] getContent() {
        return content;
    }

    /**
     * @param content the content to set
     */
    public void setContent(byte[] content) {
        this.content = content;
    }

    /**
     * @return the serialversionuid
     */
    public static long getSerialversionuid() {
        return serialVersionUID;
    }

}
