package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.storage.impl.sql.CommonSqlStatements;
import io.apicurio.registry.storage.impl.sql.H2SqlStatements;
import io.apicurio.registry.storage.impl.sql.MySQLSqlStatements;
import io.apicurio.registry.storage.impl.sql.PostgreSQLSqlStatements;
import io.apicurio.registry.storage.impl.sql.SQLServerSqlStatements;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the 109 -> 110 upgrade scripts, which add the webhook subscription and delivery log tables. The
 * scripts are applied to a database that only contains the "apicurio" bookkeeping table at version 109, which
 * is all the 110 upgrade depends on.
 * <p>
 * This is a plain JDBC test (no Quarkus) so that the same assertions can be run against more than one
 * database kind. The fresh-install path (base DDL) is covered by {@link WebhookSchemaTest}.
 */
public class WebhookDdlUpgradeTest {

    private static final String INSERT_SUBSCRIPTION = "INSERT INTO webhook_subscriptions "
            + "(subscriptionId, name, endpointUrl, eventTypes, groupFilter, artifactIdFilter, enabled, "
            + "secret, createdBy, createdOn, modifiedOn) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_DELIVERY_LOG = "INSERT INTO webhook_delivery_logs "
            + "(deliveryId, subscriptionId, eventId, eventType, status, attemptCount, lastAttemptAt, "
            + "nextRetryAt, errorMessage, httpStatusCode, createdOn) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Test
    void testUpgradeOnH2() throws Exception {
        String url = "jdbc:h2:mem:webhook-upgrade-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertUpgradeCreatesUsableSchema(connection, "h2");
        }
    }

    @Test
    void testUpgradeOnPostgresql() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start();
                Connection connection = postgres.getPostgresDatabase().getConnection()) {
            assertUpgradeCreatesUsableSchema(connection, "postgresql");
        }
    }

    private void assertUpgradeCreatesUsableSchema(Connection connection, String dbKind) throws Exception {
        int fromVersion = 109;
        int toVersion = dbVersion();
        seedVersion109(connection);
        applyUpgrade(connection, dbKind, fromVersion, toVersion);

        assertEquals(String.valueOf(toVersion), currentDbVersion(connection),
                "The upgrade script must advance the recorded db_version");

        String subscriptionId = UUID.randomUUID().toString();
        insertSubscription(connection, subscriptionId);

        insertDeliveryLog(connection, UUID.randomUUID().toString(), subscriptionId, "event-1");

        // The unique (subscriptionId, eventId) constraint is what makes delivery deduplication possible.
        SQLException duplicate = assertThrows(SQLException.class, () -> insertDeliveryLog(connection,
                UUID.randomUUID().toString(), subscriptionId, "event-1"));
        assertTrue(duplicate.getMessage().toUpperCase(Locale.ROOT).contains("UQ_WEBHOOK_DELIVERY_LOGS_1"),
                "Expected UQ_webhook_delivery_logs_1 to be violated, but got: " + duplicate.getMessage());
        rollbackIfNeeded(connection);

        // The same event fanned out to a different subscription is a separate row.
        String otherSubscriptionId = UUID.randomUUID().toString();
        insertSubscription(connection, otherSubscriptionId);
        insertDeliveryLog(connection, UUID.randomUUID().toString(), otherSubscriptionId, "event-1");

        // Orphan delivery logs are rejected by the foreign key.
        SQLException orphan = assertThrows(SQLException.class, () -> insertDeliveryLog(connection,
                UUID.randomUUID().toString(), "no-such-subscription", "event-2"));
        assertTrue(orphan.getMessage().toUpperCase(Locale.ROOT).contains("FK_WEBHOOK_DELIVERY_LOGS_1"),
                "Expected FK_webhook_delivery_logs_1 to be violated, but got: " + orphan.getMessage());
        rollbackIfNeeded(connection);

        assertEquals(1, countDeliveryLogs(connection, subscriptionId));

        // Deleting a subscription cascades to its delivery logs.
        try (PreparedStatement ps = connection
                .prepareStatement("DELETE FROM webhook_subscriptions WHERE subscriptionId = ?")) {
            ps.setString(1, subscriptionId);
            assertEquals(1, ps.executeUpdate());
        }
        assertEquals(0, countDeliveryLogs(connection, subscriptionId));
        assertEquals(1, countDeliveryLogs(connection, otherSubscriptionId));
    }

    private void seedVersion109(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE apicurio (propName VARCHAR(255) NOT NULL, "
                    + "propValue VARCHAR(255), PRIMARY KEY (propName))");
            statement.execute("INSERT INTO apicurio (propName, propValue) VALUES ('db_version', 109)");
        }
    }

    private void applyUpgrade(Connection connection, String dbKind, int fromVersion, int toVersion)
            throws SQLException {
        CommonSqlStatements sqlStatements = sqlStatementsFor(dbKind);
        List<String> statements = sqlStatements.databaseUpgrade(fromVersion, toVersion);
        assertFalse(statements.isEmpty(),
                "No upgrade statements produced for " + dbKind + " from version " + fromVersion + " to " + toVersion);
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private CommonSqlStatements sqlStatementsFor(String dbKind) {
        switch (dbKind) {
            case "h2":
                return new H2SqlStatements();
            case "postgresql":
                return new PostgreSQLSqlStatements();
            case "mysql":
                return new MySQLSqlStatements();
            case "mssql":
                return new SQLServerSqlStatements();
            default:
                throw new IllegalArgumentException("Unsupported db kind: " + dbKind);
        }
    }

    private int dbVersion() throws IOException {
        try (InputStream input = CommonSqlStatements.class.getResourceAsStream("db-version")) {
            assertNotNull(input, "db-version resource not found");
            return Integer.parseInt(new String(input.readAllBytes(), StandardCharsets.UTF_8).trim());
        }
    }

    private String currentDbVersion(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection
                .prepareStatement("SELECT propValue FROM apicurio WHERE propName = 'db_version'");
                ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    private void insertSubscription(Connection connection, String subscriptionId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_SUBSCRIPTION)) {
            ps.setString(1, subscriptionId);
            ps.setString(2, "subscription " + subscriptionId);
            ps.setString(3, "https://example.com/hook");
            ps.setString(4, "[\"ARTIFACT_CREATED\"]");
            ps.setNull(5, Types.VARCHAR);
            ps.setNull(6, Types.VARCHAR);
            ps.setBoolean(7, true);
            ps.setNull(8, Types.VARCHAR);
            ps.setString(9, "alice");
            ps.setTimestamp(10, new Timestamp(System.currentTimeMillis()));
            ps.setTimestamp(11, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    private void insertDeliveryLog(Connection connection, String deliveryId, String subscriptionId,
            String eventId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_DELIVERY_LOG)) {
            ps.setString(1, deliveryId);
            ps.setString(2, subscriptionId);
            ps.setString(3, eventId);
            ps.setString(4, "ARTIFACT_CREATED");
            ps.setString(5, "PENDING");
            ps.setInt(6, 0);
            ps.setNull(7, Types.TIMESTAMP);
            ps.setNull(8, Types.TIMESTAMP);
            ps.setNull(9, Types.VARCHAR);
            ps.setNull(10, Types.INTEGER);
            ps.setTimestamp(11, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    private int countDeliveryLogs(Connection connection, String subscriptionId) throws SQLException {
        try (PreparedStatement ps = connection
                .prepareStatement("SELECT COUNT(*) FROM webhook_delivery_logs WHERE subscriptionId = ?")) {
            ps.setString(1, subscriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private void rollbackIfNeeded(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.rollback();
        }
    }
}
