package io.apicurio.registry.storage.impl.sql.upgrader;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.impl.sql.IDbUpgrader;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;

/**
 * Database upgrader that recomputes canonical hashes for all Avro content. Existing rows stored before a
 * change to the Avro canonicalizer may have stale hashes that prevent deduplication of equivalent schemas,
 * so this upgrader is re-run whenever that canonicalizer changes. It has been run for:
 * <ul>
 * <li>db version 104 — nullable unions ({@code ["null", T]}) were normalized so that the implicit and
 * explicit {@code "default": null} forms produce the same canonical hash.</li>
 * <li>db version 108 — resolved references are now seeded into the parser, so schemas that reference
 * another artifact canonicalize instead of silently falling back to their raw, uncanonicalized content.</li>
 * </ul>
 */
@RegisterForReflection
public class AvroCanonicalHashUpgrader implements IDbUpgrader {

    private static final Logger log = LoggerFactory.getLogger(AvroCanonicalHashUpgrader.class);

    private final ArtifactTypeUtilProviderFactory factory = new DefaultArtifactTypeUtilProviderImpl(true);

    @Override
    public void upgrade(Handle handle) throws Exception {
        log.info("Recomputing Avro canonical content hashes...");

        String sql = "SELECT DISTINCT c.contentId, c.canonicalHash, c.contentHash, c.contentType, c.content, c.refs, a.type "
                + "FROM versions v "
                    + "JOIN content c ON c.contentId = v.contentId "
                    + "JOIN artifacts a ON a.groupId = v.groupId AND a.artifactId = v.artifactId "
                + "WHERE a.type = ?";

        AtomicInteger processedCount = new AtomicInteger();
        AtomicInteger updatedCount = new AtomicInteger();
        AtomicInteger skippedCount = new AtomicInteger();
        AtomicInteger failedCount = new AtomicInteger();

        // Same pattern as SqlExportRepository / SqlVersionRepository: MappedQuery.stream() only
        // closes the ResultSet/Statement via onClose, so the Stream must be closed explicitly.
        Stream<ContentWithType> stream = handle.createQuery(sql).bind(0, ArtifactType.AVRO).setFetchSize(50)
                .map(new ContentWithTypeRowMapper()).stream();
        try (stream) {
            stream.forEach(entity -> {
                processedCount.incrementAndGet();
                try {
                    int result = updateEntity(handle, entity);
                    if (result > 0) {
                        updatedCount.incrementAndGet();
                    } else {
                        skippedCount.incrementAndGet();
                    }
                } catch (Exception ex) {
                    failedCount.incrementAndGet();
                    log.warn("Failed to update canonical hash for contentId {}.",
                            entity.contentEntity.contentId, ex);
                }
            });
        }

        log.info(
                "Avro canonical hash upgrade complete: processed={}, updated={}, skipped={}, failed={}.",
                processedCount.get(), updatedCount.get(), skippedCount.get(), failedCount.get());
    }

    private int updateEntity(Handle handle, ContentWithType entity) {
        List<ArtifactReferenceDto> references = RegistryContentUtils
                .deserializeReferences(entity.contentEntity.serializedReferences);

        validateReferencesResolvable(handle, references);

        ContentWrapperDto data = ContentWrapperDto.builder()
                .content(ContentHandle.create(entity.contentEntity.contentBytes))
                .contentType(entity.contentEntity.contentType).references(references)
                .artifactType(entity.type).build();

        String newCanonicalHash = RegistryContentUtils.canonicalContentHash(factory, entity.type, data,
                ref -> resolveReference(handle, ref));

        if (!newCanonicalHash.equals(entity.contentEntity.canonicalHash)) {
            int rowCount = handle
                    .createUpdate("UPDATE content SET canonicalHash = ? WHERE contentId = ?")
                    .bind(0, newCanonicalHash).bind(1, entity.contentEntity.contentId).execute();
            if (rowCount == 0) {
                // Row disappeared between SELECT and UPDATE; treat as failure (not a silent skip).
                log.warn("Database row not found for contentId {}.", entity.contentEntity.contentId);
                throw new IllegalStateException(
                        "Database row not found for contentId " + entity.contentEntity.contentId);
            }
            return 1;
        }
        return 0;
    }

    private void validateReferencesResolvable(Handle handle, List<ArtifactReferenceDto> references) {
        if (references == null || references.isEmpty()) {
            return;
        }
        for (ArtifactReferenceDto reference : references) {
            ContentWrapperDto resolved = resolveReference(handle, reference);
            validateReferencesResolvable(handle, resolved.getReferences());
        }
    }

    private ContentWrapperDto resolveReference(Handle handle, ArtifactReferenceDto reference) {
        String sql = "SELECT c.contentId, c.canonicalHash, c.contentHash, c.contentType, c.content, c.refs, a.type "
                + "FROM versions v "
                + "JOIN content c ON c.contentId = v.contentId "
                + "JOIN artifacts a ON a.groupId = v.groupId AND a.artifactId = v.artifactId "
                + "WHERE v.groupId = ? AND v.artifactId = ? AND v.version = ?";

        ContentWithType result = handle.createQuery(sql)
                .bind(0, normalizeGroupId(reference.getGroupId()))
                .bind(1, reference.getArtifactId()).bind(2, reference.getVersion())
                .map(new ContentWithTypeRowMapper()).one();

        return ContentWrapperDto.builder().content(ContentHandle.create(result.contentEntity.contentBytes))
                .contentType(result.contentEntity.contentType)
                .references(RegistryContentUtils.deserializeReferences(result.contentEntity.serializedReferences))
                .artifactType(result.type).build();
    }

    private static class ContentWithType {
        ContentEntity contentEntity;
        String type;
    }

    private static class ContentWithTypeRowMapper implements RowMapper<ContentWithType> {
        @Override
        public ContentWithType map(ResultSet rs) throws SQLException {
            ContentWithType result = new ContentWithType();
            result.contentEntity = ContentEntityMapper.instance.map(rs);
            result.type = rs.getString("type");
            return result;
        }
    }
}
