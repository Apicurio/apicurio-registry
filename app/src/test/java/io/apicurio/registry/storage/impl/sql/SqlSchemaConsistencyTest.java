package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.utils.IoUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
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
