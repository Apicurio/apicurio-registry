package io.apicurio.registry.storage.impl.sql;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.util.PSQLException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class WebhookSchemaPostgresqlMigrationTest {

    // PostgreSQL SQL states for integrity constraints.
    private static final String PG_UNIQUE_VIOLATION = "23505";
    private static final String PG_FK_VIOLATION = "23503";
    private static final String PG_CHECK_VIOLATION = "23514";

    // ------------------------------------------------------------
    // Fresh-install: all webhook tables and invariants hold
    // ------------------------------------------------------------

    @Test
    void freshInstall_webhookTablesAndConstraintsHold() throws Exception {
        EmbeddedPostgres pg = EmbeddedPostgres.start();
        try (Connection conn = pgConnection(pg)) {
            applyStatements(conn, new PostgreSQLSqlStatements().databaseInitialization());
            assertTableExists(conn, "webhook_subscriptions");
            assertTableExists(conn, "webhook_events");
            assertTableExists(conn, "webhook_delivery_logs");
            assertDbVersion(conn, 112);
            assertConstraintInvariants(conn);
        } finally {
            pg.close();
        }
    }

    // ------------------------------------------------------------
    // Upgrade: 111 → 112 from the real v111 schema with representative rows
    // ------------------------------------------------------------

    
    @Test
    void upgrade_fromRealV111Schema_createsWebhookTablesAndPreservesRows() throws Exception {
        EmbeddedPostgres pg = EmbeddedPostgres.start();
        try (Connection conn = pgConnection(pg)) {
            // Apply the real v110 baseline (all tables, no peers, no webhook).
            applyDdlResource(conn, "pre-peers-schema/postgresql.ddl");
            assertDbVersion(conn, 110);

            // Seed representative rows that must survive both upgrades.
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO global_rules (type, configuration) VALUES ('COMPATIBILITY', 'BACKWARD')");
                st.execute("INSERT INTO apicurio (propName, propValue) VALUES ('extra_key', 'extra_value')");
            }

            // Apply the peers upgrade (110 → 111).
            applyStatements(conn, new PostgreSQLSqlStatements().databaseUpgrade(110, 111));
            assertDbVersion(conn, 111);
            assertTableExists(conn, "peers");

            // Apply the webhook upgrade (111 → 112).
            applyStatements(conn, new PostgreSQLSqlStatements().databaseUpgrade(111, 112));
            assertDbVersion(conn, 112);

            assertTableExists(conn, "webhook_subscriptions");
            assertTableExists(conn, "webhook_events");
            assertTableExists(conn, "webhook_delivery_logs");

            // Seeded rows must be intact.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT configuration FROM global_rules WHERE type = 'COMPATIBILITY'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "global_rules row must survive the upgrade");
                    assertEquals("BACKWARD", rs.getString(1));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT propValue FROM apicurio WHERE propName = 'extra_key'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "extra apicurio property must survive the upgrade");
                    assertEquals("extra_value", rs.getString(1));
                }
            }

            // All constraint invariants must hold on the upgraded schema.
            assertConstraintInvariants(conn);
        } finally {
            pg.close();
        }
    }

    // ------------------------------------------------------------
    // Parameterized constraint-invariants (fresh AND upgraded)
    // ------------------------------------------------------------

    @ParameterizedTest(name = "constraintInvariants on {0} PostgreSQL database")
    @ValueSource(strings = {"fresh", "upgraded"})
    void constraintInvariants_onFreshAndUpgraded(String scenario) throws Exception {
        EmbeddedPostgres pg = EmbeddedPostgres.start();
        try (Connection conn = pgConnection(pg)) {
            setupScenario(conn, scenario);
            assertConstraintInvariants(conn);
        } finally {
            pg.close();
        }
    }

  
    @Test
    void pgSchemaParity_freshMatchesUpgraded() throws Exception {
        EmbeddedPostgres pgFresh = EmbeddedPostgres.start();
        EmbeddedPostgres pgUpgraded = EmbeddedPostgres.start();
        try (Connection fresh = pgConnection(pgFresh);
             Connection upgraded = pgConnection(pgUpgraded)) {
            applyStatements(fresh, new PostgreSQLSqlStatements().databaseInitialization());
            setupScenario(upgraded, "upgraded");
            for (String table : List.of("webhook_subscriptions", "webhook_events", "webhook_delivery_logs")) {
                List<String> freshMeta = collectPgSchemaMeta(fresh, table);
                List<String> upgradedMeta = collectPgSchemaMeta(upgraded, table);
                assertEquals(freshMeta, upgradedMeta,
                        "Schema parity failed for table " + table
                        + ". Fresh: " + freshMeta + " Upgraded: " + upgradedMeta);
            }
        } finally {
            pgFresh.close();
            pgUpgraded.close();
        }
    }

    /**
     * Proves that the schema-metadata comparison helper actually detects differences.
     * A deliberately added column must cause the comparison to fail, so parity-test
     * passes are not vacuous.
     */
    @Test
    void pgSchemaParity_mismatchIsDetected() throws Exception {
        EmbeddedPostgres pgRef = EmbeddedPostgres.start();
        EmbeddedPostgres pgDrift = EmbeddedPostgres.start();
        try (Connection ref = pgConnection(pgRef);
             Connection drift = pgConnection(pgDrift)) {
            applyStatements(ref, new PostgreSQLSqlStatements().databaseInitialization());
            applyStatements(drift, new PostgreSQLSqlStatements().databaseInitialization());
            // Add a deliberate schema drift to the second instance.
            try (Statement st = drift.createStatement()) {
                st.execute("ALTER TABLE webhook_events ADD COLUMN drift_col VARCHAR(1)");
            }
            List<String> refMeta = collectPgSchemaMeta(ref, "webhook_events");
            List<String> driftMeta = collectPgSchemaMeta(drift, "webhook_events");
            assertNotEquals(refMeta, driftMeta,
                    "Schema comparison helper must detect the deliberately added column");
        } finally {
            pgRef.close();
            pgDrift.close();
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
        String hash = WebhookEventIdentity.computeHash(
                "https://pg-dup-hash.example.com", "pg-dup-" + UUID.randomUUID());

        // Control: first insert succeeds.
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, 'https://pg-dup-hash.example.com', 'evt-1', ?, 'com.example.test', ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, hash);
            ps.setBytes(3, new byte[]{0x41});
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
        // Second insert with same identityHash must be rejected by UQ_whevents_1.
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, 'https://pg-dup-hash.example.com', 'evt-2', ?, 'com.example.test', ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, hash);
            ps.setBytes(3, new byte[]{0x41});
            ps.setLong(4, System.currentTimeMillis());
            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "UQ_whevents_1 must reject duplicate identityHash on PostgreSQL");
            assertPgIntegrityError(ex, PG_UNIQUE_VIOLATION, "uq_whevents_1");
        }
    }

    private void assertDuplicateDeliveryKeyRejected(Connection conn) throws Exception {
        String subId = UUID.randomUUID().toString();
        insertSubscription(conn, subId);
        String eventRowId = UUID.randomUUID().toString();
        insertEvent(conn, eventRowId);

        long now = System.currentTimeMillis();
        // Control: first insert succeeds.
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
        // Second insert with same (subscriptionId, eventRowId) must be rejected by UQ_whdlogs_1.
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
                    "UQ_whdlogs_1 must reject duplicate (subscriptionId, eventRowId) on PostgreSQL");
            assertPgIntegrityError(ex, PG_UNIQUE_VIOLATION, "uq_whdlogs_1");
        }
    }

    private void assertDistinctSourcesSameEventIdAllowed(Connection conn) throws Exception {
        String sharedEventId = "shared-pg-" + UUID.randomUUID();
        String hashA = WebhookEventIdentity.computeHash("https://src-a.pg.example.com", sharedEventId);
        String hashB = WebhookEventIdentity.computeHash("https://src-b.pg.example.com", sharedEventId);

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, ?, ?, ?, 'com.example.test', ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, "https://src-a.pg.example.com");
            ps.setString(3, sharedEventId);
            ps.setString(4, hashA);
            ps.setBytes(5, new byte[]{0x41});
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, ?, ?, ?, 'com.example.test', ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, "https://src-b.pg.example.com");
            ps.setString(3, sharedEventId);
            ps.setString(4, hashB);
            ps.setBytes(5, new byte[]{0x41});
            ps.setLong(6, System.currentTimeMillis());
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
                    "FK_whdlogs_2 must prevent deleting a referenced event on PostgreSQL");
            assertPgIntegrityError(ex, PG_FK_VIOLATION, "fk_whdlogs_2");
        }
        // Control: after removing the delivery log the event can be deleted.
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM webhook_delivery_logs WHERE eventRowId = ?")) {
            ps.setString(1, eventRowId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM webhook_events WHERE eventRowId = ?")) {
            ps.setString(1, eventRowId);
            assertEquals(1, ps.executeUpdate(),
                    "Event must be deletable after its delivery logs are removed (PostgreSQL)");
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
                    "FK_whdlogs_1 must prevent deleting a referenced subscription on PostgreSQL");
            assertPgIntegrityError(ex, PG_FK_VIOLATION, "fk_whdlogs_1");
        }
        // Control: after removing the delivery log the subscription can be deleted.
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM webhook_delivery_logs WHERE subscriptionId = ?")) {
            ps.setString(1, subId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM webhook_subscriptions WHERE subscriptionId = ?")) {
            ps.setString(1, subId);
            assertEquals(1, ps.executeUpdate(),
                    "Subscription must be deletable after its delivery logs are removed (PostgreSQL)");
        }
    }

    private void assertNegativeAttemptCountRejected(Connection conn) throws Exception {
        // Control: zero attemptCount (boundary) must be accepted.
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
                    "CK_whdlogs_1 must reject negative attemptCount on PostgreSQL");
            assertPgIntegrityError(ex, PG_CHECK_VIOLATION, "ck_whdlogs_1");
        }
    }

    private void assertParentMissingOnInsertRejected(Connection conn) throws Exception {
        long now = System.currentTimeMillis();

        // FK_whdlogs_1: insert delivery log with unknown subscriptionId.
        // Use a fresh UUID as fake ID: guaranteed 36 chars (fits VARCHAR(36)) and won't match any row.
        String eventForFk1 = UUID.randomUUID().toString();
        insertEvent(conn, eventForFk1);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_delivery_logs "
                + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                + "VALUES (?, ?, ?, 'PENDING', 0, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, UUID.randomUUID().toString()); // random UUID, no matching subscription
            ps.setString(3, eventForFk1);
            ps.setLong(4, now);
            ps.setLong(5, now);
            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "FK_whdlogs_1 must reject delivery log with unknown subscriptionId on PostgreSQL");
            assertPgIntegrityError(ex, PG_FK_VIOLATION, "fk_whdlogs_1");
        }

        // Control: inserting a delivery log with a known parent is accepted.
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

        // FK_whdlogs_2: insert delivery log with unknown eventRowId.
        String subForFk2 = UUID.randomUUID().toString();
        insertSubscription(conn, subForFk2);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_delivery_logs "
                + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                + "VALUES (?, ?, ?, 'PENDING', 0, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, subForFk2);
            ps.setString(3, UUID.randomUUID().toString()); // random UUID, no matching event
            ps.setLong(4, now);
            ps.setLong(5, now);
            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "FK_whdlogs_2 must reject delivery log with unknown eventRowId on PostgreSQL");
            assertPgIntegrityError(ex, PG_FK_VIOLATION, "fk_whdlogs_2");
        }
    }

    // ------------------------------------------------------------
    // Schema metadata collection
    // ------------------------------------------------------------

    private List<String> collectPgSchemaMeta(Connection conn, String table) throws SQLException {
        List<String> descriptors = new ArrayList<>();
        String tLower = table.toLowerCase(Locale.ROOT);
        DatabaseMetaData meta = conn.getMetaData();

        // Columns.
        try (ResultSet rs = meta.getColumns(null, "public", tLower, null)) {
            while (rs.next()) {
                descriptors.add("COL:"
                        + rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT) + ":"
                        + rs.getString("TYPE_NAME").toLowerCase(Locale.ROOT) + ":"
                        + rs.getInt("COLUMN_SIZE") + ":"
                        + rs.getInt("NULLABLE") + ":"
                        + normalizeDefault(rs.getString("COLUMN_DEF")));
            }
        }

        // PK columns (not PK constraint name — auto-generated by PostgreSQL).
        try (ResultSet rs = meta.getPrimaryKeys(null, "public", tLower)) {
            while (rs.next()) {
                descriptors.add("PK_COL:"
                        + rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT) + ":"
                        + rs.getInt("KEY_SEQ"));
            }
        }

        // FK constraints.
        try (ResultSet rs = meta.getImportedKeys(null, "public", tLower)) {
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

        // Unique indexes (UQ_* constraints).
        try (ResultSet rs = meta.getIndexInfo(null, "public", tLower, true, false)) {
            while (rs.next()) {
                String idxName = rs.getString("INDEX_NAME");
                int ordinal = rs.getInt("ORDINAL_POSITION");
                if (idxName == null || ordinal == 0) {
                    continue;
                }
                String idxLower = idxName.toLowerCase(Locale.ROOT);
                // Skip auto-generated PK backing index.
                if (idxLower.endsWith("_pkey") || !idxLower.startsWith("uq_")) {
                    continue;
                }
                descriptors.add("UQ:"
                        + idxLower + ":"
                        + ordinal + ":"
                        + rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }

        // Non-unique indexes (IDX_*).
        try (ResultSet rs = meta.getIndexInfo(null, "public", tLower, false, false)) {
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
                        + ordinal + ":"
                        + rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }

        // Check constraints: include the clause body so a regressed CHECK expression is detected.
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT LOWER(cc.constraint_name), LOWER(cc.check_clause) "
                + "FROM information_schema.check_constraints cc "
                + "JOIN information_schema.table_constraints tc "
                + "  ON cc.constraint_schema = tc.constraint_schema "
                + "  AND cc.constraint_name = tc.constraint_name "
                + "WHERE LOWER(tc.table_name) = LOWER(?) AND tc.constraint_type = 'CHECK' "
                + "ORDER BY cc.constraint_name")) {
            ps.setString(1, tLower);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (name.startsWith("ck_")) {
                        descriptors.add("CK:" + name + ":" + normalizePgCheckClause(rs.getString(2)));
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
        // PostgreSQL appends type casts like "::integer"; strip for comparison.
        String s = def.trim();
        int castIdx = s.indexOf("::");
        if (castIdx > 0) {
            s = s.substring(0, castIdx).trim();
        }
        return s.toLowerCase(Locale.ROOT);
    }

    private static String normalizePgCheckClause(String clause) {
        if (clause == null) {
            return "null";
        }
        String s = clause.trim().toLowerCase(Locale.ROOT);
        // Strip outer parentheses PostgreSQL wraps around the CHECK expression.
        while (s.startsWith("(") && s.endsWith(")")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    // ------------------------------------------------------------
    // Scenario setup
    // ------------------------------------------------------------

    private void setupScenario(Connection conn, String scenario) throws Exception {
        if ("fresh".equals(scenario)) {
            applyStatements(conn, new PostgreSQLSqlStatements().databaseInitialization());
        } else {
            applyDdlResource(conn, "pre-peers-schema/postgresql.ddl");
            applyStatements(conn, new PostgreSQLSqlStatements().databaseUpgrade(110, 111));
            applyStatements(conn, new PostgreSQLSqlStatements().databaseUpgrade(111, 112));
            assertDbVersion(conn, 112);
        }
    }

    // ------------------------------------------------------------
    // Assertion helpers
    // ------------------------------------------------------------

    /**
     * Asserts the SQL state and, via {@link PSQLException#getServerErrorMessage()}, the
     * constraint name (compared case-insensitively via {@link Locale#ROOT}).
     *
     * <p>PostgreSQL uses state 23503 for both FK child-exists and parent-missing violations,
     * which is why the constraint name is the only way to distinguish them.</p>
     */
    private static void assertPgIntegrityError(SQLException ex, String expectedState, String expectedConstraint) {
        assertEquals(expectedState, ex.getSQLState(),
                "Expected PostgreSQL SQL state " + expectedState + " but got " + ex.getSQLState()
                + ". Message: " + ex.getMessage());
        if (ex instanceof PSQLException) {
            PSQLException psqlEx = (PSQLException) ex;
            String actual = psqlEx.getServerErrorMessage() != null
                    ? psqlEx.getServerErrorMessage().getConstraint() : null;
            if (actual != null) {
                assertEquals(expectedConstraint.toLowerCase(Locale.ROOT), actual.toLowerCase(Locale.ROOT),
                        "Expected constraint name '" + expectedConstraint + "' but got '" + actual + "'");
            } else {
                // Fall back to message scan when server error message is not available.
                assertTrue(ex.getMessage().toLowerCase(Locale.ROOT)
                                .contains(expectedConstraint.toLowerCase(Locale.ROOT)),
                        "Expected constraint name '" + expectedConstraint + "' in error message: "
                        + ex.getMessage());
            }
        }
    }

    // ------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------

    private static Connection pgConnection(EmbeddedPostgres pg) throws Exception {
        return DriverManager.getConnection(pg.getJdbcUrl("postgres", "postgres"), "postgres", "");
    }

    private static void applyStatements(Connection conn, List<String> statements) throws SQLException {
        for (String sql : statements) {
            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            }
        }
    }

    private static void applyDdlResource(Connection conn, String resourcePath) throws Exception {
        String fullPath = "io/apicurio/registry/storage/impl/sql/" + resourcePath;
        try (InputStream in = WebhookSchemaPostgresqlMigrationTest.class
                .getClassLoader().getResourceAsStream(fullPath)) {
            if (in == null) {
                throw new IllegalStateException("DDL resource not found: " + fullPath);
            }
            String ddl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String stmt : ddl.split(";")) {
                StringBuilder sb = new StringBuilder();
                for (String line : stmt.split("\n")) {
                    int commentIdx = line.indexOf("--");
                    String stripped = commentIdx >= 0 ? line.substring(0, commentIdx) : line;
                    sb.append(stripped).append('\n');
                }
                String sql = sb.toString().trim();
                if (!sql.isEmpty()) {
                    try (Statement st = conn.createStatement()) {
                        st.execute(sql);
                    }
                }
            }
        }
    }

    private static void assertTableExists(Connection conn, String tableName) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = LOWER(?)")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertTrue(rs.getInt(1) > 0, "Table must exist: " + tableName);
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
                + "VALUES (?, 'pg-owner', 'https://endpoint.pg.example.com', '[]', TRUE, 1, ?, ?)")) {
            ps.setString(1, subscriptionId);
            ps.setLong(2, now);
            ps.setLong(3, now);
            ps.executeUpdate();
        }
    }

    private static void insertEvent(Connection conn, String eventRowId) throws SQLException {
        String hash = WebhookEventIdentity.computeHash("https://pg.example.com", eventRowId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events "
                + "(eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, 'https://pg.example.com', ?, ?, 'com.example.test', ?, ?)")) {
            ps.setString(1, eventRowId);
            ps.setString(2, eventRowId);
            ps.setString(3, hash);
            ps.setBytes(4, new byte[]{0x41});
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }
}
