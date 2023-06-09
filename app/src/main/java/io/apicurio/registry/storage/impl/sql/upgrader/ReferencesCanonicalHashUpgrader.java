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

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.storage.impl.sql.IDbUpgrader;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ContentEntityMapper;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.stream.Stream;

@RegisterForReflection
public class ReferencesCanonicalHashUpgrader implements IDbUpgrader {

    private static final Logger logger = LoggerFactory.getLogger(ReferencesContentHashUpgrader.class);

    private static final ArtifactTypeUtilProviderFactory factory = new DefaultArtifactTypeUtilProviderImpl();

    /**
     * @see io.apicurio.registry.storage.impl.sql.IDbUpgrader#upgrade(io.apicurio.registry.storage.impl.sql.jdb.Handle)
     */
    @Override
    public void upgrade(Handle dbHandle) throws Exception {

        String sql = "SELECT c.contentId, c.content, c.canonicalHash, c.contentHash, c.artifactreferences, a.type "
                + "FROM versions v "
                + "JOIN content c on c.contentId = v.contentId "
                + "JOIN artifacts a ON v.tenantId = a.tenantId AND v.groupId = a.groupId AND v.artifactId = a.artifactId ";

        Stream<TypeContentEntity> stream = dbHandle.createQuery(sql)
                .setFetchSize(50)
                .map(new TenantContentEntityRowMapper())
                .stream();
        try (stream) {
            stream.forEach(entity -> updateHash(entity, dbHandle));
        }

    }

    private void updateHash(TypeContentEntity typeContentEntity, Handle dbHandle) {
        try {

            String canonicalContentHash;
            if (typeContentEntity.contentEntity.serializedReferences != null) {
                byte[] referencesBytes = typeContentEntity.contentEntity.serializedReferences.getBytes(StandardCharsets.UTF_8);
                canonicalContentHash = DigestUtils.sha256Hex(concatContentAndReferences(this.canonicalizeContent(typeContentEntity.contentEntity, typeContentEntity.type).bytes(), referencesBytes));
                String update = "UPDATE content SET canonicalHash = ? WHERE contentId = ? AND contentHash = ?";
                int rowCount = dbHandle.createUpdate(update)
                        .bind(0, canonicalContentHash)
                        .bind(1, typeContentEntity.contentEntity.contentId)
                        .bind(2, typeContentEntity.contentEntity.contentHash)
                        .execute();
                if (rowCount == 0) {
                    logger.warn("content row not matched for hash upgrade contentId {} contentHash {}", typeContentEntity.contentEntity.contentId, typeContentEntity.contentEntity.contentHash);
                }
            }

        } catch (Exception e) {
            logger.warn("Error found processing content with id {} and hash {}", typeContentEntity.contentEntity.contentId, typeContentEntity.contentEntity.contentHash, e);
        }
    }

    private byte[] concatContentAndReferences(byte[] contentBytes, byte[] referencesBytes) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(contentBytes.length + referencesBytes.length);
        outputStream.write(contentBytes);
        outputStream.write(referencesBytes);
        return outputStream.toByteArray();
    }

    private ContentHandle canonicalizeContent(ContentEntity contentEntity, String type) {
        ContentHandle contentHandle = ContentHandle.create(contentEntity.contentBytes);
        ContentCanonicalizer canonicalizer = factory.getArtifactTypeProvider(type).getContentCanonicalizer();
        return canonicalizer.canonicalize(contentHandle, Collections.emptyMap());
    }

    public static class TypeContentEntity {
        String type;
        ContentEntity contentEntity;
    }

    public static class TenantContentEntityRowMapper implements RowMapper<TypeContentEntity> {
        @Override
        public TypeContentEntity map(ResultSet rs) throws SQLException {
            TypeContentEntity e = new TypeContentEntity();
            e.type = rs.getString("type");
            e.contentEntity = ContentEntityMapper.instance.map(rs);
            return e;
        }
    }
}
