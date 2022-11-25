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

package io.apicurio.registry.storage.impl.sql.upgrader;

import java.util.Collections;
import java.util.stream.Stream;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.ProtobufContentCanonicalizer;
import io.apicurio.registry.storage.impl.sql.IDbUpgrader;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.mappers.ContentEntityMapper;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * @author Fabian Martinez
 */
@RegisterForReflection
public class ProtobufCanonicalHashUpgrader implements IDbUpgrader {

    private static Logger logger = LoggerFactory.getLogger(ProtobufCanonicalHashUpgrader.class);

    /**
     * @see io.apicurio.registry.storage.impl.sql.IDbUpgrader#upgrade(io.apicurio.registry.storage.impl.sql.jdb.Handle)
     */
    @Override
    public void upgrade(Handle dbHandle) throws Exception {

        String sql = "SELECT c.contentId, c.content, c.canonicalHash, c.contentHash "
                + "FROM versions v "
                + "JOIN content c on c.contentId = v.contentId "
                + "JOIN artifacts a ON v.tenantId = a.tenantId AND v.groupId = a.groupId AND v.artifactId = a.artifactId "
                + "WHERE a.type = ?";

        Stream<ContentEntity> stream = dbHandle.createQuery(sql)
                .setFetchSize(50)
                .bind(0, ArtifactType.PROTOBUF)
                .map(ContentEntityMapper.instance)
                .stream();
        try (stream) {
            stream.forEach(entity -> {
                updateCanonicalHash(entity, dbHandle);
            });
        }

    }

    private void updateCanonicalHash(ContentEntity contentEntity, Handle dbHandle) {

        ContentHandle canonicalContent = this.canonicalizeContent(ContentHandle.create(contentEntity.contentBytes));
        byte[] canonicalContentBytes = canonicalContent.bytes();
        String canonicalContentHash = DigestUtils.sha256Hex(canonicalContentBytes);

        String update = "UPDATE content SET canonicalHash = ? WHERE contentId = ? AND contentHash = ?";
        int rowCount = dbHandle.createUpdate(update)
                .bind(0, canonicalContentHash)
                .bind(1, contentEntity.contentId)
                .bind(2, contentEntity.contentHash)
                .execute();
        if (rowCount == 0) {
            logger.warn("content row not matched for canonical hash upgrade contentId {} contentHash {}", contentEntity.contentId, contentEntity.contentHash);
        }

    }

    private ContentHandle canonicalizeContent(ContentHandle content) {
        try {
            ContentCanonicalizer canonicalizer = new ProtobufContentCanonicalizer();
            ContentHandle canonicalContent = canonicalizer.canonicalize(content, Collections.emptyMap());
            return canonicalContent;
        } catch (Exception e) {
            logger.debug("Failed to canonicalize content of type: {}", ArtifactType.PROTOBUF);
            return content;
        }
    }

}
