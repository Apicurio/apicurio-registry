/*
 * Copyright 2024 Red Hat
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
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentAndReferencesDto;
import io.apicurio.registry.storage.impl.sql.IDbUpgrader;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ContentAndReferencesDtoRowMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ContentEntityMapper;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.impexp.ContentEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Stream;

import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;


public abstract class AbstractReferencesCanonicalHashUpgrader implements IDbUpgrader {

    private static final Logger log = LoggerFactory.getLogger(AbstractReferencesCanonicalHashUpgrader.class);

    protected int successCounter = 0;


    @Override
    public void upgrade(Handle handle) throws Exception {

        String sql = "SELECT DISTINCT c.*, a.type "
                + "FROM versions v "
                + "JOIN content c ON c.tenantId = v.tenantId AND c.contentId = v.contentId "
                + "JOIN artifacts a ON a.tenantId = v.tenantId AND a.groupId = v.groupId AND a.artifactId = v.artifactId";

        Stream<ExtendedContentEntity> stream = handle.createQuery(sql)
                .setFetchSize(50)
                .map(new ExtendedContentEntityRowMapper())
                .stream();

        try (stream) {
            stream.forEach(entity -> updateEntity(handle, entity));
        }

        log.info("Successfully updated {} canonical content hashes.", successCounter);
        successCounter = 0;
    }


    private void updateEntity(Handle handle, ExtendedContentEntity entity) {
        try {
            beforeEach();
            if (entityHasToBeUpgraded(entity) /* Replaces ProtobufCanonicalHashUpgrader */) {

                var newCanonicalHash = RegistryContentUtils.canonicalContentHash(
                        entity.type,
                        ContentAndReferencesDto.builder()
                                .content(ContentHandle.create(entity.contentEntity.contentBytes))
                                .references(RegistryContentUtils.deserializeReferences(entity.contentEntity.serializedReferences))
                                .build(),
                        r -> resolveReference(handle, entity.tenantId, r)
                );

                if (!newCanonicalHash.equals(entity.contentEntity.canonicalHash)) {
                    entity.contentEntity.canonicalHash = newCanonicalHash;
                    applyUpgrade(handle, entity);
                    successCounter++;
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to update canonical content hash for contentId {} and tenantId {}.", entity.contentEntity.contentId, entity.tenantId, ex);
        }
    }

    private static boolean entityHasToBeUpgraded(ExtendedContentEntity entity) {
        return entity.contentEntity.serializedReferences != null || ArtifactType.PROTOBUF.equals(entity.type) || ArtifactType.AVRO.equals(entity.type);
    }

    protected abstract void beforeEach();

    protected abstract void applyUpgrade(Handle handle, ExtendedContentEntity entity);


    private ContentAndReferencesDto resolveReference(Handle handle, String tenantId, ArtifactReferenceDto reference) {

        String sql = "SELECT c.content, c.artifactreferences "
                + "FROM versions v "
                + "JOIN content c ON c.tenantId = v.tenantId AND c.contentId = v.contentId "
                + "WHERE v.tenantId = ? AND v.groupId = ? AND v.artifactId = ? AND v.version = ?";

        return handle.createQuery(sql)
                .bind(0, tenantId)
                .bind(1, normalizeGroupId(reference.getGroupId()))
                .bind(2, reference.getArtifactId())
                .bind(3, reference.getVersion())
                .map(ContentAndReferencesDtoRowMapper.instance)
                .one();
    }


    protected static class ExtendedContentEntity {
        public String tenantId;
        public ContentEntity contentEntity;
        public String type;
    }


    private static class ExtendedContentEntityRowMapper implements RowMapper<ExtendedContentEntity> {

        @Override
        public ExtendedContentEntity map(ResultSet rs) throws SQLException {
            ExtendedContentEntity entity = new ExtendedContentEntity();
            entity.tenantId = rs.getString("tenantId");
            entity.contentEntity = ContentEntityMapper.instance.map(rs);
            entity.type = rs.getString("type");
            return entity;
        }
    }
}
