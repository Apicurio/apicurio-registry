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

package io.apicurio.registry.storage.impl.kafkasql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.ProtobufContentCanonicalizer;
import io.apicurio.registry.storage.impl.kafkasql.values.ActionType;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.IDbUpgrader;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ContentEntityMapper;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.impexp.ContentEntity;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class KafkaSqlUpgrader {

    @Inject
    Logger logger;

    @Inject
    KafkaSqlSubmitter submitter;

    @Inject
    KafkaSqlCoordinator coordinator;

    @Inject
    HandleFactory handles;

    public void upgrade() {

        handles.withHandleNoException(handle -> {

            new KafkaSqlProtobufCanonicalizerUpgrader().upgrade(handle);

            return null;
        });

    }

    private class KafkaSqlProtobufCanonicalizerUpgrader implements IDbUpgrader {

        @Override
        public void upgrade(Handle dbHandle) throws Exception {

            String sql = "SELECT c.contentId, c.content, c.canonicalHash, c.contentHash, c.artifactreferences, v.tenantId "
                    + "FROM versions v "
                    + "JOIN content c on c.contentId = v.contentId "
                    + "JOIN artifacts a ON v.tenantId = a.tenantId AND v.groupId = a.groupId AND v.artifactId = a.artifactId "
                    + "WHERE a.type = ?";

            Stream<TenantContentEntity> stream = dbHandle.createQuery(sql)
                    .setFetchSize(50)
                    .bind(0, ArtifactType.PROTOBUF)
                    .map(new TenantContentEntityRowMapper())
                    .stream();
            try (stream) {
                stream.forEach(entity -> {
                    updateCanonicalHash(entity);
                });
            }

        }

        protected void updateCanonicalHash(TenantContentEntity tenantContentEntity) {

            ContentEntity contentEntity = tenantContentEntity.contentEntity;

            ContentHandle content = ContentHandle.create(contentEntity.contentBytes);
            ContentHandle canonicalContent = canonicalizeContent(content);
            byte[] canonicalContentBytes = canonicalContent.bytes();
            String canonicalContentHash = DigestUtils.sha256Hex(canonicalContentBytes);

            if (canonicalContentHash.equals(tenantContentEntity.contentEntity.canonicalHash)) {
                //canonical hash is correct, skipping
                return;
            }

            logger.debug("Protobuf content canonicalHash outdated value detected, updating contentId {}", contentEntity.contentId);

            CompletableFuture<UUID> future = submitter
                    .submitContent(tenantContentEntity.tenantId, contentEntity.contentId, contentEntity.contentHash, ActionType.UPDATE, canonicalContentHash, null, null);
            UUID uuid = ConcurrentUtil.get(future);
            coordinator.waitForResponse(uuid);

        }

        protected ContentHandle canonicalizeContent(ContentHandle content) {
            try {
                ContentCanonicalizer canonicalizer = new ProtobufContentCanonicalizer();
                return canonicalizer.canonicalize(content, Collections.emptyMap());
            } catch (Exception e) {
                logger.debug("Failed to canonicalize content of type: {}", ArtifactType.PROTOBUF);
                return content;
            }
        }

    }

    public static class TenantContentEntity {
        String tenantId;
        ContentEntity contentEntity;
    }

    public static class TenantContentEntityRowMapper implements RowMapper<TenantContentEntity> {
        @Override
        public TenantContentEntity map(ResultSet rs) throws SQLException {
            TenantContentEntity e = new TenantContentEntity();
            e.tenantId = rs.getString("tenantId");
            e.contentEntity = ContentEntityMapper.instance.map(rs);
            return e;
        }
    }

}
