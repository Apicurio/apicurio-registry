package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.events.ArtifactVersionCreated;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the <code>outbox.aggregateid</code> column is wide enough to hold every aggregate id the
 * application can generate.
 *
 * <p>
 * Outbox aggregate ids are built by concatenating identifiers, the widest form being
 * <code>groupId-artifactId-version</code> (see {@link ArtifactVersionCreated}). The column was originally
 * declared as VARCHAR(255), which is far smaller than that concatenation can be. Overflowing it aborted the
 * INSERT, and because the outbox INSERT runs in the same transaction as the artifact write, the whole
 * artifact/version creation was rolled back.
 *
 * <p>
 * These tests are pure resource parsing with no database involved, so they run fast and cover all four
 * dialects at once including MySQL, whose outbox table is declared but not yet written to.
 */
public class OutboxAggregateIdLengthTest {

    private static final String DDL_PATH = "io/apicurio/registry/storage/impl/sql/";

    /**
     * The db version that widened outbox.aggregateid. The upgrade scripts for this version must exist for
     * every dialect, otherwise startup fails when upgrading from an earlier version.
     */
    private static final int WIDENING_DB_VERSION = 110;

    /**
     * Dialects that declare an outbox table. H2 does not have one.
     */
    private static final String[] OUTBOX_DIALECTS = { "postgresql", "mysql", "mssql" };

