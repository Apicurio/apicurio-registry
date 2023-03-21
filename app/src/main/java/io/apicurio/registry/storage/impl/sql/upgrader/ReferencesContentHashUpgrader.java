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

package io.apicurio.registry.storage.impl.sql.upgrader;

import io.apicurio.registry.storage.impl.sql.IDbUpgrader;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.mappers.ContentEntityMapper;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

/**
 * @author Carles Arnal
 */
@RegisterForReflection
public class ReferencesContentHashUpgrader implements IDbUpgrader {

    private static Logger logger = LoggerFactory.getLogger(ReferencesContentHashUpgrader.class);

    /**
     * @see io.apicurio.registry.storage.impl.sql.IDbUpgrader#upgrade(io.apicurio.registry.storage.impl.sql.jdb.Handle)
     */
    @Override
    public void upgrade(Handle dbHandle) throws Exception {

        String sql = "SELECT c.contentId, c.content, c.canonicalHash, c.contentHash, c.artifactreferences "
                + "FROM versions v "
                + "JOIN content c on c.contentId = v.contentId "
                + "JOIN artifacts a ON v.tenantId = a.tenantId AND v.groupId = a.groupId AND v.artifactId = a.artifactId ";

        Stream<ContentEntity> stream = dbHandle.createQuery(sql)
                .setFetchSize(50)
                .map(ContentEntityMapper.instance)
                .stream();
        try (stream) {
            stream.forEach(entity -> updateHash(entity, dbHandle));
        }

    }

    private void updateHash(ContentEntity contentEntity, Handle dbHandle) {
        try {

            String contentHash;
            if (contentEntity.serializedReferences != null) {
                byte[] referencesBytes = contentEntity.serializedReferences.getBytes(StandardCharsets.UTF_8);
                contentHash = DigestUtils.sha256Hex(concatContentAndReferences(contentEntity.contentBytes, referencesBytes));
            } else {
                contentHash = DigestUtils.sha256Hex(contentEntity.contentBytes);
            }

            String update = "UPDATE content SET contentHash = ? WHERE contentId = ? AND contentHash = ?";
            int rowCount = dbHandle.createUpdate(update)
                    .bind(0, contentHash)
                    .bind(1, contentEntity.contentId)
                    .bind(2, contentEntity.contentHash)
                    .execute();
            if (rowCount == 0) {
                logger.warn("content row not matched for hash upgrade contentId {} contentHash {}", contentEntity.contentId, contentEntity.contentHash);
            }
        } catch (IOException e) {
            logger.warn("Error found processing content with id {} and hash {}", contentEntity.contentId, contentEntity.contentHash, e);
        }
    }

    private byte[] concatContentAndReferences(byte[] contentBytes, byte[] referencesBytes) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(contentBytes.length + referencesBytes.length);
        outputStream.write(contentBytes);
        outputStream.write(referencesBytes);
        return outputStream.toByteArray();
    }
}
