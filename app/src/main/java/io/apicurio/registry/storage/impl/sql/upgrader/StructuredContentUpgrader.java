package io.apicurio.registry.storage.impl.sql.upgrader;

import io.apicurio.registry.content.ContentHandle;
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

import static io.apicurio.registry.utils.StringUtil.asLowerCase;
import static io.apicurio.registry.utils.StringUtil.limitStr;

/**
 * Database upgrader that backfills the artifact_structured_content table for existing artifacts. Structured
 * elements (e.g. Agent Card skills and capabilities, MCP tool parameters) were previously only indexed into
 * Elasticsearch, so structure-based search filters were ignored on SQL storage. This upgrader extracts the
 * elements from the latest version of every artifact whose type has a structured content extractor and
 * populates the new table so those filters work on SQL.
 */
@RegisterForReflection
public class StructuredContentUpgrader implements IDbUpgrader {

    private static final Logger log = LoggerFactory.getLogger(StructuredContentUpgrader.class);

    private final ArtifactTypeUtilProviderFactory factory = new DefaultArtifactTypeUtilProviderImpl(true);

    @Override
    public void upgrade(Handle handle) throws Exception {
        log.info("Backfilling structured content for existing artifacts...");

        String sql = "SELECT a.groupId, a.artifactId, a.type, c.content "
                + "FROM artifacts a "
                    + "JOIN versions v ON v.groupId = a.groupId AND v.artifactId = a.artifactId "
                    + "JOIN content c ON c.contentId = v.contentId "
                + "WHERE a.type = ? AND v.versionOrder = "
                    + "(SELECT MAX(v2.versionOrder) FROM versions v2 "
                        + "WHERE v2.groupId = a.groupId AND v2.artifactId = a.artifactId)";

        int totalCount = 0;
        for (ArtifactTypeUtilProvider provider : factory.getAllArtifactTypeProviders()) {
            StructuredContentExtractor extractor = provider.getStructuredContentExtractor();
            if (extractor == null) {
                continue;
            }
            totalCount += handle.createQuery(sql).bind(0, provider.getArtifactType()).setFetchSize(50)
                    .map(new ArtifactContentRowMapper()).stream().mapToInt(artifact -> {
                        try {
                            return backfillArtifact(handle, artifact, extractor);
                        } catch (Exception ex) {
                            log.warn("Failed to backfill structured content for {}/{}.",
                                    artifact.groupId, artifact.artifactId, ex);
                            return 0;
                        }
                    }).sum();
        }

        log.info("Successfully backfilled structured content for {} artifacts.", totalCount);
    }

    private int backfillArtifact(Handle handle, ArtifactContent artifact,
            StructuredContentExtractor extractor) {
        List<StructuredElement> elements = extractor
                .extract(ContentHandle.create(artifact.contentBytes));
        if (elements.isEmpty()) {
            return 0;
        }

        // Idempotency: clear any existing rows for the artifact before inserting.
        handle.createUpdate("DELETE FROM artifact_structured_content WHERE groupId = ? AND artifactId = ?")
                .bind(0, artifact.groupId).bind(1, artifact.artifactId).execute();

        Set<String> seen = new HashSet<>();
        for (StructuredElement element : elements) {
            String elementType = limitStr(asLowerCase(artifact.type + ":" + element.kind()), 64);
            String elementValue = limitStr(asLowerCase(element.name()), 512);
            if (seen.add(elementType + ":" + elementValue)) {
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
