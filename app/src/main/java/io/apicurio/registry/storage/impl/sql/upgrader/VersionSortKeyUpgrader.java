package io.apicurio.registry.storage.impl.sql.upgrader;

import io.apicurio.registry.storage.impl.sql.IDbUpgrader;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.utils.VersionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class VersionSortKeyUpgrader implements IDbUpgrader {
    private static final Logger log = LoggerFactory.getLogger(VersionSortKeyUpgrader.class);

    @Override
    public void upgrade(Handle handle) throws Exception {
        log.info("Running VersionSortKeyUpgrader to compute semantic sort keys for existing artifacts. Note: Version ordering may be unstable during this migration window for large registries.");

        String selectSql = "SELECT globalId, version FROM versions WHERE versionSortKey IS NULL";
        String updateSql = "UPDATE versions SET versionSortKey = ? WHERE globalId = ?";
        
        AtomicInteger count = new AtomicInteger(0);

        handle.createQuery(selectSql)
                .map(new RowMapper<VersionRecord>() {
                    @Override
                    public VersionRecord map(ResultSet rs) throws SQLException {
                        return new VersionRecord(rs.getLong("globalId"), rs.getString("version"));
                    }
                })
                .stream()
                .forEach(vRecord -> {
                    String sortKey = VersionUtil.generateVersionSortKey(vRecord.version);
                    handle.createUpdate(updateSql)
                            .bind(0, sortKey)
                            .bind(1, vRecord.globalId)
                            .execute();
                    count.incrementAndGet();
                });

        if (count.get() == 0) {
            log.info("No legacy versions found requiring a versionSortKey backfill.");
        } else {
            log.info("Successfully backfilled versionSortKey for {} versions.", count.get());
        }
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
