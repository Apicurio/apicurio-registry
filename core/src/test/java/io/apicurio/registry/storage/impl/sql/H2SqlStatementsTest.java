package io.apicurio.registry.storage.impl.sql;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class H2SqlStatementsTest {

    private final H2SqlStatements statements = new H2SqlStatements();

    @Test
    void testCreateDataSnapshotWithGzipExtension() {
        String sql = statements.createDataSnapshot("/tmp/snapshot.sql.gz");
        Assertions.assertEquals("SCRIPT TO ? COMPRESSION GZIP", sql);
    }

    @Test
    void testCreateDataSnapshotWithPlainExtension() {
        String sql = statements.createDataSnapshot("/tmp/snapshot.sql");
        Assertions.assertEquals("SCRIPT TO ?", sql);
    }

    @Test
    void testCreateDataSnapshotWithNullLocation() {
        String sql = statements.createDataSnapshot(null);
        Assertions.assertEquals("SCRIPT TO ?", sql);
    }

    @Test
    void testRestoreFromSnapshotWithGzipExtension() {
        String sql = statements.restoreFromSnapshot("/tmp/snapshot.sql.gz");
        Assertions.assertEquals("RUNSCRIPT FROM ? COMPRESSION GZIP", sql);
    }

    @Test
    void testRestoreFromSnapshotWithPlainExtension() {
        String sql = statements.restoreFromSnapshot("/tmp/snapshot.sql");
        Assertions.assertEquals("RUNSCRIPT FROM ?", sql);
    }

    @Test
    void testRestoreFromSnapshotWithNullLocation() {
        String sql = statements.restoreFromSnapshot(null);
        Assertions.assertEquals("RUNSCRIPT FROM ?", sql);
    }
}
