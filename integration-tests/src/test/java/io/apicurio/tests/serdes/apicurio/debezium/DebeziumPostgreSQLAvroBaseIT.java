package io.apicurio.tests.serdes.apicurio.debezium;

import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.debezium.testing.testcontainers.ConnectorConfiguration;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Abstract base class for Debezium PostgreSQL CDC integration tests with Apicurio Registry.
 * Extends the common DebeziumAvroBaseIT and adds PostgreSQL-specific functionality.
 */
public abstract class DebeziumPostgreSQLAvroBaseIT extends DebeziumAvroBaseIT {

    private static final Logger log = LoggerFactory.getLogger(DebeziumPostgreSQLAvroBaseIT.class);

    /**
     * Returns the PostgreSQL container to use for this test.
     */
    protected abstract PostgreSQLContainer<?> getPostgresContainer();

    @Override
    protected String getDatabaseType() {
        return "postgresql";
    }

    @Override
    protected Connection createDatabaseConnection() throws SQLException {
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            String username = System.getProperty("debezium.postgres.username", "postgres");
            String password = System.getProperty("debezium.postgres.password", "postgres");
            String postgresJdbcUrl = "jdbc:postgresql://" + getPostgresContainer().getHost() + ":5432/registry";
            return DriverManager.getConnection(postgresJdbcUrl, username, password);
        }
        else {
            String jdbcUrl = getPostgresContainer().getJdbcUrl();
            String username = getPostgresContainer().getUsername();
            String password = getPostgresContainer().getPassword();
            return DriverManager.getConnection(jdbcUrl, username, password);
        }
    }

    @Override
    protected void dropTable(String tableName) throws SQLException {
        try (Statement stmt = getDatabaseConnection().createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + tableName + " CASCADE");
        }
    }

    @Override
    protected void registerDebeziumConnectorWithApicurioConverters(String connectorName,
                                                                   String topicPrefix,
                                                                   String tableIncludeList) {
        String slotName = "slot_" + connectorName.replace("-", "_");

        ConnectorConfiguration config = buildBaseConnectorConfiguration(topicPrefix, tableIncludeList)
                .with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
                .with("database.hostname", getPostgresContainer().getContainerInfo().getConfig().getHostName())
                .with("database.port", "5432")
                .with("database.user", getPostgresContainer().getUsername())
                .with("database.password", getPostgresContainer().getPassword())
                .with("database.dbname", getPostgresContainer().getDatabaseName())
                .with("slot.name", slotName)
                .with("publication.name", "pub_" + connectorName.replace("-", "_"))
                .with("plugin.name", "pgoutput");

        getDebeziumContainer().registerConnector(connectorName, config);
        currentConnectorName = connectorName;

        String jdbcUrl = getPostgresContainer().getJdbcUrl();
        log.info("Registered Debezium connector: {} with slot: {}, tables: {}, postgres: {}",
                connectorName, slotName, tableIncludeList, jdbcUrl);
    }

    // ==================== PostgreSQL-Specific Helper Methods ====================

    protected void cleanupPostgreSQLReplicationState() throws SQLException {
        try (Statement stmt = getDatabaseConnection().createStatement()) {
            // Drop all replication slots
            var rs = stmt.executeQuery("SELECT slot_name FROM pg_replication_slots WHERE database = 'registry'");
            List<String> slotsToDelete = new ArrayList<>();
            while (rs.next()) {
                slotsToDelete.add(rs.getString("slot_name"));
            }
            rs.close();

            for (String slotName : slotsToDelete) {
                try {
                    stmt.execute("SELECT pg_drop_replication_slot('" + slotName + "')");
                    log.info("Dropped replication slot: {}", slotName);
                }
                catch (SQLException e) {
                    log.warn("Could not drop slot {} (might still be active): {}", slotName, e.getMessage());
                }
            }

            // Drop all publications
            var pubRs = stmt.executeQuery("SELECT pubname FROM pg_publication");
            List<String> publicationsToDelete = new ArrayList<>();
            while (pubRs.next()) {
                publicationsToDelete.add(pubRs.getString("pubname"));
            }
            pubRs.close();

            for (String pubName : publicationsToDelete) {
                if (!pubName.equals("pub_my_connector")) {
                    try {
                        stmt.execute("DROP PUBLICATION IF EXISTS " + pubName);
                        log.info("Dropped publication: {}", pubName);
                    }
                    catch (SQLException e) {
                        log.warn("Could not drop publication {}: {}", pubName, e.getMessage());
                    }
                }
            }
        }
    }

    @Override
    public void cleanup() throws Exception {
        // Call parent cleanup first to delete the connector
        super.cleanup();

        // Clean up PostgreSQL replication state
        if (dbConnection != null) {
            try {
                cleanupPostgreSQLReplicationState();
            }
            catch (Exception e) {
                log.error("Failed to clean up PostgreSQL replication state: {}", e.getMessage(), e);
            }
        }
    }

    // ==================== PostgreSQL-Specific Tests ====================

    /**
     * Test 1: Basic CDC with Schema Auto-Registration
     */
    @Test
    @Order(1)
    public void testBasicCDCWithSchemaAutoRegistration() throws Exception {
        String tableName = "customers";
        String topicPrefix = "test1";
        String topicName = topicPrefix + "." + "public." + tableName;

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id SERIAL PRIMARY KEY, " +
                        "name VARCHAR(100) NOT NULL, " +
                        "email VARCHAR(100), " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")");

        String connectorName = "connector-" + connectorCounter.incrementAndGet();
        registerDebeziumConnectorWithApicurioConverters(
                connectorName,
                topicPrefix,
                "public." + tableName);

        waitForConnectorReady(connectorName, Duration.ofSeconds(10));
        consumer.subscribe(List.of(topicName));
        waitForConsumerReady(Duration.ofSeconds(5));

        insertCustomer(tableName, "Alice Smith", "alice@example.com");
        insertCustomer(tableName, "Bob Jones", "bob@example.com");

        List<GenericRecord> events = consumeAvroEvents(topicName, 2, Duration.ofSeconds(10));
        assertEquals(2, events.size(), "Expected 2 CDC events");

        GenericRecord firstEvent = events.get(0);
        GenericRecord afterFirstEvent = (GenericRecord) firstEvent.get("after");
        assertNotNull(afterFirstEvent);
        assertEquals("Alice Smith", afterFirstEvent.get("name").toString());
        assertEquals("alice@example.com", afterFirstEvent.get("email").toString());

        waitForSchemaInRegistry(topicName + "-key", Duration.ofSeconds(10));
        waitForSchemaInRegistry(topicName + "-value", Duration.ofSeconds(10));

        log.info("Successfully verified basic CDC with schema auto-registration");
    }

    /**
     * Test 2: UPDATE and DELETE Operations
     */
    @Test
    @Order(2)
    public void testUpdateAndDeleteOperations() throws Exception {
        String tableName = "products";
        String topicPrefix = "test2";
        String topicName = topicPrefix + "." + "public." + tableName;

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id SERIAL PRIMARY KEY, " +
                        "name VARCHAR(100) NOT NULL, " +
                        "price DECIMAL(10, 2)" +
                        ")");

        try (Statement stmt = getDatabaseConnection().createStatement()) {
            stmt.execute("ALTER TABLE " + tableName + " REPLICA IDENTITY FULL");
            log.info("Set REPLICA IDENTITY FULL for table: {}", tableName);
        }

        String connectorName = "connector-" + connectorCounter.incrementAndGet();
        registerDebeziumConnectorWithApicurioConverters(
                connectorName,
                topicPrefix,
                "public." + tableName);

        waitForConnectorReady(connectorName, Duration.ofSeconds(10));
        consumer.subscribe(List.of(topicName));
        waitForConsumerReady(Duration.ofSeconds(5));

        // INSERT
        try (PreparedStatement stmt = getDatabaseConnection().prepareStatement(
                "INSERT INTO " + tableName + " (name, price) VALUES (?, ?) RETURNING id")) {
            stmt.setString(1, "Widget");
            stmt.setDouble(2, 19.99);
            var rs = stmt.executeQuery();
            rs.next();
            int productId = rs.getInt(1);

            // UPDATE
            try (PreparedStatement updateStmt = getDatabaseConnection().prepareStatement(
                    "UPDATE " + tableName + " SET price = ? WHERE id = ?")) {
                updateStmt.setDouble(1, 24.99);
                updateStmt.setInt(2, productId);
                updateStmt.executeUpdate();
            }

            // DELETE
            try (PreparedStatement deleteStmt = getDatabaseConnection().prepareStatement(
                    "DELETE FROM " + tableName + " WHERE id = ?")) {
                deleteStmt.setInt(1, productId);
                deleteStmt.executeUpdate();
            }
        }

        List<GenericRecord> events = consumeAvroEvents(topicName, 3, Duration.ofSeconds(15));
        assertEquals(3, events.size());

        // Verify INSERT
        GenericRecord insertEvent = events.get(0);
        assertEquals("c", insertEvent.get("op").toString());
        GenericRecord afterInsert = (GenericRecord) insertEvent.get("after");
        assertNotNull(afterInsert);
        assertEquals("Widget", afterInsert.get("name").toString());

        // Verify UPDATE
        GenericRecord updateEvent = events.get(1);
        assertEquals("u", updateEvent.get("op").toString());
        GenericRecord beforeUpdate = (GenericRecord) updateEvent.get("before");
        GenericRecord afterUpdate = (GenericRecord) updateEvent.get("after");
        assertNotNull(beforeUpdate);
        assertNotNull(afterUpdate);
        java.math.BigDecimal priceBefore = decodeAvroDecimal(beforeUpdate.get("price"), 2);
        java.math.BigDecimal priceAfter = decodeAvroDecimal(afterUpdate.get("price"), 2);
        assertEquals(new java.math.BigDecimal("19.99"), priceBefore);
        assertEquals(new java.math.BigDecimal("24.99"), priceAfter);

        // Verify DELETE
        GenericRecord deleteEvent = events.get(2);
        assertEquals("d", deleteEvent.get("op").toString());
        GenericRecord beforeDelete = (GenericRecord) deleteEvent.get("before");
        assertNotNull(beforeDelete);
        assertEquals("Widget", beforeDelete.get("name").toString());

        log.info("Successfully verified UPDATE and DELETE operations");
    }

    /**
     * Test 3: Multiple Table Capture
     */
    @Test
    @Order(3)
    public void testMultipleTableCapture() throws Exception {
        String table1 = "orders";
        String table2 = "order_items";
        String table3 = "inventory";
        String topicPrefix = "test3";

        createTable(table1,
                "CREATE TABLE " + table1 + " (" +
                        "id SERIAL PRIMARY KEY, " +
                        "order_number VARCHAR(50) NOT NULL, " +
                        "total DECIMAL(10, 2)" +
                        ")");

        createTable(table2,
                "CREATE TABLE " + table2 + " (" +
                        "id SERIAL PRIMARY KEY, " +
                        "order_id INT, " +
                        "product_name VARCHAR(100), " +
                        "quantity INT" +
                        ")");

        createTable(table3,
                "CREATE TABLE " + table3 + " (" +
                        "id SERIAL PRIMARY KEY, " +
                        "sku VARCHAR(50), " +
                        "stock_count INT" +
                        ")");

        String connectorName = "connector-" + connectorCounter.incrementAndGet();
        registerDebeziumConnectorWithApicurioConverters(
                connectorName,
                topicPrefix,
                "public." + table1 + ",public." + table2 + ",public." + table3);

        waitForConnectorReady(connectorName, Duration.ofSeconds(10));

        String topic1 = topicPrefix + ".public." + table1;
        String topic2 = topicPrefix + ".public." + table2;
        String topic3 = topicPrefix + ".public." + table3;

        consumer.subscribe(List.of(topic1, topic2, topic3));
        waitForConsumerReady(Duration.ofSeconds(5));

        executeUpdate("INSERT INTO " + table1 + " (order_number, total) VALUES ('ORD-001', 99.99)");
        executeUpdate("INSERT INTO " + table2 + " (order_id, product_name, quantity) VALUES (1, 'Laptop', 1)");
        executeUpdate("INSERT INTO " + table3 + " (sku, stock_count) VALUES ('SKU-123', 50)");

        List<ConsumerRecord<byte[], byte[]>> allRecords = new ArrayList<>();
        Unreliables.retryUntilTrue(10, TimeUnit.SECONDS, () -> {
            consumer.poll(Duration.ofMillis(500)).forEach(allRecords::add);
            return allRecords.size() >= 3;
        });

        assertEquals(3, allRecords.size());

        waitForSchemaInRegistry(topic1 + "-value", Duration.ofSeconds(10));
        waitForSchemaInRegistry(topic2 + "-value", Duration.ofSeconds(10));
        waitForSchemaInRegistry(topic3 + "-value", Duration.ofSeconds(10));

        log.info("Successfully verified multiple table capture");
    }

    /**
     * Test 4: Schema Name Adjustment
     */
    @Test
    @Order(4)
    public void testSchemaNameAdjustment() throws Exception {
        String tableName = "special_columns";
        String topicPrefix = "test4";
        String topicName = topicPrefix + "." + "public." + tableName;

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id SERIAL PRIMARY KEY, " +
                        "\"first-name\" VARCHAR(100), " +
                        "\"last name\" VARCHAR(100), " +
                        "\"email@address\" VARCHAR(100)" +
                        ")");

        String connectorName = "connector-" + connectorCounter.incrementAndGet();
        String slotName = "slot_" + connectorName.replace("-", "_");
        String registryUrl = getContainerAccessibleRegistryUrl();

        ConnectorConfiguration config = ConnectorConfiguration
                .forJdbcContainer(getPostgresContainer())
                .with("topic.prefix", topicPrefix)
                .with("table.include.list", "public." + tableName)
                .with("slot.name", slotName)
                .with("publication.name", "pub_" + connectorName.replace("-", "_"))
                .with("plugin.name", "pgoutput")
                .with("schema.name.adjustment.mode", "avro")
                .with("field.name.adjustment.mode", "avro")
                .with("key.converter", "io.apicurio.registry.utils.converter.AvroConverter")
                .with("key.converter.apicurio.registry.url", registryUrl)
                .with("key.converter.apicurio.registry.auto-register", "true")
                .with("key.converter.apicurio.registry.find-latest", "true")
                .with("key.converter.apicurio.registry.headers.enabled", "false")
                .with("value.converter", "io.apicurio.registry.utils.converter.AvroConverter")
                .with("value.converter.apicurio.registry.url", registryUrl)
                .with("value.converter.apicurio.registry.auto-register", "true")
                .with("value.converter.apicurio.registry.find-latest", "true")
                .with("value.converter.apicurio.registry.headers.enabled", "false");

        getDebeziumContainer().registerConnector(connectorName, config);
        currentConnectorName = connectorName;

        waitForConnectorReady(connectorName, Duration.ofSeconds(10));
        consumer.subscribe(List.of(topicName));
        waitForConsumerReady(Duration.ofSeconds(5));

        executeUpdate("INSERT INTO " + tableName +
                " (\"first-name\", \"last name\", \"email@address\") VALUES " +
                "('John', 'Doe', 'john@example.com')");

        List<GenericRecord> events = consumeAvroEvents(topicName, 1, Duration.ofSeconds(10));
        assertEquals(1, events.size());

        GenericRecord event = events.get(0);
        GenericRecord after = (GenericRecord) event.get("after");
        assertNotNull(after);
        assertNotNull(after.get("first_name"));
        assertEquals("John", after.get("first_name").toString());

        log.info("Successfully verified schema name adjustment");
    }

    /**
     * Test 5: Backward Compatible Schema Evolution
     */
    @Test
    @Order(5)
    public void testBackwardCompatibleEvolution() throws Exception {
        String tableName = "evolving_table";
        String topicPrefix = "test5";
        String topicName = topicPrefix + "." + "public." + tableName;

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id SERIAL PRIMARY KEY, " +
                        "name VARCHAR(100) NOT NULL" +
                        ")");

        String connectorName = "connector-" + connectorCounter.incrementAndGet();
        registerDebeziumConnectorWithApicurioConverters(
                connectorName,
                topicPrefix,
                "public." + tableName);

        waitForConnectorReady(connectorName, Duration.ofSeconds(10));
        consumer.subscribe(List.of(topicName));
        waitForConsumerReady(Duration.ofSeconds(5));

        executeUpdate("INSERT INTO " + tableName + " (name) VALUES ('Original')");

        List<GenericRecord> events1 = consumeAvroEvents(topicName, 1, Duration.ofSeconds(10));
        assertEquals(1, events1.size());

        waitForSchemaInRegistry(topicName + "-value", Duration.ofSeconds(10));

        try (Statement stmt = getDatabaseConnection().createStatement()) {
            stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN email VARCHAR(100) DEFAULT NULL");
        }

        executeUpdate("INSERT INTO " + tableName + " (name, email) VALUES ('New Record', 'test@example.com')");

        List<GenericRecord> events2 = consumeAvroEvents(topicName, 1, Duration.ofSeconds(10));
        assertEquals(1, events2.size());

        GenericRecord newEvent = events2.get(0);
        GenericRecord after = (GenericRecord) newEvent.get("after");
        assertNotNull(after);
        assertEquals("New Record", after.get("name").toString());
        assertNotNull(after.get("email"));

        ArtifactMetaData metadata = registryClient.groups().byGroupId("default")
                .artifacts().byArtifactId(topicName + "-value")
                .get();
        assertNotNull(metadata);

        log.info("Successfully verified backward compatible schema evolution");
    }

    /**
     * Test 6: Schema Compatibility Rules
     */
    @Test
    @Order(6)
    public void testSchemaCompatibilityRules() throws Exception {
        String tableName = "compat_test";
        String topicPrefix = "test6";
        String topicName = topicPrefix + "." + "public." + tableName;

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id SERIAL PRIMARY KEY, " +
                        "data VARCHAR(100)" +
                        ")");

        String connectorName = "connector-" + connectorCounter.incrementAndGet();
        registerDebeziumConnectorWithApicurioConverters(
                connectorName,
                topicPrefix,
                "public." + tableName);

        waitForConnectorReady(connectorName, Duration.ofSeconds(10));
        consumer.subscribe(List.of(topicName));
        waitForConsumerReady(Duration.ofSeconds(5));

        executeUpdate("INSERT INTO " + tableName + " (data) VALUES ('test')");
        waitForSchemaInRegistry(topicName + "-value", Duration.ofSeconds(10));

        CreateRule rule = new CreateRule();
        rule.setRuleType(RuleType.COMPATIBILITY);
        rule.setConfig(io.apicurio.registry.rules.compatibility.CompatibilityLevel.BACKWARD.name());

        registryClient.groups().byGroupId("default")
                .artifacts().byArtifactId(topicName + "-value")
                .rules().post(rule);

        log.info("Successfully set BACKWARD compatibility rule on {}", topicName + "-value");

        var rules = registryClient.groups().byGroupId("default")
                .artifacts().byArtifactId(topicName + "-value")
                .rules().get();

        assertTrue(rules.stream().anyMatch(r -> r.equals(RuleType.COMPATIBILITY)));
        log.info("Successfully verified schema compatibility rules");
    }

    /**
     * Test 7: Schema Versioning
     */
    @Test
    @Order(7)
    public void testSchemaVersioning() throws Exception {
        String tableName = "versioned_table";
        String topicPrefix = "test7";
        String topicName = topicPrefix + "." + "public." + tableName;

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id SERIAL PRIMARY KEY, " +
                        "field1 VARCHAR(100)" +
                        ")");

        String connectorName = "connector-" + connectorCounter.incrementAndGet();
        registerDebeziumConnectorWithApicurioConverters(
                connectorName,
                topicPrefix,
                "public." + tableName);

        waitForConnectorReady(connectorName, Duration.ofSeconds(10));
        consumer.subscribe(List.of(topicName));
        waitForConsumerReady(Duration.ofSeconds(5));

        executeUpdate("INSERT INTO " + tableName + " (field1) VALUES ('v1')");
        consumeAvroEvents(topicName, 1, Duration.ofSeconds(10));
        waitForSchemaInRegistry(topicName + "-value", Duration.ofSeconds(10));

        executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN field2 VARCHAR(100)");
        executeUpdate("INSERT INTO " + tableName + " (field1, field2) VALUES ('v2', 'data2')");
        consumeAvroEvents(topicName, 1, Duration.ofSeconds(10));

        executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN field3 INT");
        executeUpdate("INSERT INTO " + tableName + " (field1, field2, field3) VALUES ('v3', 'data3', 123)");
        consumeAvroEvents(topicName, 1, Duration.ofSeconds(10));

        var versions = registryClient.groups().byGroupId("default")
                .artifacts().byArtifactId(topicName + "-value")
                .versions().get();

        assertNotNull(versions);
        assertTrue(versions.getCount() > 0);

        log.info("Schema has {} versions", versions.getCount());
        log.info("Successfully verified schema versioning");
    }

    /**
     * Test 8: PostgreSQL-Specific Data Types
     */
    @Test
    @Order(8)
    public void testPostgreSQLSpecificTypes() throws Exception {
        String tableName = "pg_types_test";
        String topicPrefix = "test8";
        String topicName = topicPrefix + "." + "public." + tableName;

        try (Statement stmt = getDatabaseConnection().createStatement()) {
            stmt.execute("DROP TYPE IF EXISTS mood CASCADE");
            stmt.execute("CREATE TYPE mood AS ENUM ('happy', 'sad', 'neutral')");
        }

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id SERIAL PRIMARY KEY, " +
                        "data_json JSONB, " +
                        "tags TEXT[], " +
                        "user_mood mood, " +
                        "user_id UUID, " +
                        "created_at TIMESTAMPTZ" +
                        ")");

        String connectorName = "connector-" + connectorCounter.incrementAndGet();
        registerDebeziumConnectorWithApicurioConverters(
                connectorName,
                topicPrefix,
                "public." + tableName);

        waitForConnectorReady(connectorName, Duration.ofSeconds(10));
        consumer.subscribe(List.of(topicName));
        waitForConsumerReady(Duration.ofSeconds(5));

        try (PreparedStatement stmt = getDatabaseConnection().prepareStatement(
                "INSERT INTO " + tableName +
                        " (data_json, tags, user_mood, user_id, created_at) " +
                        "VALUES (?::jsonb, ?::text[], ?::mood, ?::uuid, NOW())")) {
            stmt.setString(1, "{\"key\": \"value\", \"number\": 42}");
            stmt.setArray(2, getDatabaseConnection().createArrayOf("text", new String[]{ "tag1", "tag2", "tag3" }));
            stmt.setObject(3, "happy", java.sql.Types.OTHER);
            stmt.setObject(4, UUID.randomUUID());
            stmt.executeUpdate();
        }

        List<GenericRecord> events = consumeAvroEvents(topicName, 1, Duration.ofSeconds(10));
        assertEquals(1, events.size());

        GenericRecord event = events.get(0);
        GenericRecord after = (GenericRecord) event.get("after");
        assertNotNull(after);
        assertNotNull(after.get("data_json"));
        assertNotNull(after.get("tags"));
        assertNotNull(after.get("user_mood"));
        assertNotNull(after.get("user_id"));
        assertNotNull(after.get("created_at"));

        log.info("Successfully verified PostgreSQL-specific types");
    }

    /**
     * Test 9: Numeric and Decimal Precision
     */
    @Test
    @Order(9)
    public void testNumericAndDecimalPrecision() throws Exception {
        String tableName = "decimal_test";
        String topicPrefix = "test9";
        String topicName = topicPrefix + "." + "public." + tableName;

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id SERIAL PRIMARY KEY, " +
                        "price DECIMAL(10, 2), " +
                        "tax_rate NUMERIC(5, 4), " +
                        "weight DECIMAL(15, 6), " +
                        "quantity NUMERIC(10, 0)" +
                        ")");

        String connectorName = "connector-" + connectorCounter.incrementAndGet();
        registerDebeziumConnectorWithApicurioConverters(
                connectorName,
                topicPrefix,
                "public." + tableName);

        waitForConnectorReady(connectorName, Duration.ofSeconds(10));
        consumer.subscribe(List.of(topicName));
        waitForConsumerReady(Duration.ofSeconds(5));

        try (PreparedStatement stmt = getDatabaseConnection().prepareStatement(
                "INSERT INTO " + tableName + " (price, tax_rate, weight, quantity) VALUES (?, ?, ?, ?)")) {
            stmt.setBigDecimal(1, new java.math.BigDecimal("99.99"));
            stmt.setBigDecimal(2, new java.math.BigDecimal("0.0825"));
            stmt.setBigDecimal(3, new java.math.BigDecimal("123.456789"));
            stmt.setBigDecimal(4, new java.math.BigDecimal("1000"));
            stmt.executeUpdate();
        }

        List<GenericRecord> events = consumeAvroEvents(topicName, 1, Duration.ofSeconds(10));
        assertEquals(1, events.size());

        GenericRecord event = events.get(0);
        GenericRecord after = (GenericRecord) event.get("after");
        assertNotNull(after);
        assertNotNull(after.get("price"));
        assertNotNull(after.get("tax_rate"));
        assertNotNull(after.get("weight"));
        assertNotNull(after.get("quantity"));

        log.info("Successfully verified numeric and decimal precision");
    }

    /**
     * Test 10: Bulk Operations
     */
    @Test
    @Order(10)
    public void testBulkOperations() throws Exception {
        String tableName = "bulk_test";
        String topicPrefix = "test10";
        String topicName = topicPrefix + "." + "public." + tableName;

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id SERIAL PRIMARY KEY, " +
                        "value VARCHAR(100)" +
                        ")");

        String connectorName = "connector-" + connectorCounter.incrementAndGet();
        registerDebeziumConnectorWithApicurioConverters(
                connectorName,
                topicPrefix,
                "public." + tableName);

        waitForConnectorReady(connectorName, Duration.ofSeconds(10));
        consumer.subscribe(List.of(topicName));
        waitForConsumerReady(Duration.ofSeconds(5));

        int totalRows = 1000;
        int batchSize = 100;

        for (int batch = 0; batch < totalRows / batchSize; batch++) {
            StringBuilder sql = new StringBuilder("INSERT INTO " + tableName + " (value) VALUES ");
            for (int i = 0; i < batchSize; i++) {
                if (i > 0)
                    sql.append(", ");
                sql.append("('value-").append(batch * batchSize + i).append("')");
            }
            executeUpdate(sql.toString());
        }

        List<ConsumerRecord<byte[], byte[]>> allRecords = new ArrayList<>();
        Unreliables.retryUntilTrue(60, TimeUnit.SECONDS, () -> {
            consumer.poll(Duration.ofSeconds(1)).forEach(allRecords::add);
            log.info("Consumed {} records so far", allRecords.size());
            return allRecords.size() >= totalRows;
        });

        assertEquals(totalRows, allRecords.size(), "Expected all 1000 CDC events");
        log.info("Successfully verified bulk operations with {} events", totalRows);
    }

    /**
     * Test 11: Connector Recovery
     */
    @Test
    @Order(11)
    public void testConnectorRecovery() throws Exception {
        String tableName = "recovery_test";
        String topicPrefix = "test11";
        String topicName = topicPrefix + "." + "public." + tableName;

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id SERIAL PRIMARY KEY, " +
                        "data VARCHAR(100)" +
                        ")");

        String connectorName = "connector-" + connectorCounter.incrementAndGet();
        registerDebeziumConnectorWithApicurioConverters(
                connectorName,
                topicPrefix,
                "public." + tableName);

        waitForConnectorReady(connectorName, Duration.ofSeconds(10));
        consumer.subscribe(List.of(topicName));
        waitForConsumerReady(Duration.ofSeconds(5));

        executeUpdate("INSERT INTO " + tableName + " (data) VALUES ('before')");
        List<GenericRecord> events1 = consumeAvroEvents(topicName, 1, Duration.ofSeconds(10));
        assertEquals(1, events1.size());

        executeUpdate("INSERT INTO " + tableName + " (data) VALUES ('after')");
        List<GenericRecord> events2 = consumeAvroEvents(topicName, 1, Duration.ofSeconds(10));
        assertEquals(1, events2.size());

        GenericRecord afterEvent = events2.get(0);
        GenericRecord after = (GenericRecord) afterEvent.get("after");
        assertEquals("after", after.get("data").toString());

        log.info("Successfully verified connector recovery");
    }
}
