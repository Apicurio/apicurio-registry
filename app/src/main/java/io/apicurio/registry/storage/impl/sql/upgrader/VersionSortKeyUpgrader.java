package io.apicurio.registry.storage.impl.sql.upgrader;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.storage.impl.sql.IDbUpgrader;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.utils.VersionUtil;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class VersionSortKeyUpgrader implements IDbUpgrader {
    private static final Logger log = LoggerFactory.getLogger(VersionSortKeyUpgrader.class);

    @Override
    public void upgrade(Handle handle) throws Exception {
        String countSql = "SELECT COUNT(globalId) FROM versions WHERE versionSortKey IS NULL";
        long totalRows = handle.createQuery(countSql).mapTo(Long.class).one();

        if (totalRows == 0) {
            log.info("No legacy versions found requiring a versionSortKey backfill.");
            return;
        }

        long minId = handle.createQuery("SELECT MIN(globalId) FROM versions WHERE versionSortKey IS NULL").mapTo(Long.class).one();
        long maxId = handle.createQuery("SELECT MAX(globalId) FROM versions WHERE versionSortKey IS NULL").mapTo(Long.class).one();

        log.info("Starting versionSortKey backfill for {} existing artifacts (ID range [{}, {}]). Note: Version ordering may be unstable during this migration window.", totalRows, minId, maxId);

        String selectSql = "SELECT globalId, version FROM versions WHERE versionSortKey IS NULL AND globalId >= ? AND globalId <= ?";
        
        long currentMin = minId;
        // Chunking to 500 to stay safely under MSSQL's strict 2100 parameter limit per query
        long chunkSize = 500;
        long totalProcessed = 0;

        while (currentMin <= maxId) {
            long currentMax = currentMin + chunkSize - 1;
            
            List<VersionRecord> batch = handle.createQuery(selectSql)
                    .bind(0, currentMin)
                    .bind(1, currentMax)
                    .map(new RowMapper<VersionRecord>() {
                        @Override
                        public VersionRecord map(ResultSet rs) throws SQLException {
                            return new VersionRecord(rs.getLong("globalId"), rs.getString("version"));
                        }
                    })
                    .list();

            if (!batch.isEmpty()) {
                // Dynamically build a single bulk UPDATE statement to eliminate N-round trips
                StringBuilder updateSql = new StringBuilder("UPDATE versions SET versionSortKey = CASE globalId ");
                for (int i = 0; i < batch.size(); i++) {
                    updateSql.append("WHEN ? THEN ? ");
                }
                updateSql.append("END WHERE globalId IN (");
                for (int i = 0; i < batch.size(); i++) {
                    updateSql.append(i == 0 ? "?" : ", ?");
                }
                updateSql.append(")");

                var update = handle.createUpdate(updateSql.toString());
                int bindIdx = 0;
                
                // 1. Bind the CASE WHEN id THEN sortKey parameters
                for (VersionRecord vRecord : batch) {
                    String sortKey = VersionUtil.generateVersionSortKey(vRecord.version);
                    update.bind(bindIdx++, vRecord.globalId);
                    update.bind(bindIdx++, sortKey);
                }
                
                // 2. Bind the WHERE globalId IN (...) parameters
                for (VersionRecord vRecord : batch) {
                    update.bind(bindIdx++, vRecord.globalId);
                }
                
                // 3. Execute the entire chunk in a single network round-trip!
                update.execute();
                
                totalProcessed += batch.size();
                log.info("Backfilled {} / {} artifacts...", totalProcessed, totalRows);
            }
            currentMin = currentMax + 1;
        }

        log.info("Successfully backfilled versionSortKey for {} versions.", totalRows);
    }

    private static class VersionRecord {
        long globalId;
        String version;
        
        VersionRecord(long globalId, String version) {
            this.globalId = globalId;
            this.version = version;
        }
    }
}
