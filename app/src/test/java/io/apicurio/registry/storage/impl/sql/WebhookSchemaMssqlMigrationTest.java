package io.apicurio.registry.storage.impl.sql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * <strong>Isolated webhook migration smoketest</strong> for SQL Server (schema version 112).
 *
 * <p>Scope: verifies that the 111→112 upgrade DDL creates the three webhook tables with
 * correct constraints, that the upgrade is retry-safe (every {@code CREATE TABLE} is guarded
 * by {@code IF OBJECT_ID IS NULL} and every {@code CREATE INDEX} by a {@code sys.indexes}
 * lookup), and that a fresh install and an upgrade produce identical schemas.</p>
 *
 * <p>Baseline limitation: the "upgraded" scenario starts from a synthetic
 * {@code apicurio} marker table only — not a full v111 registry schema with peer tables.
 * This is sufficient to verify the isolated webhook migration; real-baseline upgrade
 * evidence (full v111 schema + upgrade) must be provided separately.</p>
 *
 * <p>Tests are skipped when Docker is unavailable
 * ({@link Testcontainers#disabledWithoutDocker()}).</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class WebhookSchemaMssqlMigrationTest {

    // Pinned to SQL Server 2022 CU14 (mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04).
    @Container
    @SuppressWarnings({"resource", "rawtypes"})
    static MSSQLServerContainer mssql =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04")
                    .acceptLicense();

    // SQL Server error numbers for integrity violations.
    private static final int MSSQL_UNIQUE_VIOLATION = 2627;   // unique constraint / PK violation
    private static final int MSSQL_FK_VIOLATION = 547;        // FK and CHECK constraint violations

    // ------------------------------------------------------------
    // Per-test cleanup: drop all tables so each test starts from a blank database
    // ------------------------------------------------------------

    @BeforeEach
    void cleanDatabase() throws Exception {
        try (Connection conn = openConnection()) {
            dropAllTables(conn);
        }
    }

    private static void dropAllTables(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            // Drop all FK constraints first so that tables can be dropped without order dependencies.
            st.execute(
                    "DECLARE @sql NVARCHAR(MAX) = ''; "
                    + "SELECT @sql = @sql + 'ALTER TABLE [' + tc.TABLE_NAME + '] DROP CONSTRAINT [' + tc.CONSTRAINT_NAME + ']; ' "
                    + "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc "
                    + "WHERE tc.CONSTRAINT_TYPE = 'FOREIGN KEY'; "
                    + "EXEC(@sql)");
            // Then drop all base tables in the current database.
            st.execute(
                    "DECLARE @sql2 NVARCHAR(MAX) = ''; "
                    + "SELECT @sql2 = @sql2 + 'DROP TABLE [' + TABLE_NAME + ']; ' "
                    + "FROM INFORMATION_SCHEMA.TABLES "
                    + "WHERE TABLE_TYPE = 'BASE TABLE'; "
                    + "EXEC(@sql2)");
        }
    }

    // ------------------------------------------------------------
    // Fresh install
    // ------------------------------------------------------------

    @Test
    void freshInstall_webhookTablesExistAfterInitialization() throws Exception {
        try (Connection conn = openConnection()) {
            applyStatements(conn, new SQLServerSqlStatements().databaseInitialization());
            assertTableExists(conn, "webhook_subscriptions");
            assertTableExists(conn, "webhook_events");
            assertTableExists(conn, "webhook_delivery_logs");
            assertDbVersion(conn, 112);
        }
    }

    // ------------------------------------------------------------
    // Upgrade: 111 → 112 from a minimal v111 state with representative rows
    // ------------------------------------------------------------

    @Test
    void upgrade_fromV111State_createsWebhookTablesAndPreservesRows() throws Exception {
        try (Connection conn = openConnection()) {
            // Create a minimal v111 state: just the apicurio table and an extra property.
            // (A full v110 SQL Server DDL is not pinned in test resources; using the minimal
            // apicurio table is sufficient to verify that the upgrade preserves existing rows.)
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE apicurio (propName NVARCHAR(255) NOT NULL PRIMARY KEY, propValue NVARCHAR(512))");
                st.execute("INSERT INTO apicurio VALUES ('db_version', '111')");
                st.execute("INSERT INTO apicurio VALUES ('extra_key', 'extra_value')");
            }
            assertDbVersion(conn, 111);

            applyStatements(conn, new SQLServerSqlStatements().databaseUpgrade(111, 112));
            assertDbVersion(conn, 112);

            assertTableExists(conn, "webhook_subscriptions");
            assertTableExists(conn, "webhook_events");
            assertTableExists(conn, "webhook_delivery_logs");

            // Seeded apicurio row must be intact.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT propValue FROM apicurio WHERE propName = 'extra_key'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "extra apicurio property must survive the upgrade");
                    assertEquals("extra_value", rs.getString(1));
                }
            }
        }
    }

    // ------------------------------------------------------------
    // Partial-upgrade retry test
    // ------------------------------------------------------------

    /**
     * Two cut-point scenarios verify that the 112 upgrade script is fully retry-safe on SQL Server:
     *
     * <ol>
     *   <li><strong>Pre-index cut</strong>: {@code webhook_subscriptions} table exists (no indexes
     *       yet) but the script died before {@code webhook_events} and the version marker.
     *       Exercises the {@code IF OBJECT_ID IS NULL} table guard.</li>
     *   <li><strong>Post-index cut</strong>: {@code webhook_subscriptions} table AND its two
     *       indexes ({@code IDX_whsubs_1}, {@code IDX_whsubs_2}) already exist, but
     *       {@code webhook_events} and the version marker are absent.
     *       Exercises the {@code sys.indexes} name-lookup guard.</li>
     * </ol>
     *
     * <p>In both cases the full upgrade script is retried and must complete without errors.
     * Constraint invariants are verified after each retry to confirm that constraints
     * (FKs, UQ, CHECK) are intact, not just that the tables exist.</p>
     */
    @Test
    void upgrade_partialApplication_isRetryable() throws Exception {
        // --- Cut point 1: table present, no indexes ---
        try (Connection conn = openConnection()) {
            // Minimal v111 state.
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE apicurio (propName NVARCHAR(255) NOT NULL PRIMARY KEY, propValue NVARCHAR(512))");
                st.execute("INSERT INTO apicurio VALUES ('db_version', '111')");
            }
            assertDbVersion(conn, 111);

            // Simulate partial failure: webhook_subscriptions was committed but the script
            // died before its indexes, webhook_events, webhook_delivery_logs, and the version marker.
            try (Statement st = conn.createStatement()) {
                st.execute(
                        "CREATE TABLE webhook_subscriptions ("
                        + "subscriptionId     NVARCHAR(36)   NOT NULL,"
                        + "name               NVARCHAR(512),"
                        + "ownerId            NVARCHAR(256)  NOT NULL,"
                        + "endpointUrl        NVARCHAR(2048) NOT NULL,"
                        + "eventTypes         NVARCHAR(MAX)  NOT NULL,"
                        + "groupFilter        NVARCHAR(512),"
                        + "artifactIdFilter   NVARCHAR(512),"
                        + "artifactTypeFilter NVARCHAR(32),"
                        + "enabled            BIT            NOT NULL,"
                        + "deletedOn          BIGINT,"
                        + "revision           BIGINT         NOT NULL,"
                        + "signingSecretRef   NVARCHAR(512),"
                        + "createdOn          BIGINT         NOT NULL,"
                        + "modifiedOn         BIGINT         NOT NULL,"
                        + "PRIMARY KEY (subscriptionId)"
                        + ")");
            }
            assertDbVersion(conn, 111);

            // Retry: the full 112 upgrade script must succeed without errors.
            applyStatements(conn, new SQLServerSqlStatements().databaseUpgrade(111, 112));

            assertTableExists(conn, "webhook_subscriptions");
            assertTableExists(conn, "webhook_events");
            assertTableExists(conn, "webhook_delivery_logs");
            assertDbVersion(conn, 112);

            // Verify constraints are intact after retry, not just that tables exist.
            assertConstraintInvariants(conn);
        }

        // --- Cut point 2: table + indexes present, webhook_events absent ---
        // Reset to a blank state before the second cut-point scenario.
        try (Connection conn = openConnection()) {
            dropAllTables(conn);
        }
        try (Connection conn = openConnection()) {
            // Minimal v111 state.
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE apicurio (propName NVARCHAR(255) NOT NULL PRIMARY KEY, propValue NVARCHAR(512))");
                st.execute("INSERT INTO apicurio VALUES ('db_version', '111')");
            }
            assertDbVersion(conn, 111);

            // Simulate partial failure: webhook_subscriptions AND its two indexes were committed
            // but the script died before webhook_events. This exercises the sys.indexes guard.
            try (Statement st = conn.createStatement()) {
                st.execute(
                        "CREATE TABLE webhook_subscriptions ("
                        + "subscriptionId     NVARCHAR(36)   NOT NULL,"
                        + "name               NVARCHAR(512),"
                        + "ownerId            NVARCHAR(256)  NOT NULL,"
                        + "endpointUrl        NVARCHAR(2048) NOT NULL,"
                        + "eventTypes         NVARCHAR(MAX)  NOT NULL,"
                        + "groupFilter        NVARCHAR(512),"
                        + "artifactIdFilter   NVARCHAR(512),"
                        + "artifactTypeFilter NVARCHAR(32),"
                        + "enabled            BIT            NOT NULL,"
                        + "deletedOn          BIGINT,"
                        + "revision           BIGINT         NOT NULL,"
                        + "signingSecretRef   NVARCHAR(512),"
                        + "createdOn          BIGINT         NOT NULL,"
                        + "modifiedOn         BIGINT         NOT NULL,"
                        + "PRIMARY KEY (subscriptionId)"
                        + ")");
                st.execute("CREATE INDEX IDX_whsubs_1 ON webhook_subscriptions(enabled, deletedOn)");
                st.execute("CREATE INDEX IDX_whsubs_2 ON webhook_subscriptions(createdOn)");
            }
            assertDbVersion(conn, 111);

            // Retry: the full 112 upgrade script must succeed — sys.indexes guards skip
            // IDX_whsubs_1 and IDX_whsubs_2 that already exist.
            applyStatements(conn, new SQLServerSqlStatements().databaseUpgrade(111, 112));

            assertTableExists(conn, "webhook_subscriptions");
            assertTableExists(conn, "webhook_events");
            assertTableExists(conn, "webhook_delivery_logs");
            assertDbVersion(conn, 112);

            // Verify constraints are intact after retry.
            assertConstraintInvariants(conn);
        }
    }

    // ------------------------------------------------------------
    // Parameterized constraint-invariants (fresh AND upgraded)
    // ------------------------------------------------------------

    @ParameterizedTest(name = "constraintInvariants on {0} SQL Server database")
    @ValueSource(strings = {"fresh", "upgraded"})
    void constraintInvariants_onFreshAndUpgraded(String scenario) throws Exception {
        try (Connection conn = openConnection()) {
            setupScenario(conn, scenario);
            assertConstraintInvariants(conn);
        }
    }

    // ------------------------------------------------------------
    // Schema-parity test (fresh vs upgraded)
    // ------------------------------------------------------------

    /**
     * Normalized schema metadata must be identical between a fresh install and an upgraded DB.
     *
     * <p>Auto-generated names ignored: primary-key constraint names that SQL Server generates
     * when not explicitly specified (e.g. {@code PK__webhook___...}).  All other explicitly
     * named constraints ({@code UQ_*}, {@code FK_*}, {@code CK_*}) and indexes
     * ({@code IDX_*}) are compared.</p>
     */
    @Test
    void mssqlSchemaParity_freshMatchesUpgraded() throws Exception {
        // Phase 1: collect metadata from a fresh install.
        Map<String, List<String>> freshMeta = new LinkedHashMap<>();
        try (Connection conn = openConnection()) {
            applyStatements(conn, new SQLServerSqlStatements().databaseInitialization());
            for (String table : List.of("webhook_subscriptions", "webhook_events", "webhook_delivery_logs")) {
                freshMeta.put(table, collectMssqlSchemaMeta(conn, table));
            }
            // Drop all tables so the upgraded phase starts with a blank database.
            dropAllTables(conn);
        }

        // Phase 2: collect metadata from an upgraded install and compare.
        try (Connection conn = openConnection()) {
            setupScenario(conn, "upgraded");
            for (String table : List.of("webhook_subscriptions", "webhook_events", "webhook_delivery_logs")) {
                List<String> upgradedMeta = collectMssqlSchemaMeta(conn, table);
                assertEquals(freshMeta.get(table), upgradedMeta,
                        "Schema parity failed for table " + table
                        + ". Fresh: " + freshMeta.get(table) + " Upgraded: " + upgradedMeta);
            }
        }
    }

    // ------------------------------------------------------------
    // Invariant helpers
    // ------------------------------------------------------------

    private void assertConstraintInvariants(Connection conn) throws Exception {
        assertDuplicateIdentityHashRejected(conn);
        assertDuplicateDeliveryKeyRejected(conn);
        assertDistinctSourcesSameEventIdAllowed(conn);
        assertDeleteReferencedEventRejected(conn);
        assertDeleteReferencedSubscriptionRejected(conn);
        assertNegativeAttemptCountRejected(conn);
        assertParentMissingOnInsertRejected(conn);
    }

    private void assertDuplicateIdentityHashRejected(Connection conn) throws Exception {
        String hash = WebhookEventIdentity.computeHash("https://ms-dup.example.com", "ms-dup-" + UUID.randomUUID());

        // Control: first insert succeeds.
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, 'https://ms-dup.example.com', 'evt-1', ?, 'com.example.test', 0x41, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, hash);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
        // Duplicate identityHash must be rejected by UQ_whevents_1.
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, 'https://ms-dup.example.com', 'evt-2', ?, 'com.example.test', 0x41, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, hash);
            ps.setLong(3, System.currentTimeMillis());
            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "UQ_whevents_1 must reject duplicate identityHash on SQL Server");
            assertMssqlIntegrityError(ex, MSSQL_UNIQUE_VIOLATION, "UQ_whevents_1");
        }
    }

    private void assertDuplicateDeliveryKeyRejected(Connection conn) throws Exception {
        String subId = UUID.randomUUID().toString();
        insertSubscription(conn, subId);
        String eventRowId = UUID.randomUUID().toString();
        insertEvent(conn, eventRowId);

        long now = System.currentTimeMillis();
        // Control: first delivery log insert succeeds.
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_delivery_logs "
                + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                + "VALUES (?, ?, ?, 'PENDING', 0, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, subId);
            ps.setString(3, eventRowId);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
        // Duplicate (subscriptionId, eventRowId) must be rejected by UQ_whdlogs_1.
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_delivery_logs "
                + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                + "VALUES (?, ?, ?, 'PENDING', 0, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, subId);
            ps.setString(3, eventRowId);
            ps.setLong(4, now);
            ps.setLong(5, now);
            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "UQ_whdlogs_1 must reject duplicate (subscriptionId, eventRowId) on SQL Server");
            assertMssqlIntegrityError(ex, MSSQL_UNIQUE_VIOLATION, "UQ_whdlogs_1");
        }
    }

    private void assertDistinctSourcesSameEventIdAllowed(Connection conn) throws Exception {
        String sharedId = "shared-ms-" + UUID.randomUUID();
        String hashA = WebhookEventIdentity.computeHash("https://src-a.ms.example.com", sharedId);
        String hashB = WebhookEventIdentity.computeHash("https://src-b.ms.example.com", sharedId);

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, ?, ?, ?, 'com.example.test', 0x41, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, "https://src-a.ms.example.com");
            ps.setString(3, sharedId);
            ps.setString(4, hashA);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, ?, ?, ?, 'com.example.test', 0x41, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, "https://src-b.ms.example.com");
            ps.setString(3, sharedId);
            ps.setString(4, hashB);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private void assertDeleteReferencedEventRejected(Connection conn) throws Exception {
        String subId = UUID.randomUUID().toString();
        insertSubscription(conn, subId);
        String eventRowId = UUID.randomUUID().toString();
        insertEvent(conn, eventRowId);

        long now = System.currentTimeMillis();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_delivery_logs "
                + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                + "VALUES (?, ?, ?, 'PENDING', 0, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, subId);
            ps.setString(3, eventRowId);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
        // Negative: FK_whdlogs_2 must prevent deleting a referenced event.
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM webhook_events WHERE eventRowId = ?")) {
            ps.setString(1, eventRowId);
            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "FK_whdlogs_2 must prevent deleting a referenced event on SQL Server");
            assertMssqlIntegrityError(ex, MSSQL_FK_VIOLATION, "FK_whdlogs_2");
        }
        // Control: removing the delivery log first allows the event to be deleted.
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM webhook_delivery_logs WHERE eventRowId = ?")) {
            ps.setString(1, eventRowId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM webhook_events WHERE eventRowId = ?")) {
            ps.setString(1, eventRowId);
            assertEquals(1, ps.executeUpdate(),
                    "Event must be deletable after its delivery logs are removed (SQL Server)");
        }
    }

    private void assertDeleteReferencedSubscriptionRejected(Connection conn) throws Exception {
        String subId = UUID.randomUUID().toString();
        insertSubscription(conn, subId);
        String eventRowId = UUID.randomUUID().toString();
        insertEvent(conn, eventRowId);

        long now = System.currentTimeMillis();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_delivery_logs "
                + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                + "VALUES (?, ?, ?, 'PENDING', 0, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, subId);
            ps.setString(3, eventRowId);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
        // Negative: FK_whdlogs_1 must prevent deleting a referenced subscription.
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM webhook_subscriptions WHERE subscriptionId = ?")) {
            ps.setString(1, subId);
            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "FK_whdlogs_1 must prevent deleting a referenced subscription on SQL Server");
            assertMssqlIntegrityError(ex, MSSQL_FK_VIOLATION, "FK_whdlogs_1");
        }
        // Control: removing the delivery log first allows the subscription to be deleted.
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM webhook_delivery_logs WHERE subscriptionId = ?")) {
            ps.setString(1, subId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM webhook_subscriptions WHERE subscriptionId = ?")) {
            ps.setString(1, subId);
            assertEquals(1, ps.executeUpdate(),
                    "Subscription must be deletable after its delivery logs are removed (SQL Server)");
        }
    }

    private void assertNegativeAttemptCountRejected(Connection conn) throws Exception {
        long now = System.currentTimeMillis();

        // Control: zero attemptCount must be accepted.
        String subId0 = UUID.randomUUID().toString();
        insertSubscription(conn, subId0);
        String eventRowId0 = UUID.randomUUID().toString();
        insertEvent(conn, eventRowId0);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_delivery_logs "
                + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                + "VALUES (?, ?, ?, 'PENDING', 0, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, subId0);
            ps.setString(3, eventRowId0);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate(); // must not throw
        }

        // Control: attemptCount = 1 must also be accepted — CK_whdlogs_1 is >= 0, not = 0.
        // This catches a regression where the CHECK was accidentally written as = 0.
        String subId1 = UUID.randomUUID().toString();
        insertSubscription(conn, subId1);
        String eventRowId1 = UUID.randomUUID().toString();
        insertEvent(conn, eventRowId1);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_delivery_logs "
                + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                + "VALUES (?, ?, ?, 'PENDING', 1, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, subId1);
            ps.setString(3, eventRowId1);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate(); // must not throw
        }

        // Negative: attemptCount = -1 must be rejected by CK_whdlogs_1.
        String subIdNeg = UUID.randomUUID().toString();
        insertSubscription(conn, subIdNeg);
        String eventRowIdNeg = UUID.randomUUID().toString();
        insertEvent(conn, eventRowIdNeg);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_delivery_logs "
                + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                + "VALUES (?, ?, ?, 'PENDING', -1, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, subIdNeg);
            ps.setString(3, eventRowIdNeg);
            ps.setLong(4, now);
            ps.setLong(5, now);
            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "CK_whdlogs_1 must reject negative attemptCount on SQL Server");
            assertMssqlIntegrityError(ex, MSSQL_FK_VIOLATION, "CK_whdlogs_1");
        }
    }

    private void assertParentMissingOnInsertRejected(Connection conn) throws Exception {
        long now = System.currentTimeMillis();

        // FK_whdlogs_1: unknown subscriptionId.
        String eventForFk1 = UUID.randomUUID().toString();
        insertEvent(conn, eventForFk1);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_delivery_logs "
                + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                + "VALUES (?, ?, ?, 'PENDING', 0, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, UUID.randomUUID().toString()); // random UUID — no matching subscription
            ps.setString(3, eventForFk1);
            ps.setLong(4, now);
            ps.setLong(5, now);
            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "FK_whdlogs_1 must reject delivery log with unknown subscriptionId on SQL Server");
            assertMssqlIntegrityError(ex, MSSQL_FK_VIOLATION, "FK_whdlogs_1");
        }

        // Control: inserting a delivery log with known parents is accepted.
        String subCtrl = UUID.randomUUID().toString();
        insertSubscription(conn, subCtrl);
        String eventCtrl = UUID.randomUUID().toString();
        insertEvent(conn, eventCtrl);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_delivery_logs "
                + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                + "VALUES (?, ?, ?, 'PENDING', 0, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, subCtrl);
            ps.setString(3, eventCtrl);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate(); // must not throw
        }

        // FK_whdlogs_2: unknown eventRowId.
        String subForFk2 = UUID.randomUUID().toString();
        insertSubscription(conn, subForFk2);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_delivery_logs "
                + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                + "VALUES (?, ?, ?, 'PENDING', 0, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, subForFk2);
            ps.setString(3, UUID.randomUUID().toString()); // random UUID — no matching event
            ps.setLong(4, now);
            ps.setLong(5, now);
            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "FK_whdlogs_2 must reject delivery log with unknown eventRowId on SQL Server");
            assertMssqlIntegrityError(ex, MSSQL_FK_VIOLATION, "FK_whdlogs_2");
        }
    }

    // ------------------------------------------------------------
    // Schema metadata collection
    // ------------------------------------------------------------

    private List<String> collectMssqlSchemaMeta(Connection conn, String table) throws SQLException {
        List<String> descriptors = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();

        // Columns.
        try (ResultSet rs = meta.getColumns(null, "dbo", table, null)) {
            while (rs.next()) {
                descriptors.add("COL:"
                        + rs.getString("COLUMN_NAME").toUpperCase(Locale.ROOT) + ":"
                        + rs.getString("TYPE_NAME").toUpperCase(Locale.ROOT) + ":"
                        + rs.getInt("COLUMN_SIZE") + ":"
                        + rs.getInt("NULLABLE") + ":"
                        + normalizeDefault(rs.getString("COLUMN_DEF")));
            }
        }

        // PK columns (not PK constraint name — auto-generated by SQL Server for unnamed PKs).
        try (ResultSet rs = meta.getPrimaryKeys(null, "dbo", table)) {
            while (rs.next()) {
                descriptors.add("PK_COL:"
                        + rs.getString("COLUMN_NAME").toUpperCase(Locale.ROOT) + ":"
                        + rs.getInt("KEY_SEQ"));
            }
        }

        // FK constraints.
        try (ResultSet rs = meta.getImportedKeys(null, "dbo", table)) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                if (fkName == null) {
                    continue;
                }
                descriptors.add("FK:"
                        + fkName.toUpperCase(Locale.ROOT) + ":"
                        + rs.getString("FKCOLUMN_NAME").toUpperCase(Locale.ROOT) + ":"
                        + rs.getString("PKTABLE_NAME").toUpperCase(Locale.ROOT) + ":"
                        + rs.getString("PKCOLUMN_NAME").toUpperCase(Locale.ROOT) + ":"
                        + rs.getInt("DELETE_RULE"));
            }
        }

        // Unique indexes (UQ_*): include ORDINAL_POSITION so column order is part of the contract.
        try (ResultSet rs = meta.getIndexInfo(null, "dbo", table, true, false)) {
            while (rs.next()) {
                String idxName = rs.getString("INDEX_NAME");
                int ordinal = rs.getInt("ORDINAL_POSITION");
                if (idxName == null || ordinal == 0) {
                    continue;
                }
                String idxUpper = idxName.toUpperCase(Locale.ROOT);
                if (!idxUpper.startsWith("UQ_")) {
                    continue;
                }
                descriptors.add("UQ:"
                        + idxUpper + ":"
                        + ordinal + ":"
                        + rs.getString("COLUMN_NAME").toUpperCase(Locale.ROOT));
            }
        }

        // Non-unique indexes (IDX_*): include ORDINAL_POSITION so column order is part of the contract.
        try (ResultSet rs = meta.getIndexInfo(null, "dbo", table, false, false)) {
            while (rs.next()) {
                String idxName = rs.getString("INDEX_NAME");
                int ordinal = rs.getInt("ORDINAL_POSITION");
                if (idxName == null || ordinal == 0) {
                    continue;
                }
                String idxUpper = idxName.toUpperCase(Locale.ROOT);
                if (!idxUpper.startsWith("IDX_")) {
                    continue;
                }
                descriptors.add("IDX:"
                        + idxUpper + ":"
                        + ordinal + ":"
                        + rs.getString("COLUMN_NAME").toUpperCase(Locale.ROOT));
            }
        }

        // Check constraints: include the clause body so a regressed CHECK expression is detected.
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT UPPER(cc.CONSTRAINT_NAME), UPPER(cc.CHECK_CLAUSE) "
                + "FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc "
                + "JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc "
                + "  ON cc.CONSTRAINT_CATALOG = tc.CONSTRAINT_CATALOG "
                + "  AND cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA "
                + "  AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME "
                + "WHERE UPPER(tc.TABLE_NAME) = UPPER(?) "
                + "ORDER BY cc.CONSTRAINT_NAME")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (name.startsWith("CK_")) {
                        descriptors.add("CK:" + name + ":" + rs.getString(2));
                    }
                }
            }
        }

        Collections.sort(descriptors);
        return descriptors;
    }

    private static String normalizeDefault(String def) {
        if (def == null) {
            return "null";
        }
        // SQL Server wraps defaults in parentheses, e.g. ((0)); strip them.
        String s = def.trim();
        while (s.startsWith("(") && s.endsWith(")")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s.toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------
    // Scenario setup
    // ------------------------------------------------------------

    private void setupScenario(Connection conn, String scenario) throws Exception {
        if ("fresh".equals(scenario)) {
            applyStatements(conn, new SQLServerSqlStatements().databaseInitialization());
        } else {
            // Minimal v111 state.
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE apicurio (propName NVARCHAR(255) NOT NULL PRIMARY KEY, propValue NVARCHAR(512))");
                st.execute("INSERT INTO apicurio VALUES ('db_version', '111')");
            }
            applyStatements(conn, new SQLServerSqlStatements().databaseUpgrade(111, 112));
            assertDbVersion(conn, 112);
        }
    }

    // ------------------------------------------------------------
    // Assertion helper
    // ------------------------------------------------------------

    /**
     * Asserts the SQL Server error number and that the error message contains the expected
     * constraint name (compared case-insensitively via {@link Locale#ROOT}).
     *
     * <p>SQL Server uses error 2627 for unique-constraint violations and error 547 for both
     * FK violations and CHECK constraint violations.  The constraint name always appears in
     * the error message.</p>
     */
    private static void assertMssqlIntegrityError(SQLException ex, int expectedCode, String expectedConstraint) {
        assertEquals(expectedCode, ex.getErrorCode(),
                "Expected SQL Server error " + expectedCode + " but got " + ex.getErrorCode()
                + ". Message: " + ex.getMessage());
        assertTrue(ex.getMessage().toUpperCase(Locale.ROOT)
                        .contains(expectedConstraint.toUpperCase(Locale.ROOT)),
                "Expected constraint name '" + expectedConstraint + "' in error message: "
                + ex.getMessage());
    }

    // ------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                mssql.getJdbcUrl(), mssql.getUsername(), mssql.getPassword());
    }

    private static void applyStatements(Connection conn, List<String> statements) throws SQLException {
        for (String sql : statements) {
            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            }
        }
    }

    private static void assertTableExists(Connection conn, String tableName) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE UPPER(TABLE_NAME) = UPPER(?)")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertTrue(rs.getInt(1) > 0, "Table must exist on SQL Server: " + tableName);
            }
        }
    }

    private static void assertDbVersion(Connection conn, int expected) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT propValue FROM apicurio WHERE propName = 'db_version'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "db_version row must exist");
                assertEquals(String.valueOf(expected), rs.getString(1),
                        "db_version must be " + expected);
            }
        }
    }

    private static void insertSubscription(Connection conn, String subscriptionId) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_subscriptions "
                + "(subscriptionId, ownerId, endpointUrl, eventTypes, enabled, revision, createdOn, modifiedOn) "
                + "VALUES (?, 'ms-owner', 'https://endpoint.ms.example.com', '[]', 1, 1, ?, ?)")) {
            ps.setString(1, subscriptionId);
            ps.setLong(2, now);
            ps.setLong(3, now);
            ps.executeUpdate();
        }
    }

    private static void insertEvent(Connection conn, String eventRowId) throws SQLException {
        String hash = WebhookEventIdentity.computeHash("https://ms.example.com", eventRowId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events "
                + "(eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, 'https://ms.example.com', ?, ?, 'com.example.test', 0x41, ?)")) {
            ps.setString(1, eventRowId);
            ps.setString(2, eventRowId);
            ps.setString(3, hash);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }
}
