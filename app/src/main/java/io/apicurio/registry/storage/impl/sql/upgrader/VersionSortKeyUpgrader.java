package io.apicurio.registry.storage.impl.sql.upgrader;

import io.apicurio.registry.storage.impl.sql.IDbUpgrader;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.util.VersionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@ApplicationScoped
public class VersionSortKeyUpgrader implements IDbUpgrader {
    private static final Logger log = LoggerFactory.getLogger(VersionSortKeyUpgrader.class);

    @Override
    public void upgrade(Handle handle) throws Exception {
        log.info("Running VersionSortKeyUpgrader to compute semantic sort keys for existing artifacts.");

        String selectSql = "SELECT globalId, version FROM versions WHERE versionSortKey IS NULL";
        
        List<VersionRecord> records = handle.createQuery(selectSql)
                .map(new RowMapper<VersionRecord>() {
                    @Override
                    public VersionRecord map(ResultSet rs) throws SQLException {
                        return new VersionRecord(rs.getLong("globalId"), rs.getString("version"));
                    }
                }).list();

        if (records.isEmpty()) {
            log.info("No legacy versions found requiring a versionSortKey backfill.");
            return;
        }

        String updateSql = "UPDATE versions SET versionSortKey = ? WHERE globalId = ?";
        for (VersionRecord record : records) {
            String sortKey = VersionUtil.generateVersionSortKey(record.version);
            handle.createUpdate(updateSql)
                    .bind(0, sortKey)
                    .bind(1, record.globalId)
                    .execute();
        }

        log.info("Successfully backfilled versionSortKey for {} versions.", records.size());
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