package io.apicurio.registry.storage.impl.sql.upgrader;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.impl.sql.IDbUpgrader;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.mappers.ContentEntityMapper;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;

/**
 * Database upgrader that recomputes canonical hashes for all Avro content. This is needed because the Avro
 * canonicalizer was changed to use Apache Avro's standard {@code SchemaNormalization.toParsingForm()}.
 */
@RegisterForReflection
public class AvroCanonicalHashUpgrader implements IDbUpgrader {

    private static final Logger log = LoggerFactory.getLogger(AvroCanonicalHashUpgrader.class);

    private final ArtifactTypeUtilProviderFactory factory = new DefaultArtifactTypeUtilProviderImpl(true);

    @Override
    public void upgrade(Handle handle) throws Exception {
        log.info("Recomputing Avro canonical content hashes...");

        String sql = "SELECT DISTINCT c.contentId, c.canonicalHash, c.contentHash, c.contentType, "
                + "c.content, c.refs, a.type " + "FROM versions v "
                + "JOIN content c ON c.contentId = v.contentId "
                + "JOIN artifacts a ON a.groupId = v.groupId AND a.artifactId = v.artifactId "
                + "WHERE a.type = ?";

        int successCount = handle.createQuery(sql).bind(0, ArtifactType.AVRO).setFetchSize(50)
                .map(new ContentWithTypeRowMapper()).stream().mapToInt(entity -> {
                    try {
                        return updateEntity(handle, entity);
                    } catch (Exception ex) {
                        log.warn("Failed to update canonical hash for contentId {}.",
                                entity.contentEntity.contentId, ex);
                        return 0;
                    }
                }).sum();

        log.info("Successfully updated {} Avro canonical content hashes.", successCount);
    }

    private int updateEntity(Handle handle, ContentWithType entity) {
        List<ArtifactReferenceDto> references = RegistryContentUtils
                .deserializeReferences(entity.contentEntity.serializedReferences);

        ContentWrapperDto data = ContentWrapperDto.builder()
                .content(ContentHandle.create(entity.contentEntity.contentBytes))
                .contentType(entity.contentEntity.contentType).references(references)
                .artifactType(entity.contentEntity.contentType).build();

        String newCanonicalHash = RegistryContentUtils.canonicalContentHash(factory, entity.type, data,
                ref -> resolveReference(handle, ref));

        if (!newCanonicalHash.equals(entity.contentEntity.canonicalHash)) {
            int rowCount = handle
                    .createUpdate("UPDATE content SET canonicalHash = ? WHERE contentId = ?")
                    .bind(0, newCanonicalHash).bind(1, entity.contentEntity.contentId).execute();
            if (rowCount == 0) {
                log.warn("Database row not found for contentId {}.", entity.contentEntity.contentId);
                return 0;
            }
            return 1;
        }
        return 0;
    }

    private ContentWrapperDto resolveReference(Handle handle, ArtifactReferenceDto reference) {
        String sql = "SELECT c.contentId, c.canonicalHash, c.contentHash, c.contentType, c.content, c.refs "
                + "FROM versions v "
                + "JOIN content c ON c.contentId = v.contentId "
                + "WHERE v.groupId = ? AND v.artifactId = ? AND v.version = ?";

        ContentEntity entity = handle.createQuery(sql)
                .bind(0, normalizeGroupId(reference.getGroupId()))
                .bind(1, reference.getArtifactId()).bind(2, reference.getVersion())
                .map(ContentEntityMapper.instance).one();

        return ContentWrapperDto.builder().content(ContentHandle.create(entity.contentBytes))
                .contentType(entity.contentType)
                .references(RegistryContentUtils.deserializeReferences(entity.serializedReferences))
                .artifactType(entity.contentType).build();
    }

    private static class ContentWithType {
        ContentEntity contentEntity;
        String type;
    }

    private static class ContentWithTypeRowMapper
            implements io.apicurio.registry.storage.impl.sql.jdb.RowMapper<ContentWithType> {
        @Override
        public ContentWithType map(ResultSet rs) throws SQLException {
            ContentWithType result = new ContentWithType();
            result.contentEntity = ContentEntityMapper.instance.map(rs);
            result.type = rs.getString("type");
            return result;
        }
    }
}
