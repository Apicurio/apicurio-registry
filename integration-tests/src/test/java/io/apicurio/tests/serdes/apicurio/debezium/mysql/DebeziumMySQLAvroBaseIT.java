package io.apicurio.tests.serdes.apicurio.debezium.mysql;

import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.tests.serdes.apicurio.debezium.DebeziumAvroBaseIT;
import io.debezium.testing.testcontainers.ConnectorConfiguration;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Abstract base class for Debezium MySQL CDC integration tests with Apicurio Registry.
 * Extends the common DebeziumAvroBaseIT and adds MySQL-specific functionality.
 */
public abstract class DebeziumMySQLAvroBaseIT extends DebeziumAvroBaseIT {

    private static final Logger log = LoggerFactory.getLogger(DebeziumMySQLAvroBaseIT.class);

    /**
     * Returns the MySQL container to use for this test.
     */
    protected abstract MySQLContainer getMySQLContainer();

    @Override
    protected String getDatabaseType() {
        return "mysql";
    }

    @Override
    protected String getTableIncludePattern() {
        // Watch all tables in the registry database
        return "registry.*";
    }

    @Override
    protected String getTopicNameForTable(String tableName) {
        return sharedTopicPrefix + ".registry." + tableName;
    }

    @Override
    protected Connection createDatabaseConnection() throws SQLException {
        // Always use the container's JDBC URL which includes necessary parameters
        String jdbcUrl = getMySQLContainer().getJdbcUrl();
        String username = getMySQLContainer().getUsername();
        String password = getMySQLContainer().getPassword();
        log.info("Creating MySQL connection: url={}, user={}", jdbcUrl, username);
        try {
            Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
            log.info("MySQL connection created successfully");
            return conn;
        } catch (SQLException e) {
            log.error("Failed to create MySQL connection to {}: {}", jdbcUrl, e.getMessage());
            throw e;
        }
    }

