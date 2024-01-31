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

package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.impl.kafkasql.values.ActionType;
import io.apicurio.registry.storage.impl.sql.IDbUpgrader;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ContentEntityMapper;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import io.apicurio.registry.utils.impexp.ContentEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;

@ApplicationScoped
public class KafkaSqlAvroCanonicalizerUpgrader implements IDbUpgrader {

    @Inject
    Logger logger;

    @Inject
    KafkaSqlSubmitter submitter;

    RegistryStorage storage;

    private static final ArtifactTypeUtilProviderFactory factory = new DefaultArtifactTypeUtilProviderImpl();

    @Override
    public void upgrade(Handle dbHandle) throws Exception {
        //Do nothing, this is just implemented for backward compatibility reasons.
    }

    @Override
    public void upgrade(RegistryStorage registryStorage, Handle dbHandle) throws Exception {
        String sql = "SELECT c.contentId, c.content, c.canonicalHash, c.contentHash, c.artifactreferences, a.type "
                + "FROM versions v "
                + "JOIN content c on c.contentId = v.contentId "
                + "JOIN artifacts a ON v.tenantId = a.tenantId AND v.groupId = a.groupId AND v.artifactId = a.artifactId "
                + "WHERE a.type = ?";

        Stream<TenantContentEntity> stream = dbHandle.createQuery(sql)
                .setFetchSize(50)
                .bind(0, ArtifactType.AVRO)
                .map(new TenantContentEntityRowMapper())
                .stream();
        try (stream) {
            stream.forEach(this::updateCanonicalHash);
        }
    }

    protected void updateCanonicalHash(TenantContentEntity contentEntity) {
        try {

            String canonicalContentHash;
            byte[] referencesBytes = contentEntity.contentEntity.serializedReferences.getBytes(StandardCharsets.UTF_8);
            canonicalContentHash = DigestUtils.sha256Hex(concatContentAndReferences(this.canonicalizeContent(contentEntity.contentEntity, contentEntity.contentEntity.artifactType).bytes(), referencesBytes));

            if (canonicalContentHash.equals(contentEntity.contentEntity.canonicalHash)) {
                logger.debug("Skipping content because the canonical hash is up to date, updating contentId {}", contentEntity.contentEntity.contentId);
                return;
            }

            logger.debug("Avro content canonicalHash outdated value detected, updating contentId {}", contentEntity.contentEntity.contentId);

            submitter.submitContent(contentEntity.tenantId, contentEntity.contentEntity.contentId, contentEntity.contentEntity.contentHash, ActionType.UPDATE, canonicalContentHash, null, contentEntity.contentEntity.serializedReferences != null ? contentEntity.contentEntity.serializedReferences : null);

        } catch (Exception e) {
            logger.warn("Error found processing content with id {} and hash {}", contentEntity.contentEntity.contentId, contentEntity.contentEntity.contentHash, e);
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
        List<ArtifactReferenceDto> artifactReferenceDtos = SqlUtil.deserializeReferences(contentEntity.serializedReferences);
        ContentCanonicalizer canonicalizer = factory.getArtifactTypeProvider(type).getContentCanonicalizer();
        return canonicalizer.canonicalize(contentHandle, storage.resolveReferences(artifactReferenceDtos));
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
