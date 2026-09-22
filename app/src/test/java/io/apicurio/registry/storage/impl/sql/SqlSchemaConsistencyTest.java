package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.utils.IoUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

/**
 * Verifies that {@code db-version} matches an existing {@code upgrades/<db-version>/} directory
 * for every SQL dialect, and that every base DDL declares that same version.
 */
class SqlSchemaConsistencyTest {

    private static final String[] DIALECTS = { "h2", "mssql", "mysql", "postgresql" };

    // Version that introduced the peers table. Update only if that migration is renumbered.
    private static final int PEERS_DB_VERSION = 110;

    @Test
    void testUpgradeScriptsExistForCurrentDbVersion() {
        int dbVersion = readDbVersion();

        for (String dialect : DIALECTS) {
            String upgradePath = "upgrades/" + dbVersion + "/" + dialect + ".upgrade.ddl";
            String upgradeDdl = readResource(upgradePath);
            Assertions.assertNotNull(upgradeDdl, "Missing upgrade DDL for dialect '" + dialect
                    + "' at version " + dbVersion + ": " + upgradePath);
            Assertions.assertTrue(
                    upgradeDdl.contains("UPDATE apicurio SET propValue = " + dbVersion),
                    "Upgrade DDL for dialect '" + dialect + "' at version " + dbVersion
                            + " does not bump db_version to " + dbVersion + ".");
        }
    }

    @Test
    void testBaseDdlsDeclareCurrentDbVersionAndPeersTable() {
        int dbVersion = readDbVersion();

        for (String dialect : DIALECTS) {
            String baseDdl = readResource(dialect + ".ddl");
            Assertions.assertNotNull(baseDdl, "Missing base DDL for dialect '" + dialect + "'.");
            Assertions.assertTrue(
                    baseDdl.contains("'db_version', " + dbVersion),
                    "Base DDL for dialect '" + dialect + "' does not declare db_version " + dbVersion + ".");
            Assertions.assertTrue(baseDdl.contains("CREATE TABLE peers"),
                    "Base DDL for dialect '" + dialect + "' does not declare the peers table.");
        }
    }

    @Test
    void testPeersUpgradeScriptsExistForEveryDialect() {
        for (String dialect : DIALECTS) {
            String upgradePath = "upgrades/" + PEERS_DB_VERSION + "/" + dialect + ".upgrade.ddl";
            String upgradeDdl = readResource(upgradePath);
            Assertions.assertNotNull(upgradeDdl, "Missing peers upgrade DDL for dialect '" + dialect
                    + "': " + upgradePath);
            Assertions.assertTrue(upgradeDdl.contains("CREATE TABLE peers"),
                    "Upgrade DDL for dialect '" + dialect + "' at version " + PEERS_DB_VERSION
                            + " does not create the peers table.");
        }
    }

    private int readDbVersion() {
        String raw = readResource("db-version");
        Assertions.assertNotNull(raw, "Missing db-version resource.");
        return Integer.parseInt(raw.trim());
    }

    private String readResource(String name) {
        try (InputStream is = SqlSchemaConsistencyTest.class.getResourceAsStream(name)) {
            if (is == null) {
                return null;
            }
            return IoUtil.toString(is);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
