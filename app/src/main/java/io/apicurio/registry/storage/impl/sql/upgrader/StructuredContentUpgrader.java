package io.apicurio.registry.storage.impl.sql.upgrader;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.NoopStructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.storage.impl.sql.IDbUpgrader;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import io.quarkus.runtime.annotations.RegisterForReflection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static io.apicurio.registry.storage.impl.sql.StructuredContentIndexUtils.elementType;
import static io.apicurio.registry.storage.impl.sql.StructuredContentIndexUtils.elementValue;
import static io.apicurio.registry.storage.impl.sql.StructuredContentIndexUtils.extractionContent;
import static io.apicurio.registry.storage.impl.sql.StructuredContentIndexUtils.isIndexable;
import static io.apicurio.registry.storage.impl.sql.StructuredContentIndexUtils.rowKey;
import static io.apicurio.registry.utils.StringUtil.sanitizeForLog;

/**
 * Database upgrader that backfills the artifact_structured_content table for existing artifacts. Structured
 * elements (e.g. Agent Card skills and capabilities, MCP tool parameters) were previously only indexed into
 * Elasticsearch, so structure-based search filters were ignored on SQL storage. This upgrader extracts the
 * elements from the latest version of every artifact whose type has a structured content extractor and
 * populates the new table so those filters work on SQL.
 * <p>
 * The backfill reads and re-parses the latest version's content of every artifact of an extractable type
 * (agent card, MCP tool, OpenAPI, AsyncAPI), so on a large registry it adds a one-time cost to the
 * database upgrade proportional to the number of such artifacts - other artifact types are not touched.
 * Progress is logged every {@link #PROGRESS_LOG_INTERVAL} artifacts so a long-running upgrade is
 * observable rather than looking hung.
 */
@RegisterForReflection
public class StructuredContentUpgrader implements IDbUpgrader {

    private static final Logger log = LoggerFactory.getLogger(StructuredContentUpgrader.class);

    /**
     * JDBC fetch size for the backfill query. This is a bulk read of whole content blobs, so it is sized
     * to keep the number of database round trips low without holding an unbounded number of blobs in
     * memory at once.
     */
    private static final int BACKFILL_FETCH_SIZE = 500;

    /**
     * Number of artifacts between progress log lines.
     */
    private static final int PROGRESS_LOG_INTERVAL = 500;

    private final ArtifactTypeUtilProviderFactory factory = new DefaultArtifactTypeUtilProviderImpl(true);