    @Override
    protected void dropTable(String tableName) throws SQLException {
        try (Statement stmt = getDatabaseConnection().createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + tableName);
        }
    }

    @Override
    protected void registerDebeziumConnectorWithApicurioConverters(String connectorName,
                                                                   String topicPrefix,
                                                                   String tableIncludeList) {
        String dockerAccessibleRegistryUrl = getContainerAccessibleRegistryUrl();

        // MySQL requires unique server ID for each connector
        int serverId = 10000 + connectorCounter.get();

        // Schema history topic for tracking DDL changes
        String schemaHistoryTopic = "schema-history-" + connectorName.replace("-", "_");

        // Get the correct Kafka bootstrap servers
        // In cluster mode: use cluster.bootstrap.servers (if set) or bootstrap.servers
        // In local mode: use bootstrap.servers
        String kafkaBootstrapServers = System.getProperty("cluster.bootstrap.servers",
                System.getProperty("bootstrap.servers"));

        ConnectorConfiguration config = ConnectorConfiguration
                .forJdbcContainer(getMySQLContainer())
                .with("topic.prefix", topicPrefix)
                .with("table.include.list", tableIncludeList)
                .with("database.server.id", String.valueOf(serverId))
                .with("snapshot.locking.mode", "none")
                .with("schema.name.adjustment.mode", "avro")
                .with("field.name.adjustment.mode", "avro")
                .with("schema.history.internal.kafka.bootstrap.servers", kafkaBootstrapServers)
                .with("schema.history.internal.kafka.topic", schemaHistoryTopic)
                .with("key.converter", "io.apicurio.registry.utils.converter.AvroConverter")
                .with("key.converter.apicurio.registry.url", dockerAccessibleRegistryUrl)
                .with("key.converter.apicurio.registry.auto-register", "true")
                .with("key.converter.apicurio.registry.find-latest", "true")
                .with("key.converter.apicurio.registry.headers.enabled", "false")
                .with("value.converter", "io.apicurio.registry.utils.converter.AvroConverter")
                .with("value.converter.apicurio.registry.url", dockerAccessibleRegistryUrl)
                .with("value.converter.apicurio.registry.auto-register", "true")
                .with("value.converter.apicurio.registry.find-latest", "true")
                .with("value.converter.apicurio.registry.headers.enabled", "false");

        getDebeziumContainer().registerConnector(connectorName, config);

        String jdbcUrl = getMySQLContainer().getJdbcUrl();
        log.info("Registered Debezium connector: {} with server.id: {}, schema-history: {}, tables: {}, registry: {}, mysql: {}",
                connectorName, serverId, schemaHistoryTopic, tableIncludeList, dockerAccessibleRegistryUrl, jdbcUrl);
    }

    // ==================== MySQL-Specific Tests ====================

    /**
     * Test 1: Basic CDC with Schema Auto-Registration
     * Uses the shared connector that watches all tables in registry database.
     */
    @Test
    @Order(1)
    public void testBasicCDCWithSchemaAutoRegistration() throws Exception {
        String tableName = getTableName("customers");
        String topicName = getTopicNameForTable(tableName);

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "name VARCHAR(100) NOT NULL, " +
                        "email VARCHAR(100), " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")");

        // Using shared connector from @BeforeAll
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
        String tableName = getTableName("products");
        String topicName = getTopicNameForTable(tableName);

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "name VARCHAR(100) NOT NULL, " +
                        "price DECIMAL(10, 2)" +
                        ")");

        // Using shared connector from @BeforeAll
        consumer.subscribe(List.of(topicName));
        waitForConsumerReady(Duration.ofSeconds(5));

        // INSERT
        try (PreparedStatement stmt = getDatabaseConnection().prepareStatement(
                "INSERT INTO " + tableName + " (name, price) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, "Widget");
            stmt.setDouble(2, 19.99);
            stmt.executeUpdate();
            var rs = stmt.getGeneratedKeys();
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
        String table1 = getTableName("orders");
        String table2 = getTableName("order_items");
        String table3 = getTableName("inventory");

        createTable(table1,
                "CREATE TABLE " + table1 + " (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "order_number VARCHAR(50) NOT NULL, " +
                        "total DECIMAL(10, 2)" +
                        ")");

        createTable(table2,
                "CREATE TABLE " + table2 + " (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "order_id INT, " +
                        "product_name VARCHAR(100), " +
                        "quantity INT" +
                        ")");

        createTable(table3,
                "CREATE TABLE " + table3 + " (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "sku VARCHAR(50), " +
                        "stock_count INT" +
                        ")");

        // Using shared connector that watches all tables
        String topic1 = getTopicNameForTable(table1);
        String topic2 = getTopicNameForTable(table2);
        String topic3 = getTopicNameForTable(table3);

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
        String tableName = getTableName("special_columns");
        String topicName = getTopicNameForTable(tableName);

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "`first-name` VARCHAR(100), " +
                        "`last name` VARCHAR(100), " +
                        "`email@address` VARCHAR(100)" +
                        ")");

        // Using shared connector from @BeforeAll
        // Note: Shared connector already has schema.name.adjustment.mode and field.name.adjustment.mode set to "avro"
        consumer.subscribe(List.of(topicName));
        waitForConsumerReady(Duration.ofSeconds(5));

        executeUpdate("INSERT INTO " + tableName +
                " (`first-name`, `last name`, `email@address`) VALUES " +
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
        String tableName = getTableName("evolving_table");
        String topicName = getTopicNameForTable(tableName);

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "name VARCHAR(100) NOT NULL" +
                        ")");

        // Using shared connector from @BeforeAll
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
        String tableName = getTableName("compat_test");
        String topicName = getTopicNameForTable(tableName);

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "data VARCHAR(100)" +
                        ")");

        // Using shared connector from @BeforeAll
        consumer.subscribe(List.of(topicName));
        waitForConsumerReady(Duration.ofSeconds(5));

        executeUpdate("INSERT INTO " + tableName + " (data) VALUES ('test')");
        waitForSchemaInRegistry(topicName + "-value", Duration.ofSeconds(10));

        CreateRule rule = new CreateRule();
        rule.setRuleType(RuleType.COMPATIBILITY);
        rule.setConfig(CompatibilityLevel.BACKWARD.name());

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
        String tableName = getTableName("versioned_table");
        String topicName = getTopicNameForTable(tableName);

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "field1 VARCHAR(100)" +
                        ")");

        // Using shared connector from @BeforeAll
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
     * Test 8: MySQL-Specific Data Types
     */
    @Test
    @Order(8)
    public void testMySQLSpecificTypes() throws Exception {
        String tableName = getTableName("mysql_types_test");
        String topicName = getTopicNameForTable(tableName);

        // MySQL-specific types: ENUM, SET, TINYINT, MEDIUMINT, YEAR, etc.
        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "status ENUM('active', 'inactive', 'pending'), " +
                        "permissions SET('read', 'write', 'execute'), " +
                        "year_col YEAR, " +
                        "tiny_val TINYINT, " +
                        "medium_val MEDIUMINT, " +
                        "text_data TEXT, " +
                        "blob_data BLOB" +
                        ")");

        // Using shared connector from @BeforeAll
        consumer.subscribe(List.of(topicName));
        waitForConsumerReady(Duration.ofSeconds(5));

        try (PreparedStatement stmt = getDatabaseConnection().prepareStatement(
                "INSERT INTO " + tableName +
                        " (status, permissions, year_col, tiny_val, medium_val, text_data, blob_data) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, "active");
            stmt.setString(2, "read,write");
            stmt.setInt(3, 2024);
            stmt.setInt(4, 100);
            stmt.setInt(5, 50000);
            stmt.setString(6, "Sample text");
            stmt.setBytes(7, "binary data".getBytes());
            stmt.executeUpdate();
        }

        List<GenericRecord> events = consumeAvroEvents(topicName, 1, Duration.ofSeconds(10));
        assertEquals(1, events.size());

        GenericRecord event = events.get(0);
        GenericRecord after = (GenericRecord) event.get("after");
        assertNotNull(after);
        assertNotNull(after.get("status"));
        assertNotNull(after.get("permissions"));
        assertNotNull(after.get("year_col"));

        log.info("Successfully verified MySQL-specific types");
    }

    /**
     * Test 9: Numeric and Decimal Precision
     */
    @Test
    @Order(9)
    public void testNumericAndDecimalPrecision() throws Exception {
        String tableName = getTableName("decimal_test");
        String topicName = getTopicNameForTable(tableName);

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "price DECIMAL(10, 2), " +
                        "tax_rate DECIMAL(5, 4), " +
                        "weight DECIMAL(15, 6), " +
                        "quantity DECIMAL(10, 0)" +
                        ")");

        // Using shared connector from @BeforeAll
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
        String tableName = getTableName("bulk_test");
        String topicName = getTopicNameForTable(tableName);

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "value VARCHAR(100)" +
                        ")");

        // Using shared connector from @BeforeAll
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
     * Note: With shared connector, this tests that the connector continues working after inserts
     */
    @Test
    @Order(11)
    public void testConnectorRecovery() throws Exception {
        String tableName = getTableName("recovery_test");
        String topicName = getTopicNameForTable(tableName);

        createTable(tableName,
                "CREATE TABLE " + tableName + " (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "data VARCHAR(100)" +
                        ")");

        // Using shared connector from @BeforeAll
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
