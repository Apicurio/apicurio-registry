
package io.apicurio.registry.storage.impl.sql;

import org.h2.api.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class WebhookSchemaMigrationTest {

    private static final int VERSION_BEFORE = 110;
    private static final int VERSION_PEERS = 111;
    private static final int VERSION_AFTER = 112;

    // ------------------------------------------------------------
    // Fresh-install tests
    // ------------------------------------------------------------

    @Test
    void freshInstall_allWebhookTablesExist() throws Exception {
        try (Connection conn = freshH2()) {
            applyStatements(conn, new H2SqlStatements().databaseInitialization());
            assertTableExists(conn, "webhook_subscriptions");
            assertTableExists(conn, "webhook_events");
            assertTableExists(conn, "webhook_delivery_logs");
        }
    }

    @Test
    void freshInstall_dbVersionIs112() throws Exception {
        try (Connection conn = freshH2()) {
            applyStatements(conn, new H2SqlStatements().databaseInitialization());
            assertDbVersion(conn, VERSION_AFTER);
        }
    }

    @Test
    void freshInstall_subscriptionsColumnsExist() throws Exception {
        try (Connection conn = freshH2()) {
            applyStatements(conn, new H2SqlStatements().databaseInitialization());
            assertColumnExists(conn, "webhook_subscriptions", "subscriptionId");
            assertColumnExists(conn, "webhook_subscriptions", "ownerId");
            assertColumnExists(conn, "webhook_subscriptions", "endpointUrl");
            assertColumnExists(conn, "webhook_subscriptions", "eventTypes");
            assertColumnExists(conn, "webhook_subscriptions", "enabled");
            assertColumnExists(conn, "webhook_subscriptions", "revision");
            assertColumnExists(conn, "webhook_subscriptions", "createdOn");
            assertColumnExists(conn, "webhook_subscriptions", "modifiedOn");
        }
    }

    @Test
    void freshInstall_eventsColumnsExist() throws Exception {
        try (Connection conn = freshH2()) {
            applyStatements(conn, new H2SqlStatements().databaseInitialization());
            assertColumnExists(conn, "webhook_events", "eventRowId");
            assertColumnExists(conn, "webhook_events", "source");
            assertColumnExists(conn, "webhook_events", "eventId");
            assertColumnExists(conn, "webhook_events", "identityHash");
            assertColumnExists(conn, "webhook_events", "eventType");
            assertColumnExists(conn, "webhook_events", "payload");
            assertColumnExists(conn, "webhook_events", "createdOn");
        }
    }

    @Test
    void freshInstall_deliveryLogsColumnsExist() throws Exception {
        try (Connection conn = freshH2()) {
            applyStatements(conn, new H2SqlStatements().databaseInitialization());
            assertColumnExists(conn, "webhook_delivery_logs", "deliveryId");
            assertColumnExists(conn, "webhook_delivery_logs", "subscriptionId");
            assertColumnExists(conn, "webhook_delivery_logs", "eventRowId");
            assertColumnExists(conn, "webhook_delivery_logs", "status");
            assertColumnExists(conn, "webhook_delivery_logs", "attemptCount");
            assertColumnExists(conn, "webhook_delivery_logs", "claimToken");
            assertColumnExists(conn, "webhook_delivery_logs", "completedOn");
        }
    }

    // ------------------------------------------------------------
    // Upgrade-parity test
    // ------------------------------------------------------------

    @Test
    void upgrade_fromRealV110BaselineProducesWebhookTables() throws Exception {
        try (Connection conn = freshH2()) {
            // Start from the actual pinned v110 schema — not a fresh install with tables dropped.
            executeDdlResource(conn, "pre-peers-schema/h2.ddl");
            assertDbVersion(conn, VERSION_BEFORE);

            // Seed an unrelated apicurio property that must survive the upgrade unchanged.
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO apicurio (propName, propValue) VALUES ('some_other_key', 'original_value')");
            }

            // Seed a row in an existing pre-webhook table to verify data is preserved.
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO global_rules (type, configuration) VALUES (?, ?)")) {
                ps.setString(1, "COMPATIBILITY");
                ps.setString(2, "BACKWARD");
                ps.executeUpdate();
            }

            // Apply the full upgrade chain: 110 → 111 (peers) → 112 (webhooks).
            applyStatements(conn, new H2SqlStatements().databaseUpgrade(110, 111));
            assertDbVersion(conn, VERSION_PEERS);
            applyStatements(conn, new H2SqlStatements().databaseUpgrade(111, 112));

            // All three webhook tables must now exist.
            assertTableExists(conn, "webhook_subscriptions");
            assertTableExists(conn, "webhook_events");
            assertTableExists(conn, "webhook_delivery_logs");

            // Version marker must be 112.
            assertDbVersion(conn, VERSION_AFTER);

            // Unrelated apicurio property must be unchanged.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT propValue FROM apicurio WHERE propName = 'some_other_key'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "some_other_key row must still exist after upgrade");
                    assertEquals("original_value", rs.getString(1),
                            "Upgrade must not modify unrelated apicurio properties");
                }
            }

            // Pre-existing data in an old table must survive.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT configuration FROM global_rules WHERE type = 'COMPATIBILITY'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Pre-existing global_rules row must survive the upgrade");
                    assertEquals("BACKWARD", rs.getString(1));
                }
            }
        }
    }

    @Test
    void upgrade_fromV111BaselinePreservesPeersTableAndData() throws Exception {
        try (Connection conn = freshH2()) {
            // Build a genuine v111 state: apply the pinned v110 baseline then the peers migration.
            executeDdlResource(conn, "pre-peers-schema/h2.ddl");
            applyStatements(conn, new H2SqlStatements().databaseUpgrade(110, 111));
            assertDbVersion(conn, VERSION_PEERS);
            assertTableExists(conn, "peers");

            // Seed a peer row that must survive the 112 (webhook) upgrade unchanged.
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO peers (peerId, url, name, description, enabled) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, "test-peer-1");
                ps.setString(2, "https://peer.example.com/apis/registry/v3");
                ps.setString(3, "Test Peer");
                ps.setString(4, "A peer registry for testing");
                ps.setBoolean(5, true);
                ps.executeUpdate();
            }

            // Apply the 112 (webhooks) upgrade.
            applyStatements(conn, new H2SqlStatements().databaseUpgrade(111, 112));
            assertDbVersion(conn, VERSION_AFTER);

            // The peers table must still exist after the 112 upgrade.
            assertTableExists(conn, "peers");

            // The seeded peer row must be intact.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT url, name, enabled FROM peers WHERE peerId = 'test-peer-1'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Peer row must survive the 112 upgrade");
                    assertEquals("https://peer.example.com/apis/registry/v3", rs.getString("url"));
                    assertEquals("Test Peer", rs.getString("name"));
                    assertTrue(rs.getBoolean("enabled"));
                }
            }
        }
    }

    // ------------------------------------------------------------
    // Partial-upgrade retry test
    // ------------------------------------------------------------

    /**
     * Simulates a MySQL-style partial failure during the 112 (webhook) upgrade: the first
     * CREATE TABLE auto-committed but the process died before the rest of the script ran,
     * leaving the version marker at 111.  On the next start the upgrade runner reads version 111,
     * re-runs the 112 upgrade script, and must succeed because every statement is guarded by
     * IF NOT EXISTS.
     */
    @Test
    void upgrade_partialApplication_isRetryable() throws Exception {
        try (Connection conn = freshH2()) {
            // Start from v111 (peers already applied).
            executeDdlResource(conn, "pre-peers-schema/h2.ddl");
            applyStatements(conn, new H2SqlStatements().databaseUpgrade(110, 111));
            assertDbVersion(conn, VERSION_PEERS);

            // Simulate partial failure during 112: webhook_subscriptions was committed but the
            // script died before webhook_events, webhook_delivery_logs, and the version marker
            // were written.
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE webhook_subscriptions ("
                        + "subscriptionId VARCHAR(36) NOT NULL,"
                        + "name VARCHAR(512),"
                        + "ownerId VARCHAR(256) NOT NULL,"
                        + "endpointUrl VARCHAR(2048) NOT NULL,"
                        + "eventTypes TEXT NOT NULL,"
                        + "groupFilter VARCHAR(512),"
                        + "artifactIdFilter VARCHAR(512),"
                        + "artifactTypeFilter VARCHAR(32),"
                        + "enabled BOOLEAN NOT NULL,"
                        + "deletedOn BIGINT,"
                        + "revision BIGINT NOT NULL,"
                        + "signingSecretRef VARCHAR(512),"
                        + "createdOn BIGINT NOT NULL,"
                        + "modifiedOn BIGINT NOT NULL,"
                        + "PRIMARY KEY (subscriptionId)"
                        + ")");
            }

            // Version is still 111: the marker had not been written yet (it is last in the script).
            assertDbVersion(conn, VERSION_PEERS);

            // Retry: the full 112 upgrade script must succeed without "table already exists" errors.
            applyStatements(conn, new H2SqlStatements().databaseUpgrade(111, 112));

            // All three webhook tables must now exist and the version must be 112.
            assertTableExists(conn, "webhook_subscriptions");
            assertTableExists(conn, "webhook_events");
            assertTableExists(conn, "webhook_delivery_logs");
            assertDbVersion(conn, VERSION_AFTER);
        }
    }

    // ------------------------------------------------------------
    // Binary payload round-trip
    // ------------------------------------------------------------

    @Test
    void payloadRoundTrip_includesNullBytesAndHighBytes() throws Exception {
        try (Connection conn = freshH2()) {
            applyStatements(conn, new H2SqlStatements().databaseInitialization());

            String subId = UUID.randomUUID().toString();
            insertSubscription(conn, subId);

            // Payload with null byte, 0xFF, and invalid-UTF-8 sequence 0x80.
            byte[] original = new byte[]{0x41, 0x00, (byte) 0xFF, (byte) 0x80, 0x42};
            String eventRowId = UUID.randomUUID().toString();
            String hash = WebhookEventIdentity.computeHash("https://example.com", "evt-rt-1");

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, eventRowId);
                ps.setString(2, "https://example.com");
                ps.setString(3, "evt-rt-1");
                ps.setString(4, hash);
                ps.setString(5, "com.example.test");
                ps.setBytes(6, original);
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            }

            byte[] retrieved;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT payload FROM webhook_events WHERE eventRowId = ?")) {
                ps.setString(1, eventRowId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    retrieved = rs.getBytes("payload");
                }
            }

            assertArrayEquals(original, retrieved, "Binary payload must survive a round-trip unchanged");
        }
    }

    @Test
    void payloadRoundTrip_validCloudEventJson() throws Exception {
        try (Connection conn = freshH2()) {
            applyStatements(conn, new H2SqlStatements().databaseInitialization());

            String subId = UUID.randomUUID().toString();
            insertSubscription(conn, subId);

            // A minimal but spec-compliant CloudEvent serialized as UTF-8 JSON.
            String cloudEventJson = "{\"specversion\":\"1.0\","
                    + "\"type\":\"io.apicurio.registry.artifact.created\","
                    + "\"source\":\"https://registry.example.com\","
                    + "\"id\":\"ce-valid-001\","
                    + "\"datacontenttype\":\"application/json\","
                    + "\"data\":{\"groupId\":\"default\",\"artifactId\":\"my-schema\"}}";
            byte[] original = cloudEventJson.getBytes(StandardCharsets.UTF_8);

            String eventRowId = UUID.randomUUID().toString();
            String hash = WebhookEventIdentity.computeHash("https://registry.example.com", "ce-valid-001");

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, eventRowId);
                ps.setString(2, "https://registry.example.com");
                ps.setString(3, "ce-valid-001");
                ps.setString(4, hash);
                ps.setString(5, "io.apicurio.registry.artifact.created");
                ps.setBytes(6, original);
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            }

            byte[] retrieved;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT payload FROM webhook_events WHERE eventRowId = ?")) {
                ps.setString(1, eventRowId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    retrieved = rs.getBytes("payload");
                }
            }

            assertArrayEquals(original, retrieved, "Valid CloudEvent JSON payload must survive a round-trip unchanged");
            assertEquals(cloudEventJson, new String(retrieved, StandardCharsets.UTF_8),
                    "Retrieved payload must decode to the original JSON string");
        }
    }

    // ------------------------------------------------------------
    // Foreign-key enforcement — standalone tests
    // ------------------------------------------------------------

    @Test
    void fkWhdlogs1_rejectsUnknownSubscriptionId() throws Exception {
        try (Connection conn = freshH2()) {
            applyStatements(conn, new H2SqlStatements().databaseInitialization());

            String eventRowId = UUID.randomUUID().toString();
            insertEvent(conn, eventRowId);

            long now = System.currentTimeMillis();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO webhook_delivery_logs "
                    + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                    + "VALUES (?, ?, ?, 'PENDING', 0, ?, ?)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, "no-such-subscription");  // FK violation
                ps.setString(3, eventRowId);
                ps.setLong(4, now);
                ps.setLong(5, now);
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                        "FK_whdlogs_1 must reject unknown subscriptionId");
                assertH2IntegrityError(ex,
                        ErrorCode.REFERENTIAL_INTEGRITY_VIOLATED_PARENT_MISSING_1,
                        "FK_whdlogs_1");
            }
        }
    }

    @Test
    void fkWhdlogs2_rejectsUnknownEventRowId() throws Exception {
        try (Connection conn = freshH2()) {
            applyStatements(conn, new H2SqlStatements().databaseInitialization());

            String subId = UUID.randomUUID().toString();
            insertSubscription(conn, subId);

            long now = System.currentTimeMillis();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO webhook_delivery_logs "
                    + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                    + "VALUES (?, ?, ?, 'PENDING', 0, ?, ?)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, subId);
                ps.setString(3, "no-such-event");  // FK violation
                ps.setLong(4, now);
                ps.setLong(5, now);
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                        "FK_whdlogs_2 must reject unknown eventRowId");
                assertH2IntegrityError(ex,
                        ErrorCode.REFERENTIAL_INTEGRITY_VIOLATED_PARENT_MISSING_1,
                        "FK_whdlogs_2");
            }
        }
    }

    // ------------------------------------------------------------
    // Check constraint — standalone test
    // ------------------------------------------------------------

    @Test
    void checkConstraint_rejectsNegativeAttemptCount() throws Exception {
        try (Connection conn = freshH2()) {
            applyStatements(conn, new H2SqlStatements().databaseInitialization());

            String subId = UUID.randomUUID().toString();
            insertSubscription(conn, subId);
            String eventRowId = UUID.randomUUID().toString();
            insertEvent(conn, eventRowId);

            long now = System.currentTimeMillis();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO webhook_delivery_logs "
                    + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                    + "VALUES (?, ?, ?, 'PENDING', -1, ?, ?)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, subId);
                ps.setString(3, eventRowId);
                ps.setLong(4, now);
                ps.setLong(5, now);
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                        "CK_whdlogs_1 must reject negative attemptCount");
                assertH2IntegrityError(ex,
                        ErrorCode.CHECK_CONSTRAINT_VIOLATED_1,
                        "CK_whdlogs_1");
            }
        }
    }

    // ------------------------------------------------------------
    // Parameterized constraint-invariants test (fresh AND upgraded)
    // ------------------------------------------------------------

    /**
     * All structural invariants (unique constraints, FK protection, check constraints) must hold
     * on both a fresh install and a fully upgraded database.
     *
     * <p>"fresh" = full version-112 initialization DDL on an empty DB.</p>
     * <p>"upgraded" = real v110 schema + v111 upgrade + seed rows + v112 upgrade, with
     * verification that seeded rows are preserved and db_version = 112.</p>
     */
    @ParameterizedTest(name = "constraintInvariants on {0} database")
    @ValueSource(strings = {"fresh", "upgraded"})
    void constraintInvariants_onFreshAndUpgraded(String scenario) throws Exception {
        try (Connection conn = openScenarioH2(scenario)) {
            assertConstraintInvariants(conn);
        }
    }

    // ------------------------------------------------------------
    // Schema-parity test (fresh vs upgraded H2)
    // ------------------------------------------------------------

    /**
     * Normalized schema metadata (columns, primary keys, foreign keys, unique constraints,
     * check constraints, indexes) must be identical between a fresh install and an upgraded DB.
     *
     * <p>Auto-generated names ignored: primary-key constraint names, because the webhook DDL
     * does not assign explicit names to primary-key constraints and H2 auto-generates them
     * (e.g. {@code CONSTRAINT_5}).  All other constraint and index names are explicit and
     * therefore comparable.</p>
     */
    @Test
    void h2SchemaParity_freshMatchesUpgraded() throws Exception {
        try (Connection fresh = openScenarioH2("fresh");
             Connection upgraded = openScenarioH2("upgraded")) {
            for (String table : List.of("webhook_subscriptions", "webhook_events", "webhook_delivery_logs")) {
                List<String> freshMeta = collectH2SchemaMeta(fresh, table);
                List<String> upgradedMeta = collectH2SchemaMeta(upgraded, table);
                assertEquals(freshMeta, upgradedMeta,
                        "Schema parity failed for table " + table
                        + ". Fresh: " + freshMeta + " Upgraded: " + upgradedMeta);
            }
        }
    }

    /**
     * Proves that the schema-metadata comparison helper actually detects differences.
     * A deliberately added column must cause the comparison to fail, so parity-test
     * passes are not vacuous.
     */
    @Test
    void h2SchemaParity_mismatchIsDetected() throws Exception {
        try (Connection ref = openScenarioH2("fresh");
             Connection drift = openScenarioH2("fresh")) {
            // Add a deliberate schema drift to the drift connection.
            try (Statement st = drift.createStatement()) {
                st.execute("ALTER TABLE webhook_events ADD COLUMN drift_col VARCHAR(1)");
            }
            List<String> refMeta = collectH2SchemaMeta(ref, "webhook_events");
            List<String> driftMeta = collectH2SchemaMeta(drift, "webhook_events");
            assertNotEquals(refMeta, driftMeta,
                    "Schema comparison helper must detect the deliberately added column");
        }
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    /**
     * Asserts the exact H2 error code (via {@link ErrorCode} constants) and that the error
     * message contains the expected constraint name (compared case-insensitively via
     * {@link Locale#ROOT}).
     */
    private static void assertH2IntegrityError(SQLException ex, int expectedCode, String expectedConstraint) {
        assertEquals(expectedCode, ex.getErrorCode(),
                "Expected H2 error code " + expectedCode + " but got " + ex.getErrorCode()
                + " (SQL state " + ex.getSQLState() + "). Message: " + ex.getMessage());
        assertTrue(ex.getMessage().toUpperCase(Locale.ROOT)
                        .contains(expectedConstraint.toUpperCase(Locale.ROOT)),
                "Expected constraint name '" + expectedConstraint + "' in error message: "
                + ex.getMessage());
    }

    private void assertConstraintInvariants(Connection conn) throws Exception {
        // 1. Duplicate identityHash rejected; control: different hash inserts fine.
        assertDuplicateIdentityHashRejected(conn);
        // 2. Duplicate (subscriptionId, eventRowId) rejected; control: different pair inserts fine.
        assertDuplicateDeliveryKeyRejected(conn);
        // 3. Same eventId, two different sources → both inserts succeed.
        assertDistinctSourcesSameEventIdAllowed(conn);
        // 4 & 5. Delete referenced event / subscription rejected; unreferenced deletion succeeds.
        assertDeleteReferencedEventRejected(conn);
        assertDeleteReferencedSubscriptionRejected(conn);
        // 6. Negative attemptCount rejected; zero (boundary) is accepted.
        assertNegativeAttemptCountRejected(conn);
        // 7. Missing parent on insert rejected for both FK_whdlogs_1 and FK_whdlogs_2.
        assertParentMissingOnInsertRejected(conn);
    }

    private void assertDuplicateIdentityHashRejected(Connection conn) throws Exception {
        String hash = WebhookEventIdentity.computeHash("https://dup-hash.example.com", "dup-hash-evt-" + UUID.randomUUID());

        // Control: first insert must succeed.
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, 'https://dup-hash.example.com', 'dup-hash-evt', ?, 'com.example.test', X'41', ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, hash);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }

        // Second insert with the same identityHash must be rejected by UQ_whevents_1.
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, 'https://dup-hash.example.com', 'dup-hash-evt-2', ?, 'com.example.test', X'41', ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, hash);
            ps.setLong(3, System.currentTimeMillis());
            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "Duplicate identityHash must be rejected by UQ_whevents_1");
            assertH2IntegrityError(ex, ErrorCode.DUPLICATE_KEY_1, "UQ_whevents_1");
        }
    }

    private void assertDuplicateDeliveryKeyRejected(Connection conn) throws Exception {
        String subId = UUID.randomUUID().toString();
        insertSubscription(conn, subId);
        String eventRowId = UUID.randomUUID().toString();
        insertEvent(conn, eventRowId);

        long now = System.currentTimeMillis();
        // Control: first delivery log must succeed.
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

        // Second insert with the same (subscriptionId, eventRowId) must be rejected by UQ_whdlogs_1.
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
                    "Duplicate (subscriptionId, eventRowId) must be rejected by UQ_whdlogs_1");
            assertH2IntegrityError(ex, ErrorCode.DUPLICATE_KEY_1, "UQ_whdlogs_1");
        }
    }

    private void assertDistinctSourcesSameEventIdAllowed(Connection conn) throws Exception {
        // The identityHash incorporates the source, so the same eventId under two different
        // sources must hash differently and both rows must be accepted.
        String sharedEventId = "shared-evt-" + UUID.randomUUID();
        String hashA = WebhookEventIdentity.computeHash("https://source-a.example.com", sharedEventId);
        String hashB = WebhookEventIdentity.computeHash("https://source-b.example.com", sharedEventId);

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, ?, ?, ?, 'com.example.test', X'41', ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, "https://source-a.example.com");
            ps.setString(3, sharedEventId);
            ps.setString(4, hashA);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, ?, ?, ?, 'com.example.test', X'41', ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, "https://source-b.example.com");
            ps.setString(3, sharedEventId);
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

        // Negative: delete the event while a delivery log references it must be refused.
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM webhook_events WHERE eventRowId = ?")) {
            ps.setString(1, eventRowId);
            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "FK_whdlogs_2 must prevent deleting an event that has delivery logs");
            assertH2IntegrityError(ex,
                    ErrorCode.REFERENTIAL_INTEGRITY_VIOLATED_CHILD_EXISTS_1,
                    "FK_whdlogs_2");
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
            assertEquals(1, ps.executeUpdate(), "Event must be deletable after its delivery logs are removed");
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

        // Negative: delete the subscription while a delivery log references it must be refused.
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM webhook_subscriptions WHERE subscriptionId = ?")) {
            ps.setString(1, subId);
            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "FK_whdlogs_1 must prevent deleting a subscription that has delivery logs");
            assertH2IntegrityError(ex,
                    ErrorCode.REFERENTIAL_INTEGRITY_VIOLATED_CHILD_EXISTS_1,
                    "FK_whdlogs_1");
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
            assertEquals(1, ps.executeUpdate(), "Subscription must be deletable after its delivery logs are removed");
        }
    }

    private void assertNegativeAttemptCountRejected(Connection conn) throws Exception {
        // Control: zero attemptCount (boundary value) must be accepted.
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
                    "CK_whdlogs_1 must reject negative attemptCount");
            assertH2IntegrityError(ex, ErrorCode.CHECK_CONSTRAINT_VIOLATED_1, "CK_whdlogs_1");
        }
    }

    private void assertParentMissingOnInsertRejected(Connection conn) throws Exception {
        long now = System.currentTimeMillis();

        // FK_whdlogs_1: insert delivery log with unknown subscriptionId.
        String eventRowIdForFk1 = UUID.randomUUID().toString();
        insertEvent(conn, eventRowIdForFk1);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_delivery_logs "
                + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                + "VALUES (?, ?, ?, 'PENDING', 0, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, UUID.randomUUID().toString()); // random UUID — no matching subscription
            ps.setString(3, eventRowIdForFk1);
            ps.setLong(4, now);
            ps.setLong(5, now);
            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "FK_whdlogs_1 must reject delivery log with unknown subscriptionId");
            assertH2IntegrityError(ex,
                    ErrorCode.REFERENTIAL_INTEGRITY_VIOLATED_PARENT_MISSING_1,
                    "FK_whdlogs_1");
        }

        // Control: delivery log referencing an existing subscription inserts fine.
        String subIdCtrl = UUID.randomUUID().toString();
        insertSubscription(conn, subIdCtrl);
        String eventRowIdCtrl = UUID.randomUUID().toString();
        insertEvent(conn, eventRowIdCtrl);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_delivery_logs "
                + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                + "VALUES (?, ?, ?, 'PENDING', 0, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, subIdCtrl);
            ps.setString(3, eventRowIdCtrl);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate(); // must not throw
        }

        // FK_whdlogs_2: insert delivery log with unknown eventRowId.
        String subIdForFk2 = UUID.randomUUID().toString();
        insertSubscription(conn, subIdForFk2);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_delivery_logs "
                + "(deliveryId, subscriptionId, eventRowId, status, attemptCount, createdOn, updatedOn) "
                + "VALUES (?, ?, ?, 'PENDING', 0, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, subIdForFk2);
            ps.setString(3, UUID.randomUUID().toString()); // random UUID — no matching event
            ps.setLong(4, now);
            ps.setLong(5, now);
            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "FK_whdlogs_2 must reject delivery log with unknown eventRowId");
            assertH2IntegrityError(ex,
                    ErrorCode.REFERENTIAL_INTEGRITY_VIOLATED_PARENT_MISSING_1,
                    "FK_whdlogs_2");
        }
    }

    /**
     * Opens an H2 in-memory connection pre-populated according to the given scenario.
     *
     * <ul>
     *   <li>{@code "fresh"} – applies the full v112 initialization DDL to an empty DB.</li>
     *   <li>{@code "upgraded"} – applies the pinned v110 baseline, seeds representative rows
     *       (an extra {@code apicurio} property and a {@code global_rules} row), runs the full
     *       v110→v111→v112 upgrade chain, and verifies the seeded rows are preserved and
     *       {@code db_version = 112}.</li>
     * </ul>
     */
    private Connection openScenarioH2(String scenario) throws Exception {
        Connection conn = freshH2();
        if ("fresh".equals(scenario)) {
            applyStatements(conn, new H2SqlStatements().databaseInitialization());
        } else {
            // Load v110 baseline via test resource (no production path exists for this pinned schema).
            executeDdlResource(conn, "pre-peers-schema/h2.ddl");
            // Seed rows that must survive the upgrade.
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO apicurio (propName, propValue) VALUES ('scenario_key', 'scenario_value')");
                st.execute("INSERT INTO global_rules (type, configuration) VALUES ('VALIDITY', 'FULL')");
            }
            // Apply upgrades via the production DdlParser path (not a manual split).
            applyStatements(conn, new H2SqlStatements().databaseUpgrade(110, 112));
            assertDbVersion(conn, VERSION_AFTER);
            // Verify seeded rows survived.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT propValue FROM apicurio WHERE propName = 'scenario_key'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Seeded apicurio property must survive upgrade");
                    assertEquals("scenario_value", rs.getString(1));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT configuration FROM global_rules WHERE type = 'VALIDITY'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Seeded global_rules row must survive upgrade");
                    assertEquals("FULL", rs.getString(1));
                }
            }
        }
        return conn;
    }

    /**
     * Collects normalized schema descriptors for the given table.  The result is a sorted list
     * of strings, one per schema element.  Descriptors use uppercase names via
     * {@link Locale#ROOT} so that comparisons are locale-neutral.
     *
     * <p>Auto-generated names ignored: primary-key constraint names (H2 generates them when the
     * DDL does not assign one; e.g. {@code CONSTRAINT_5}).  All other names — unique constraints
     * ({@code UQ_*}), foreign keys ({@code FK_*}), check constraints ({@code CK_*}), and
     * indexes ({@code IDX_*}) — are explicit and therefore included.</p>
     */
    private List<String> collectH2SchemaMeta(Connection conn, String table) throws SQLException {
        List<String> descriptors = new ArrayList<>();
        String t = table.toUpperCase(Locale.ROOT);
        DatabaseMetaData meta = conn.getMetaData();

        // Columns: name, type, size, nullable, default.
        try (ResultSet rs = meta.getColumns(null, null, t, null)) {
            while (rs.next()) {
                descriptors.add("COL:"
                        + rs.getString("COLUMN_NAME").toUpperCase(Locale.ROOT) + ":"
                        + rs.getString("TYPE_NAME").toUpperCase(Locale.ROOT) + ":"
                        + rs.getInt("COLUMN_SIZE") + ":"
                        + rs.getInt("NULLABLE") + ":"
                        + normalizeDefault(rs.getString("COLUMN_DEF")));
            }
        }

        // Primary-key columns (ordinal only; constraint name is auto-generated by H2).
        try (ResultSet rs = meta.getPrimaryKeys(null, null, t)) {
            while (rs.next()) {
                descriptors.add("PK_COL:"
                        + rs.getString("COLUMN_NAME").toUpperCase(Locale.ROOT) + ":"
                        + rs.getInt("KEY_SEQ"));
            }
        }

        // Foreign-key constraints: FK name, FK column, referenced table, referenced column,
        // and delete rule.
        try (ResultSet rs = meta.getImportedKeys(null, null, t)) {
            while (rs.next()) {
                descriptors.add("FK:"
                        + rs.getString("FK_NAME").toUpperCase(Locale.ROOT) + ":"
                        + rs.getString("FKCOLUMN_NAME").toUpperCase(Locale.ROOT) + ":"
                        + rs.getString("PKTABLE_NAME").toUpperCase(Locale.ROOT) + ":"
                        + rs.getString("PKCOLUMN_NAME").toUpperCase(Locale.ROOT) + ":"
                        + rs.getInt("DELETE_RULE"));
            }
        }

        // Unique constraints (backing indexes for CONSTRAINT ... UNIQUE).
        try (ResultSet rs = meta.getIndexInfo(null, null, t, true, false)) {
            while (rs.next()) {
                String idxName = rs.getString("INDEX_NAME");
                int ordinal = rs.getInt("ORDINAL_POSITION");
                if (idxName == null || ordinal == 0) {
                    continue; // statistics row
                }
                // Skip auto-generated PK backing indexes (share H2's auto-generated PK name).
                if (idxName.toUpperCase(Locale.ROOT).startsWith("PRIMARY_KEY_")
                        || idxName.toUpperCase(Locale.ROOT).startsWith("CONSTRAINT_")) {
                    continue;
                }
                descriptors.add("UQ:"
                        + idxName.toUpperCase(Locale.ROOT) + ":"
                        + ordinal + ":"
                        + rs.getString("COLUMN_NAME").toUpperCase(Locale.ROOT));
            }
        }

        // Non-unique indexes (IDX_* only).
        try (ResultSet rs = meta.getIndexInfo(null, null, t, false, false)) {
            while (rs.next()) {
                String idxName = rs.getString("INDEX_NAME");
                int ordinal = rs.getInt("ORDINAL_POSITION");
                if (idxName == null || ordinal == 0) {
                    continue;
                }
                String idxNameUpper = idxName.toUpperCase(Locale.ROOT);
                if (!idxNameUpper.startsWith("IDX_")) {
                    continue;
                }
                if (!rs.getBoolean("NON_UNIQUE")) {
                    continue; // already captured as UQ above
                }
                descriptors.add("IDX:"
                        + idxNameUpper + ":"
                        + ordinal + ":"
                        + rs.getString("COLUMN_NAME").toUpperCase(Locale.ROOT));
            }
        }

        // Check constraints (CK_* only): include the clause body so a regressed CHECK
        // expression is detected, not just that the constraint name is present.
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE "
                + "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc "
                + "JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc "
                + "  ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA "
                + "  AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME "
                + "WHERE UPPER(tc.TABLE_NAME) = ? AND tc.CONSTRAINT_TYPE = 'CHECK' "
                + "ORDER BY tc.CONSTRAINT_NAME")) {
            ps.setString(1, t);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1).toUpperCase(Locale.ROOT);
                    if (name.startsWith("CK_")) {
                        descriptors.add("CK:" + name + ":" + normalizeCheckClause(rs.getString(2)));
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
        return def.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeCheckClause(String clause) {
        if (clause == null) {
            return "NULL";
        }
        String s = clause.trim().toUpperCase(Locale.ROOT);
        // Strip outer parentheses some databases add around the expression.
        while (s.startsWith("(") && s.endsWith(")")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private static final AtomicInteger DB_COUNTER = new AtomicInteger();

    private static void applyStatements(Connection conn, List<String> statements) throws SQLException {
        for (String sql : statements) {
            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            }
        }
    }

    private Connection freshH2() throws SQLException {
        // No DB_CLOSE_DELAY: each test holds the connection throughout, so the in-memory
        // database is released as soon as the connection closes at the end of the try block.
        String url = "jdbc:h2:mem:webhook_migration_" + DB_COUNTER.incrementAndGet() + ";MODE=LEGACY";
        return DriverManager.getConnection(url, "sa", "");
    }

    private void executeDdlResource(Connection conn, String resourcePath) throws Exception {
        String fullPath = "io/apicurio/registry/storage/impl/sql/" + resourcePath;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(fullPath)) {
            if (in == null) {
                throw new IllegalStateException("DDL resource not found on classpath: " + fullPath);
            }
            String ddl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String stmt : ddl.split(";")) {
                // Strip line comments then check if any SQL remains.
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

    private void assertTableExists(Connection conn, String tableName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName.toUpperCase(Locale.ROOT), new String[]{"TABLE"})) {
            assertTrue(rs.next(), "Table must exist: " + tableName);
        }
    }

    private void assertColumnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT))) {
            assertTrue(rs.next(), "Column must exist: " + tableName + "." + columnName);
        }
    }

    private void assertDbVersion(Connection conn, int expectedVersion) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT propValue FROM apicurio WHERE propName = 'db_version'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "db_version row must exist");
                assertEquals(String.valueOf(expectedVersion), rs.getString(1),
                        "db_version must be " + expectedVersion);
            }
        }
    }

    private void insertSubscription(Connection conn, String subscriptionId) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_subscriptions "
                + "(subscriptionId, ownerId, endpointUrl, eventTypes, enabled, revision, createdOn, modifiedOn) "
                + "VALUES (?, 'test-owner', 'https://endpoint.example.com', '[]', TRUE, 1, ?, ?)")) {
            ps.setString(1, subscriptionId);
            ps.setLong(2, now);
            ps.setLong(3, now);
            ps.executeUpdate();
        }
    }

    private void insertEvent(Connection conn, String eventRowId) throws SQLException {
        String hash = WebhookEventIdentity.computeHash("https://example.com", eventRowId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webhook_events (eventRowId, source, eventId, identityHash, eventType, payload, createdOn) "
                + "VALUES (?, 'https://example.com', ?, ?, 'com.example.test', X'41', ?)")) {
            ps.setString(1, eventRowId);
            ps.setString(2, eventRowId);
            ps.setString(3, hash);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }
}