    @Override
    public void upgrade(Handle handle) throws Exception {
        log.info("Backfilling structured content for existing artifacts...");
        // Rebuilds/retries must also remove stale rows for artifacts with no eligible published tip.
        handle.createUpdate("DELETE FROM artifact_structured_content").execute();

        String sql = "SELECT a.groupId, a.artifactId, a.type, c.content "
                + "FROM artifacts a "
                    + "JOIN versions v ON v.groupId = a.groupId AND v.artifactId = a.artifactId "
                    + "JOIN content c ON c.contentId = v.contentId "
                + "WHERE a.type = ? AND v.state NOT IN ('DRAFT', 'DISABLED') AND ("
                + "EXISTS (SELECT 1 FROM branch_versions b WHERE b.groupId = v.groupId AND b.artifactId = v.artifactId"
                + " AND b.branchId = 'latest' AND b.version = v.version AND b.branchOrder = "
                + "(SELECT MAX(b2.branchOrder) FROM branch_versions b2 JOIN versions v2 ON v2.groupId = b2.groupId"
                + " AND v2.artifactId = b2.artifactId AND v2.version = b2.version WHERE b2.groupId = v.groupId"
                + " AND b2.artifactId = v.artifactId AND b2.branchId = 'latest' AND v2.state NOT IN ('DRAFT', 'DISABLED')))"
                + " OR (NOT EXISTS (SELECT 1 FROM branches b WHERE b.groupId = v.groupId AND b.artifactId = v.artifactId"
                + " AND b.branchId = 'latest') AND v.versionOrder = (SELECT MAX(v2.versionOrder) FROM versions v2"
                + " WHERE v2.groupId = a.groupId AND v2.artifactId = a.artifactId AND v2.state NOT IN ('DRAFT', 'DISABLED'))))";

        int totalCount = 0;
        AtomicInteger examined = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        for (ArtifactTypeUtilProvider provider : factory.getAllArtifactTypeProviders()) {
            StructuredContentExtractor extractor = provider.getStructuredContentExtractor();
            if (extractor == null || extractor instanceof NoopStructuredContentExtractor) {
                // Providers never return null here - types without structured extraction get
                // NoopStructuredContentExtractor, so that is what identifies them. Checking for null
                // alone would read and extract every artifact of every type (AVRO, PROTOBUF, ...) only
                // to discard an always-empty element list, adding avoidable time to the backfill.
                continue;
            }
            totalCount += handle.createQuery(sql).bind(0, provider.getArtifactType())
                    .setFetchSize(BACKFILL_FETCH_SIZE).map(new ArtifactContentRowMapper()).stream()
                    .mapToInt(artifact -> {
                        if (examined.incrementAndGet() % PROGRESS_LOG_INTERVAL == 0) {
                            log.info("Backfilling structured content: {} artifacts examined so far...",
                                    examined.get());
                        }
                        return backfillArtifact(handle, artifact, extractor, failed);
                    }).sum();
        }

        // Report the failure count explicitly: individual failures are only warnings, so without a
        // summary a partially successful backfill is indistinguishable from a clean one.
        if (failed.get() > 0) {
            log.warn("Backfilled structured content for {} artifacts ({} examined), but {} artifact(s) "
                    + "failed and will not match structure-based search filters until they are written "
                    + "again. See the warnings above for the affected artifacts.", totalCount,
                    examined.get(), failed.get());
        } else {
            log.info("Successfully backfilled structured content for {} artifacts ({} examined).",
                    totalCount, examined.get());
        }
    }

    private int backfillArtifact(Handle handle, ArtifactContent artifact,
            StructuredContentExtractor extractor, AtomicInteger failed) {
        List<StructuredElement> elements;
        try {
            elements = extractor.extract(extractionContent(artifact.type, ContentHandle.create(artifact.contentBytes)));
        } catch (Exception ex) {
            failed.incrementAndGet();
            log.warn("Failed to extract structured content for {}/{}", sanitizeForLog(artifact.groupId),
                    sanitizeForLog(artifact.artifactId), ex);
            elements = List.of();
        }

        // Idempotency: clear any existing rows for the artifact before inserting.
        handle.createUpdate("DELETE FROM artifact_structured_content WHERE groupId = ? AND artifactId = ?")
                .bind(0, artifact.groupId).bind(1, artifact.artifactId).execute();

        Set<String> seen = new HashSet<>();
        for (StructuredElement element : elements) {
            if (!isIndexable(element)) {
                // Skip malformed elements: a null value would violate the NOT NULL constraints and,
                // on databases like PostgreSQL, abort the backfill transaction.
                continue;
            }
            String elementType = elementType(artifact.type, element.kind());
            String elementValue = elementValue(element.name());
            // The four columns are the table's primary key, so a repeated element would abort the
            // backfill transaction; de-duplicate on the normalized values before inserting.
            if (seen.add(rowKey(elementType, elementValue))) {
                handle.createUpdate("INSERT INTO artifact_structured_content "
                                + "(groupId, artifactId, elementType, elementValue) VALUES (?, ?, ?, ?)")
                        .bind(0, artifact.groupId).bind(1, artifact.artifactId).bind(2, elementType)
                        .bind(3, elementValue).execute();
            }
        }
        return 1;
    }

    private static class ArtifactContent {
        String groupId;
        String artifactId;
        String type;
        byte[] contentBytes;
    }

    private static class ArtifactContentRowMapper implements RowMapper<ArtifactContent> {
        @Override
        public ArtifactContent map(ResultSet rs) throws SQLException {
            ArtifactContent result = new ArtifactContent();
            result.groupId = rs.getString("groupId");
            result.artifactId = rs.getString("artifactId");
            result.type = rs.getString("type");
            result.contentBytes = rs.getBytes("content");
            return result;
        }
    }
}
