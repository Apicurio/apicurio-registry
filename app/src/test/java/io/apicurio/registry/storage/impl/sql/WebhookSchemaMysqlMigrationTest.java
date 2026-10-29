package io.apicurio.registry.storage.impl.sql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Testcontainers(disabledWithoutDocker = true)
class WebhookSchemaMysqlMigrationTest {

    // Pin exact version; MySQL >= 8.0.16 is required so CHECK constraints are enforced.
    @Container
    @SuppressWarnings({"resource", "rawtypes"})
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0.39")
            .withDatabaseName("apicurio_test")
            .withUsername("apicurio")
            .withPassword("apicurio");

    // MySQL SQL states / error codes for integrity violations.
    // 23000: integrity constraint violation (covers unique, FK, and check in MySQL).
    private static final String MYSQL_INTEGRITY_VIOLATION = "23000";
    // MySQL error codes:
    private static final int ER_DUP_ENTRY = 1062;
    private static final int ER_NO_REFERENCED_ROW_2 = 1452;
    private static final int ER_ROW_IS_REFERENCED_2 = 1451;
    private static final int ER_CHECK_CONSTRAINT_VIOLATED = 3819;

    @BeforeEach
    void cleanDatabase() throws Exception {
        try (Connection conn = openConnection()) {
            dropAllTables(conn);
        }
    }

