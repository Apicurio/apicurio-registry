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

package io.apicurio.registry.utils.export;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.EntityWriter;

/**
 * @author Fabian Martinez
 */
public class DefaultContentExporter implements ContentExporter {

    private AtomicInteger contentIdSeq = new AtomicInteger(1);
    private Map<String, Long> contentIndex = new HashMap<>();

    private EntityWriter writer;

    public DefaultContentExporter(EntityWriter writer) {
        this.writer = writer;
    }

    /**
     * @see io.apicurio.registry.utils.export.ContentExporter#writeContent(java.lang.String, java.lang.String, byte[], io.apicurio.registry.rest.beans.VersionMetaData)
     */
    @Override
    public Long writeContent(String contentHash, String canonicalContentHash, byte[] contentBytes, VersionMetaData meta) {
        return contentIndex.computeIfAbsent(contentHash, k -> {
            ContentEntity contentEntity = new ContentEntity();
            contentEntity.contentId = contentIdSeq.getAndIncrement();
            contentEntity.contentHash = contentHash;
            contentEntity.canonicalHash = canonicalContentHash;
            contentEntity.contentBytes = contentBytes;

            try {
                writer.writeEntity(contentEntity);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return contentEntity.contentId;
        });
    }

}
