package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.utils.IoUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies that {@code db-version} matches an existing {@code upgrades/<db-version>/} directory
 * for every SQL dialect, and that every base DDL declares that same version.
 */
class SqlSchemaConsistencyTest {

    private static final String[] DIALECTS = { "h2", "mssql", "mysql", "postgresql" };

    // Version that introduced the peers table. Update only if that migration is renumbered.
    private static final int PEERS_DB_VERSION = 111;

    private static final Pattern CREATE_PEERS_TABLE = Pattern.compile("CREATE TABLE (IF NOT EXISTS )?peers \\(");

    // The column definitions of the peers table, from peerId through credentialSecretRef. What
    // follows the last column (keys, indexes, table options) is laid out differently per script.
    private static final Pattern PEERS_COLUMNS = Pattern.compile(
            "CREATE TABLE (?:IF NOT EXISTS )?peers \\((.*?\\bcredentialSecretRef\\s+\\w+\\(\\d+\\))",
            Pattern.DOTALL);

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
            Matcher createPeers = CREATE_PEERS_TABLE.matcher(upgradeDdl);
            Assertions.assertTrue(createPeers.find(),
                    "Upgrade DDL for dialect '" + dialect + "' at version " + PEERS_DB_VERSION
                            + " does not create the peers table.");
            Assertions.assertTrue(upgradeDdl.indexOf("UPDATE apicurio SET propValue") > createPeers.start(),
                    "Upgrade DDL for dialect '" + dialect + "' at version " + PEERS_DB_VERSION
                            + " must bump db_version only after creating the peers table.");
        }
    }

    @Test
    void testPeersColumnsMatchBetweenBaseAndUpgradeDdl() {
        for (String dialect : DIALECTS) {
            String baseColumns = peersColumns(readResource(dialect + ".ddl"));
            String upgradeColumns = peersColumns(
                    readResource("upgrades/" + PEERS_DB_VERSION + "/" + dialect + ".upgrade.ddl"));
            Assertions.assertEquals(baseColumns, upgradeColumns,
                    "Upgrade DDL for dialect '" + dialect + "' at version " + PEERS_DB_VERSION
                            + " must create the peers table with the same columns as the base DDL.");
        }
    }

    @Test
    void testNativeSequencesUpgradeDropsLegacyTableForNonH2Dialects() {
        for (String dialect : List.of("postgresql", "mssql", "mysql")) {
            String base = readResource(dialect + ".ddl");
            String upgrade = readResource("upgrades/115/" + dialect + ".upgrade.ddl");
            Assertions.assertFalse(base.contains("CREATE TABLE sequences"), dialect);
            for (String seq : List.of("globalid_seq", "contentid_seq", "commentid_seq")) {
                Assertions.assertTrue(base.contains(seq), dialect + " base DDL lacks " + seq);
                Assertions.assertTrue(upgrade.contains(seq), dialect + " upgrade lacks " + seq);
            }
            Assertions.assertTrue(upgrade.contains("NativeSequencesUpgrader"), dialect);
            Assertions.assertTrue(upgrade.contains("DROP TABLE"), dialect);
            Assertions.assertTrue(upgrade.contains("sequences;"), dialect);
        }
    }

    /**
     * MySQL DDL commits implicitly, so a failed 115 upgrade must be re-runnable: object creation
     * and the legacy-table drop are guarded on every dialect.
     */
    @Test
    void testNativeSequencesUpgradeIsIdempotent() {
        String mysql = readResource("upgrades/115/mysql.upgrade.ddl");
        String postgresql = readResource("upgrades/115/postgresql.upgrade.ddl");
        String mssql = readResource("upgrades/115/mssql.upgrade.ddl");
        for (String seq : List.of("globalid_seq", "contentid_seq", "commentid_seq")) {
            Assertions.assertTrue(mysql.contains("CREATE TABLE IF NOT EXISTS " + seq), seq);
            Assertions.assertTrue(postgresql.contains("CREATE SEQUENCE IF NOT EXISTS " + seq), seq);
            Assertions.assertTrue(mssql.contains("IF OBJECT_ID(N'" + seq + "', N'SO') IS NULL CREATE SEQUENCE "
                    + seq), seq);
        }
        Assertions.assertTrue(mysql.contains("DROP TABLE IF EXISTS sequences;"));
        Assertions.assertTrue(postgresql.contains("DROP TABLE IF EXISTS sequences;"));
        Assertions.assertTrue(mssql.contains(
                "IF OBJECT_ID(N'sequences', N'U') IS NOT NULL DROP TABLE sequences;"));
    }

    private static String peersColumns(String ddl) {
        Matcher columns = PEERS_COLUMNS.matcher(ddl);
        Assertions.assertTrue(columns.find(), "Could not find the peers table columns.");
        return columns.group(1).replaceAll("\\s+", " ").trim();
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