    /**
     * Drops all user tables in the current database, disabling FK checks for the duration so
     * that tables can be dropped regardless of referencing constraints.
     */
    private static void dropAllTables(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
        }
        List<String> tables = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
        }
        try (Statement st = conn.createStatement()) {
            for (String table : tables) {
                st.execute("DROP TABLE IF EXISTS `" + table + "`");
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    // ------------------------------------------------------------
    // Fresh install
    // ------------------------------------------------------------

    @Test
    void freshInstall_webhookTablesExistAfterInitialization() throws Exception {
        try (Connection conn = openConnection()) {
            applyStatements(conn, new MySQLSqlStatements().databaseInitialization());
            assertTableExists(conn, "webhook_subscriptions");
            assertTableExists(conn, "webhook_events");
            assertTableExists(conn, "webhook_delivery_logs");
            assertDbVersion(conn, 112);
        }
    }

    // ------------------------------------------------------------
    // Constraint-invariants: parameterized over fresh and upgraded
    // ------------------------------------------------------------

    @ParameterizedTest(name = "constraintInvariants on {0} MySQL database")
    @ValueSource(strings = {"fresh", "upgraded"})
    void constraintInvariants_onFreshAndUpgraded(String scenario) throws Exception {
        try (Connection conn = openConnection()) {
            setupScenario(conn, scenario);
            assertConstraintInvariants(conn);
        }
    }

    // ------------------------------------------------------------
    // Partial-upgrade retry: parameterized over every statement cut point
    // ------------------------------------------------------------

    /**
     * Simulates a partial failure at every possible cut point in the 111→112 MySQL upgrade
     * script.  MySQL DDL auto-commits each statement, so a mid-script failure leaves the
     * database partially upgraded.  On retry the full script must succeed because every
     * statement is guarded by {@code IF NOT EXISTS}, and the final state must match a fresh
     * install.
     *
     * <p>For k = 1 .. n-1, this test:</p>
     * <ol>
     *   <li>Sets up the v111 state (apicurio table only).</li>
     *   <li>Applies the first k upgrade statements to simulate a mid-script failure.</li>
     *   <li>Verifies {@code db_version} is still 111 (the marker is the last statement).</li>
     *   <li>Retries the full upgrade — must complete without error.</li>
     *   <li>Asserts all webhook tables exist and {@code db_version = 112}.</li>
     * </ol>
     */
    @ParameterizedTest(name = "partialUpgrade cutAt={0}")
    @org.junit.jupiter.params.provider.MethodSource("upgradeStatementIndices")
    void upgrade_partialCreate_isRetryableAtEveryCutPoint(int cutAt) throws Exception {
        try (Connection conn = openConnection()) {
            // Minimal v111 state.
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE apicurio (propName VARCHAR(255) NOT NULL PRIMARY KEY, propValue VARCHAR(512))");
                st.execute("INSERT INTO apicurio VALUES ('db_version', '111')");
            }
            assertDbVersion(conn, 111);

            List<String> upgradeStatements = new MySQLSqlStatements().databaseUpgrade(111, 112);

            // Apply first cutAt statements to simulate partial failure.
            for (int i = 0; i < cutAt; i++) {
                try (Statement st = conn.createStatement()) {
                    st.execute(upgradeStatements.get(i));
                }
            }

            // Version must still be 111 (the version marker is the last statement).
            assertDbVersion(conn, 111);

            // Retry the full upgrade — must succeed due to IF NOT EXISTS guards.
            applyStatements(conn, upgradeStatements);

            assertTableExists(conn, "webhook_subscriptions");
            assertTableExists(conn, "webhook_events");
            assertTableExists(conn, "webhook_delivery_logs");
            assertDbVersion(conn, 112);
        }
    }

    static List<Integer> upgradeStatementIndices() {
        List<String> statements = new MySQLSqlStatements().databaseUpgrade(111, 112);
        List<Integer> indices = new ArrayList<>();
        // Parameterize over all cut points except applying all statements (k = n-1, k = 0 would be no-op).
        for (int i = 1; i < statements.size(); i++) {
            indices.add(i);
        }
        return indices;
    }

    // ------------------------------------------------------------
    // Schema-parity test (fresh vs upgraded)
    // ------------------------------------------------------------

    /**
     * Normalized schema metadata must be identical between a fresh install and an upgraded DB.
     *
     * <p>Auto-generated names ignored: primary-key constraint names (MySQL auto-generates
     * {@code PRIMARY} as the index name for primary keys).  All other explicitly named
     * constraints ({@code UQ_*}, {@code FK_*}, {@code CK_*}) and indexes ({@code IDX_*}) are
     * compared.</p>
     */
    @Test
    void mysqlSchemaParity_freshMatchesUpgraded() throws Exception {
        // Set up fresh DB.
        try (Connection freshConn = openConnection()) {
            applyStatements(freshConn, new MySQLSqlStatements().databaseInitialization());

            // Rebuild for upgraded scenario in same container after a clean.
            cleanDatabase();
            try (Connection upgradedConn = openConnection()) {
                setupScenario(upgradedConn, "upgraded");

                for (String table : List.of("webhook_subscriptions", "webhook_events", "webhook_delivery_logs")) {
                    List<String> freshMeta = collectMysqlSchemaMeta(freshConn, table);
                    List<String> upgradedMeta = collectMysqlSchemaMeta(upgradedConn, table);
                    assertEquals(freshMeta, upgradedMeta,
                            "Schema parity failed for table " + table
                            + ". Fresh: " + freshMeta + " Upgraded: " + upgradedMeta);
                }
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
        String hash = WebhookEventIdentity.computeHash("https://my-dup.example.com", "my-dup-" + UUID.randomUUID());

        // Control: first insert succeeds.
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, 'https://my-dup.example.com', 'evt-1', ?, 'com.example.test', 0x41, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, hash);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
        // Duplicate identityHash must be rejected by UQ_whevents_1.
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, 'https://my-dup.example.com', 'evt-2', ?, 'com.example.test', 0x41, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, hash);
            ps.setLong(3, System.currentTimeMillis());
            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "UQ_whevents_1 must reject duplicate identityHash on MySQL");
            assertMysqlIntegrityError(ex, ER_DUP_ENTRY, "UQ_whevents_1");
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
                    "UQ_whdlogs_1 must reject duplicate (subscriptionId, eventRowId) on MySQL");
            assertMysqlIntegrityError(ex, ER_DUP_ENTRY, "UQ_whdlogs_1");
        }
    }

    private void assertDistinctSourcesSameEventIdAllowed(Connection conn) throws Exception {
        String sharedId = "shared-my-" + UUID.randomUUID();
        String hashA = WebhookEventIdentity.computeHash("https://src-a.my.example.com", sharedId);
        String hashB = WebhookEventIdentity.computeHash("https://src-b.my.example.com", sharedId);

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, ?, ?, ?, 'com.example.test', 0x41, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, "https://src-a.my.example.com");
            ps.setString(3, sharedId);
            ps.setString(4, hashA);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, ?, ?, ?, 'com.example.test', 0x41, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, "https://src-b.my.example.com");
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
                    "FK_whdlogs_2 must prevent deleting a referenced event on MySQL");
            assertMysqlIntegrityError(ex, ER_ROW_IS_REFERENCED_2, "FK_whdlogs_2");
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
                    "Event must be deletable after its delivery logs are removed (MySQL)");
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
                    "FK_whdlogs_1 must prevent deleting a referenced subscription on MySQL");
            assertMysqlIntegrityError(ex, ER_ROW_IS_REFERENCED_2, "FK_whdlogs_1");
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
                    "Subscription must be deletable after its delivery logs are removed (MySQL)");
        }
    }

    private void assertNegativeAttemptCountRejected(Connection conn) throws Exception {
        // Control: zero attemptCount must be accepted.
        String subId0 = UUID.randomUUID().toString();
        insertSubscription(conn, subId0);
        String eventRowId0 = UUID.randomUUID().toString();
        insertEvent(conn, eventRowId0);
        long now = System.currentTimeMillis();
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
                    "CK_whdlogs_1 must reject negative attemptCount on MySQL");
            assertMysqlIntegrityError(ex, ER_CHECK_CONSTRAINT_VIOLATED, "CK_whdlogs_1");
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
                    "FK_whdlogs_1 must reject delivery log with unknown subscriptionId on MySQL");
            assertMysqlIntegrityError(ex, ER_NO_REFERENCED_ROW_2, "FK_whdlogs_1");
        }

        // Control: delivery log with known parents inserts fine.
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
                    "FK_whdlogs_2 must reject delivery log with unknown eventRowId on MySQL");
            assertMysqlIntegrityError(ex, ER_NO_REFERENCED_ROW_2, "FK_whdlogs_2");
        }
    }

    // ------------------------------------------------------------
    // Schema metadata collection
    // ------------------------------------------------------------

    private List<String> collectMysqlSchemaMeta(Connection conn, String table) throws SQLException {
        List<String> descriptors = new ArrayList<>();
        String tLower = table.toLowerCase(Locale.ROOT);
        DatabaseMetaData meta = conn.getMetaData();

        // Columns.
        try (ResultSet rs = meta.getColumns(null, mysql.getDatabaseName(), tLower, null)) {
            while (rs.next()) {
                descriptors.add("COL:"
                        + rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT) + ":"
                        + rs.getString("TYPE_NAME").toLowerCase(Locale.ROOT) + ":"
                        + rs.getInt("COLUMN_SIZE") + ":"
                        + rs.getInt("NULLABLE") + ":"
                        + normalizeDefault(rs.getString("COLUMN_DEF")));
            }
        }

        // PK columns (not the PK index name, which is always 'PRIMARY' in MySQL).
        try (ResultSet rs = meta.getPrimaryKeys(null, mysql.getDatabaseName(), tLower)) {
            while (rs.next()) {
                descriptors.add("PK_COL:"
                        + rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT) + ":"
                        + rs.getInt("KEY_SEQ"));
            }
        }

        // FK constraints.
        try (ResultSet rs = meta.getImportedKeys(null, mysql.getDatabaseName(), tLower)) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                if (fkName == null) {
                    continue;
                }
                descriptors.add("FK:"
                        + fkName.toLowerCase(Locale.ROOT) + ":"
                        + rs.getString("FKCOLUMN_NAME").toLowerCase(Locale.ROOT) + ":"
                        + rs.getString("PKTABLE_NAME").toLowerCase(Locale.ROOT) + ":"
                        + rs.getString("PKCOLUMN_NAME").toLowerCase(Locale.ROOT) + ":"
                        + rs.getInt("DELETE_RULE"));
            }
        }

        // Unique indexes (UQ_* only).
        try (ResultSet rs = meta.getIndexInfo(null, mysql.getDatabaseName(), tLower, true, false)) {
            while (rs.next()) {
                String idxName = rs.getString("INDEX_NAME");
                int ordinal = rs.getInt("ORDINAL_POSITION");
                if (idxName == null || ordinal == 0) {
                    continue;
                }
                String idxLower = idxName.toLowerCase(Locale.ROOT);
                if (!idxLower.startsWith("uq_")) {
                    continue;
                }
                descriptors.add("UQ:"
                        + idxLower + ":"
                        + rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }

        // Non-unique indexes (IDX_*).
        try (ResultSet rs = meta.getIndexInfo(null, mysql.getDatabaseName(), tLower, false, false)) {
            while (rs.next()) {
                String idxName = rs.getString("INDEX_NAME");
                int ordinal = rs.getInt("ORDINAL_POSITION");
                if (idxName == null || ordinal == 0) {
                    continue;
                }
                String idxLower = idxName.toLowerCase(Locale.ROOT);
                if (!idxLower.startsWith("idx_")) {
                    continue;
                }
                descriptors.add("IDX:"
                        + idxLower + ":"
                        + rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }

        // Check constraints from information_schema.
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT LOWER(CONSTRAINT_NAME) FROM information_schema.TABLE_CONSTRAINTS "
                + "WHERE LOWER(TABLE_NAME) = LOWER(?) AND TABLE_SCHEMA = DATABASE() "
                + "AND CONSTRAINT_TYPE = 'CHECK' ORDER BY CONSTRAINT_NAME")) {
            ps.setString(1, tLower);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (name.startsWith("ck_")) {
                        descriptors.add("CK:" + name);
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
        return def.trim().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------
    // Scenario setup
    // ------------------------------------------------------------

    private void setupScenario(Connection conn, String scenario) throws Exception {
        if ("fresh".equals(scenario)) {
            applyStatements(conn, new MySQLSqlStatements().databaseInitialization());
        } else {
            // Minimal v111 state: only the apicurio table (MySQL has no full pre-peers DDL in
            // test resources, so we use the minimal approach: create the apicurio table with
            // version 111 to simulate a post-v111 state before the webhook upgrade).
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE apicurio (propName VARCHAR(255) NOT NULL PRIMARY KEY, propValue VARCHAR(512))");
                st.execute("INSERT INTO apicurio VALUES ('db_version', '111')");
            }
            assertDbVersion(conn, 111);
            applyStatements(conn, new MySQLSqlStatements().databaseUpgrade(111, 112));
            assertDbVersion(conn, 112);
        }
    }

    // ------------------------------------------------------------
    // Assertion helper
    // ------------------------------------------------------------

    /**
     * Asserts the MySQL error code and that the error message contains the expected constraint
     * name (compared case-insensitively via {@link Locale#ROOT}).
     *
     * <p>MySQL error messages for FK violations include the constraint name in the format:
     * {@code CONSTRAINT `FK_whdlogs_1` FOREIGN KEY ...}.  For duplicate key violations the
     * message includes the index/constraint name after {@code for key '...'}.  For check
     * constraint violations the message includes the constraint name explicitly.</p>
     */
    private static void assertMysqlIntegrityError(SQLException ex, int expectedCode, String expectedConstraint) {
        // MySQL 8 uses 23000 for unique/FK violations but HY000 for CHECK constraint violations (3819).
        String expectedState = (expectedCode == ER_CHECK_CONSTRAINT_VIOLATED)
                ? "HY000" : MYSQL_INTEGRITY_VIOLATION;
        assertEquals(expectedState, ex.getSQLState(),
                "Expected MySQL SQL state " + expectedState + " but got " + ex.getSQLState()
                + ". Message: " + ex.getMessage());
        assertEquals(expectedCode, ex.getErrorCode(),
                "Expected MySQL error code " + expectedCode + " but got " + ex.getErrorCode()
                + ". Message: " + ex.getMessage());
        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT)
                        .contains(expectedConstraint.toLowerCase(Locale.ROOT)),
                "Expected constraint name '" + expectedConstraint + "' in error message: "
                + ex.getMessage());
    }

    // ------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
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
                "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE LOWER(table_name) = LOWER(?) AND table_schema = DATABASE()")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertTrue(rs.getInt(1) > 0, "Table must exist on MySQL: " + tableName);
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
                + "VALUES (?, 'my-owner', 'https://endpoint.my.example.com', '[]', TRUE, 1, ?, ?)")) {
            ps.setString(1, subscriptionId);
            ps.setLong(2, now);
            ps.setLong(3, now);
            ps.executeUpdate();
        }
    }

    private static void insertEvent(Connection conn, String eventRowId) throws SQLException {
        String hash = WebhookEventIdentity.computeHash("https://my.example.com", eventRowId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events "
                + "(eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, 'https://my.example.com', ?, ?, 'com.example.test', 0x41, ?)")) {
            ps.setString(1, eventRowId);
            ps.setString(2, eventRowId);
            ps.setString(3, hash);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }
}
