

package io.apicurio.registry.storage.impl.sql;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
            executeDdlResource(conn, "h2.ddl");
            assertTableExists(conn, "webhook_subscriptions");
            assertTableExists(conn, "webhook_events");
            assertTableExists(conn, "webhook_delivery_logs");
        }
    }

    @Test
    void freshInstall_dbVersionIs112() throws Exception {
        try (Connection conn = freshH2()) {
            executeDdlResource(conn, "h2.ddl");
            assertDbVersion(conn, VERSION_AFTER);
        }
    }

    @Test
    void freshInstall_subscriptionsColumnsExist() throws Exception {
        try (Connection conn = freshH2()) {
            executeDdlResource(conn, "h2.ddl");
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
            executeDdlResource(conn, "h2.ddl");
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
            executeDdlResource(conn, "h2.ddl");
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
            executeDdlResource(conn, "upgrades/111/h2.upgrade.ddl");
            assertDbVersion(conn, VERSION_PEERS);
            executeDdlResource(conn, "upgrades/112/h2.upgrade.ddl");

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
            executeDdlResource(conn, "upgrades/111/h2.upgrade.ddl");
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
            executeDdlResource(conn, "upgrades/112/h2.upgrade.ddl");
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
            executeDdlResource(conn, "upgrades/111/h2.upgrade.ddl");
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
            executeDdlResource(conn, "upgrades/112/h2.upgrade.ddl");

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
            executeDdlResource(conn, "h2.ddl");

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
            executeDdlResource(conn, "h2.ddl");

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
    // Foreign-key enforcement
    // ------------------------------------------------------------

    @Test
    void fkWhdlogs1_rejectsUnknownSubscriptionId() throws Exception {
        try (Connection conn = freshH2()) {
            executeDdlResource(conn, "h2.ddl");

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
                assertTrue(ex.getSQLState().startsWith("23"),
                        "SQL state must be integrity-constraint class 23, was: " + ex.getSQLState());
            }
        }
    }

    @Test
    void fkWhdlogs2_rejectsUnknownEventRowId() throws Exception {
        try (Connection conn = freshH2()) {
            executeDdlResource(conn, "h2.ddl");

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
                assertTrue(ex.getSQLState().startsWith("23"),
                        "SQL state must be integrity-constraint class 23, was: " + ex.getSQLState());
            }
        }
    }

    // ------------------------------------------------------------
    // Check constraint
    // ------------------------------------------------------------

    @Test
    void checkConstraint_rejectsNegativeAttemptCount() throws Exception {
        try (Connection conn = freshH2()) {
            executeDdlResource(conn, "h2.ddl");

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
                assertTrue(ex.getSQLState().startsWith("23"),
                        "SQL state must be integrity-constraint class 23, was: " + ex.getSQLState());
            }
        }
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private static final AtomicInteger DB_COUNTER = new AtomicInteger();

    private Connection freshH2() throws SQLException {
        String url = "jdbc:h2:mem:webhook_migration_" + DB_COUNTER.incrementAndGet()
                + ";DB_CLOSE_DELAY=-1;MODE=LEGACY";
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
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
            assertTrue(rs.next(), "Table must exist: " + tableName);
        }
    }

    private void assertColumnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
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