    // Postgres widens with "ALTER COLUMN aggregateid TYPE VARCHAR(n)", so TYPE may sit between the
    // column name and the type.
    private static final Pattern AGGREGATEID_COLUMN = Pattern
            .compile("aggregateid\\s+(?:TYPE\\s+)?N?VARCHAR\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);

    // MySQL quotes the reserved word `groups` and formats its DDL across multiple lines, hence the
    // optional backticks and DOTALL on the table-spanning patterns.
    private static final Pattern GROUPS_GROUPID = Pattern.compile(
            "CREATE TABLE `?groups`? \\(\\s*groupId\\s+N?VARCHAR\\((\\d+)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern ARTIFACTS_ARTIFACTID = Pattern.compile(
            "CREATE TABLE artifacts \\(\\s*groupId\\s+N?VARCHAR\\(\\d+\\)[^;]*?artifactId\\s+N?VARCHAR\\((\\d+)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern VERSIONS_VERSION = Pattern.compile(
            "CREATE TABLE versions \\([^;]*?[\\s,]version\\s+N?VARCHAR\\((\\d+)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * The core regression test: for every dialect that has an outbox table, the aggregateid column must be
     * able to hold "groupId-artifactId-version" at the maximum widths those columns allow.
     */
    @ParameterizedTest
    @ValueSource(strings = { "postgresql", "mysql", "mssql" })
    public void testAggregateIdColumnFitsWidestAggregateId(String dialect) throws IOException {
        String ddl = readResource(DDL_PATH + dialect + ".ddl");

        int aggregateIdWidth = extractWidth(AGGREGATEID_COLUMN, ddl, "outbox.aggregateid", dialect);
        int groupIdWidth = extractWidth(GROUPS_GROUPID, ddl, "groups.groupId", dialect);
        int artifactIdWidth = extractWidth(ARTIFACTS_ARTIFACTID, ddl, "artifacts.artifactId", dialect);
        int versionWidth = extractWidth(VERSIONS_VERSION, ddl, "versions.version", dialect);

        // Sanity-check the identifier widths this assertion depends on, so that widening one of them later
        // without widening aggregateid is caught here rather than in production.
        assertEquals(512, groupIdWidth, "Unexpected groups.groupId width in " + dialect + ".ddl");
        assertEquals(512, artifactIdWidth, "Unexpected artifacts.artifactId width in " + dialect + ".ddl");
        assertEquals(256, versionWidth, "Unexpected versions.version width in " + dialect + ".ddl");

        // "groupId" + "-" + "artifactId" + "-" + "version"
        int widestAggregateId = groupIdWidth + 1 + artifactIdWidth + 1 + versionWidth;
        assertEquals(1282, widestAggregateId);

        assertTrue(aggregateIdWidth >= widestAggregateId,
                "outbox.aggregateid in " + dialect + ".ddl is VARCHAR(" + aggregateIdWidth
                        + ") but must hold at least " + widestAggregateId
                        + " characters (groupId + '-' + artifactId + '-' + version)");
    }

    /**
     * MSSQL stores every other identifier as NVARCHAR. The outbox column must match, otherwise non-ASCII
     * group or artifact ids are corrupted on the way into the outbox.
     */
    @Test
    public void testMssqlAggregateIdIsNvarchar() throws IOException {
        String ddl = readResource(DDL_PATH + "mssql.ddl");
        Matcher matcher = Pattern
                .compile("aggregateid\\s+(?:TYPE\\s+)?(N?VARCHAR)\\(", Pattern.CASE_INSENSITIVE).matcher(ddl);
        assertTrue(matcher.find(), "Could not find outbox.aggregateid in mssql.ddl");
        assertEquals("NVARCHAR", matcher.group(1).toUpperCase(java.util.Locale.ROOT),
                "outbox.aggregateid must be NVARCHAR on MSSQL to match the other identifier columns");
    }

    /**
     * The aggregateid width must be identical across dialects, so that an aggregate id accepted by one
     * storage backend is accepted by all of them.
     */
    @Test
    public void testAggregateIdWidthIsConsistentAcrossDialects() throws IOException {
        Integer expected = null;
        for (String dialect : OUTBOX_DIALECTS) {
            String ddl = readResource(DDL_PATH + dialect + ".ddl");
            int width = extractWidth(AGGREGATEID_COLUMN, ddl, "outbox.aggregateid", dialect);
            if (expected == null) {
                expected = width;
            } else {
                assertEquals(expected.intValue(), width,
                        "outbox.aggregateid width in " + dialect + ".ddl differs from the other dialects");
            }
        }
        assertEquals(2048, expected);
    }

    /**
     * A missing upgrade script makes {@code CommonSqlStatements.databaseUpgrade} fail at startup, because it
     * opens {@code upgrades/<version>/<dialect>.upgrade.ddl} for every intermediate version unconditionally.
     * H2 is included even though it has no outbox table its script only bumps the version.
     */
    @ParameterizedTest
    @ValueSource(strings = { "postgresql", "mysql", "mssql", "h2" })
    public void testUpgradeScriptExistsAndBumpsVersion(String dialect) throws IOException {
        String upgrade = readResource(
                DDL_PATH + "upgrades/" + WIDENING_DB_VERSION + "/" + dialect + ".upgrade.ddl");
        assertTrue(
                upgrade.contains(
                        "UPDATE apicurio SET propValue = " + WIDENING_DB_VERSION + " WHERE propName = 'db_version'"),
                dialect + " upgrade script must set db_version to " + WIDENING_DB_VERSION);
    }

    /**
     * Dialects with an outbox table must actually widen the column in the upgrade script, and the width must
     * agree with the base DDL otherwise a freshly initialized database and an upgraded one end up with
     * different schemas.
     */
    @ParameterizedTest
    @ValueSource(strings = { "postgresql", "mysql", "mssql" })
    public void testUpgradeScriptWidensColumnToMatchBaseDdl(String dialect) throws IOException {
        String upgrade = readResource(
                DDL_PATH + "upgrades/" + WIDENING_DB_VERSION + "/" + dialect + ".upgrade.ddl");
        String baseDdl = readResource(DDL_PATH + dialect + ".ddl");

        assertTrue(upgrade.contains("ALTER TABLE outbox"),
                dialect + " upgrade script must alter the outbox table");

        int upgradeWidth = extractWidth(AGGREGATEID_COLUMN, upgrade, "outbox.aggregateid",
                dialect + " upgrade");
        int baseWidth = extractWidth(AGGREGATEID_COLUMN, baseDdl, "outbox.aggregateid", dialect);
        assertEquals(baseWidth, upgradeWidth,
                "outbox.aggregateid width in the " + dialect
                        + " upgrade script must match the base DDL, otherwise upgraded and freshly "
                        + "initialized databases diverge");
    }

    /**
     * H2 has no outbox table, so its upgrade script must not try to alter one.
     */
    @Test
    public void testH2UpgradeScriptDoesNotTouchOutbox() throws IOException {
        String upgrade = readResource(DDL_PATH + "upgrades/" + WIDENING_DB_VERSION + "/h2.upgrade.ddl");
        assertTrue(!upgrade.contains("outbox"),
                "H2 has no outbox table, so its upgrade script must not reference one");
    }

    /**
     * The build's db version must be at least the version that widened the column, otherwise the upgrade
     * script is never executed.
     */
    @Test
    public void testDbVersionIncludesTheUpgrade() throws IOException {
        int dbVersion = Integer.parseInt(readResource(DDL_PATH + "db-version").trim());
        assertTrue(dbVersion >= WIDENING_DB_VERSION, "db-version is " + dbVersion + " but must be at least "
                + WIDENING_DB_VERSION + " for the outbox.aggregateid widening to be applied");
    }

    /**
     * Ties the schema width back to the code that produces aggregate ids: an event built from
     * maximum-length identifiers must still fit the column.
     */
    @Test
    public void testGeneratedAggregateIdFitsColumn() throws IOException {
        String groupId = "g".repeat(512);
        String artifactId = "a".repeat(512);
        String version = "v".repeat(256);

        ArtifactVersionCreated event = ArtifactVersionCreated.of(ArtifactVersionMetaDataDto.builder()
                .groupId(groupId).artifactId(artifactId).version(version).build());

        String aggregateId = event.getAggregateId();
        assertEquals(1282, aggregateId.length());
        assertEquals(groupId + "-" + artifactId + "-" + version, aggregateId);

        String ddl = readResource(DDL_PATH + "postgresql.ddl");
        int aggregateIdWidth = extractWidth(AGGREGATEID_COLUMN, ddl, "outbox.aggregateid", "postgresql");
        assertTrue(aggregateId.length() <= aggregateIdWidth, "Generated aggregate id of length "
                + aggregateId.length() + " does not fit VARCHAR(" + aggregateIdWidth + ")");
    }

    private static int extractWidth(Pattern pattern, String ddl, String column, String dialect) {
        Matcher matcher = pattern.matcher(ddl);
        assertTrue(matcher.find(), "Could not find " + column + " in the " + dialect + " DDL");
        return Integer.parseInt(matcher.group(1));
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "Missing resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
