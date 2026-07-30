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
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VersionSortKeyUpgrader implements IDbUpgrader {
    private static final Logger log = LoggerFactory.getLogger(VersionSortKeyUpgrader.class);

    @Override
    public void upgrade(Handle handle) throws Exception {
        String selectSql = "SELECT globalId, version FROM versions WHERE versionSortKey IS NULL";
        String updateSql = "UPDATE versions SET versionSortKey = ? WHERE globalId = ?";
        
        List<VersionRecord> recordsToUpdate = handle.createQuery(selectSql)
                .map(new RowMapper<VersionRecord>() {
                    @Override
                    public VersionRecord map(ResultSet rs) throws SQLException {
                        return new VersionRecord(rs.getLong("globalId"), rs.getString("version"));
                    }
                })
                .list();

        int totalRows = recordsToUpdate.size();
        
        if (totalRows == 0) {
            log.info("No legacy versions found requiring a versionSortKey backfill.");
            return;
        }

        log.info("Starting versionSortKey backfill for {} existing artifacts. Note: Version ordering may be unstable during this migration window.", totalRows);

        int count = 0;
        for (VersionRecord vRecord : recordsToUpdate) {
            String sortKey = VersionUtil.generateVersionSortKey(vRecord.version);
            handle.createUpdate(updateSql)
                    .bind(0, sortKey)
                    .bind(1, vRecord.globalId)
                    .execute();
            
            count++;
            if (count % 10000 == 0) {
                log.info("Backfilled {} / {} artifacts...", count, totalRows);
            }
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
