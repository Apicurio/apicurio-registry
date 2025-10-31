package io.apicurio.tests.debezium;

import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.utils.tests.DebeziumContainerResource;
import io.debezium.testing.testcontainers.ConnectorConfiguration;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Collections;

import static io.apicurio.tests.debezium.DebeziumTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Debezium MySQL connector with Apicurio Registry.
 * Tests CDC event flow, schema registration, and CRUD operations with MySQL.
 */
@QuarkusIntegrationTest
@TestProfile(DebeziumIntegrationTestProfile.class)
public class DebeziumMySQLConnectorIT extends DebeziumTestBase {

    private static final String TEST_TOPIC_PREFIX = "mysql_test";
    private static final String TEST_DATABASE = "inventory";
    private static final String TEST_TABLE = "test_products";
    private static final String CONNECTOR_NAME = "mysql-test-connector";

    @BeforeEach
    public void setup() throws SQLException {
        // Create test table in MySQL
        String createTableSQL = String.format(
            "CREATE TABLE IF NOT EXISTS %s (" +
            "id INT AUTO_INCREMENT PRIMARY KEY, " +
            "name VARCHAR(255), " +
            "description TEXT, " +
            "price DECIMAL(10, 2), " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")",
            TEST_TABLE
        );
        executeMySqlSQL(createTableSQL);

        // Register MySQL connector
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.mysqlContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX)
            .with("database.include.list", TEST_DATABASE)
            .with("table.include.list", TEST_DATABASE + "." + TEST_TABLE)
            .with("include.schema.changes", "false")
            .with("database.server.id", String.valueOf(System.currentTimeMillis()));

        DebeziumContainerResource.debeziumContainer.registerConnector(CONNECTOR_NAME, connector);

        // Create Kafka consumer
        String topic = TEST_TOPIC_PREFIX + "." + TEST_DATABASE + "." + TEST_TABLE;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        LOGGER.info("MySQL test setup complete. Topic: {}", topic);
    }

    @AfterEach
    public void teardown() throws SQLException {
        // Clean up
        if (avroConsumer != null) {
            avroConsumer.close();
            avroConsumer = null;
        }

        // Drop test table
        try {
            executeMySqlSQL("DROP TABLE IF EXISTS " + TEST_TABLE);
        } catch (Exception e) {
            LOGGER.warn("Error dropping table: {}", e.getMessage());
        }
    }

    @Test
    public void testMySQLInsertOperation() throws SQLException {
        // Given: Insert a record into MySQL
        String insertSQL = String.format(
            "INSERT INTO %s (name, description, price) VALUES ('Product 1', 'Test product', 29.99)",
            TEST_TABLE
        );
        executeMySqlSQL(insertSQL);

        // When: Poll for CDC event
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> record =
            pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        // Then: Verify event structure and content
        assertNotNull(record, "Should receive a CDC event");
        org.apache.avro.generic.GenericRecord event = record.value();

        assertTrue(hasValidDebeziumStructure(event), "Event should have valid Debezium structure");

        String op = getOperationType(event);
        assertTrue("c".equals(op) || "r".equals(op), "Operation should be create or read (snapshot)");

        org.apache.avro.generic.GenericRecord after = getAfter(event);
        assertNotNull(after, "After value should not be null");
        assertEquals("Product 1", after.get("name").toString());
        assertEquals("Test product", after.get("description").toString());

        LOGGER.info("Successfully validated MySQL INSERT operation CDC event");
    }

    @Test
    public void testMySQLUpdateOperation() throws SQLException {
        // Given: Insert a record
        executeMySqlSQL(String.format(
            "INSERT INTO %s (id, name, description, price) VALUES (100, 'Product A', 'Original', 19.99)",
            TEST_TABLE
        ));

        // Wait for initial event
        pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        // When: Update the record
        executeMySqlSQL(String.format(
            "UPDATE %s SET description = 'Updated description', price = 24.99 WHERE id = 100",
            TEST_TABLE
        ));

        // Poll for UPDATE event
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> record =
            pollForEvent(avroConsumer, isUpdate(), 30);

        // Then: Verify update event
        assertNotNull(record, "Should receive an UPDATE event");
        org.apache.avro.generic.GenericRecord event = record.value();

        assertTrue(isValidUpdateEvent(event), "Should be a valid UPDATE event");

        org.apache.avro.generic.GenericRecord before = getBefore(event);
        org.apache.avro.generic.GenericRecord after = getAfter(event);

        assertNotNull(before, "Before value should not be null");
        assertNotNull(after, "After value should not be null");

        assertEquals("Original", before.get("description").toString());
        assertEquals("Updated description", after.get("description").toString());

        LOGGER.info("Successfully validated MySQL UPDATE operation CDC event");
    }

    @Test
    public void testMySQLDeleteOperation() throws SQLException {
        // Given: Insert a record
        executeMySqlSQL(String.format(
            "INSERT INTO %s (id, name, description, price) VALUES (200, 'Product B', 'To be deleted', 9.99)",
            TEST_TABLE
        ));

        // Wait for initial event
        pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        // When: Delete the record
        executeMySqlSQL(String.format(
            "DELETE FROM %s WHERE id = 200",
            TEST_TABLE
        ));

        // Poll for DELETE event
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> record =
            pollForEvent(avroConsumer, isDelete(), 30);

        // Then: Verify delete event
        assertNotNull(record, "Should receive a DELETE event");
        org.apache.avro.generic.GenericRecord event = record.value();

        assertTrue(isValidDeleteEvent(event), "Should be a valid DELETE event");

        org.apache.avro.generic.GenericRecord before = getBefore(event);
        org.apache.avro.generic.GenericRecord after = getAfter(event);

        assertNotNull(before, "Before value should not be null for DELETE");
        assertNull(after, "After value should be null for DELETE");

        assertEquals("Product B", before.get("name").toString());

        LOGGER.info("Successfully validated MySQL DELETE operation CDC event");
    }

    @Test
    public void testMySQLSchemaRegistration() throws SQLException {
        // Given: Insert a record to trigger schema registration
        executeMySqlSQL(String.format(
            "INSERT INTO %s (name, description, price) VALUES ('Schema Test', 'Testing schema', 99.99)",
            TEST_TABLE
        ));

        // Wait for event
        pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        // Then: Verify schemas are registered in Apicurio Registry
        ArtifactSearchResults results = registryClient.search().artifacts()
            .get(config -> {
                config.queryParameters.limit = 100;
                config.queryParameters.order = SortOrder.Asc;
            });

        assertNotNull(results, "Should retrieve artifact list");
        assertFalse(results.getArtifacts().isEmpty(), "Should have at least one schema registered");

        // Look for our topic schemas
        boolean foundValueSchema = results.getArtifacts().stream()
            .anyMatch(artifact -> artifact.getArtifactId().contains(TEST_TOPIC_PREFIX) &&
                                 artifact.getArtifactId().contains(TEST_TABLE));

        assertTrue(foundValueSchema, "Should find schema for our MySQL test topic");

        LOGGER.info("Successfully validated MySQL schema registration in Apicurio Registry");
    }

    @Test
    public void testMySQLDecimalHandling() throws SQLException {
        // Given: Insert a record with decimal values
        executeMySqlSQL(String.format(
            "INSERT INTO %s (name, description, price) VALUES ('Decimal Test', 'Testing decimals', 123.45)",
            TEST_TABLE
        ));

        // When: Poll for event
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> record =
            pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        // Then: Verify decimal value handling
        assertNotNull(record, "Should receive a CDC event");
        org.apache.avro.generic.GenericRecord event = record.value();
        org.apache.avro.generic.GenericRecord after = getAfter(event);

        assertNotNull(after, "After value should not be null");
        assertNotNull(after.get("price"), "Price should not be null");

        LOGGER.info("Successfully validated MySQL decimal handling in CDC event");
    }

    @Test
    public void testMySQLBatchInserts() throws SQLException {
        // Given: Insert multiple records in a batch
        for (int i = 1; i <= 10; i++) {
            executeMySqlSQL(String.format(
                "INSERT INTO %s (name, description, price) VALUES ('Product %d', 'Batch product %d', %d.99)",
                TEST_TABLE, i, i, i * 10
            ));
        }

        // Then: Verify we receive all events
        int eventCount = 0;
        long startTime = System.currentTimeMillis();
        long timeout = 30000; // 30 seconds

        while (eventCount < 10 && (System.currentTimeMillis() - startTime) < timeout) {
            var records = avroConsumer.poll(java.time.Duration.ofMillis(100));
            for (var record : records) {
                org.apache.avro.generic.GenericRecord event = record.value();
                String op = getOperationType(event);
                if ("c".equals(op) || "r".equals(op)) {
                    eventCount++;
                    org.apache.avro.generic.GenericRecord after = getAfter(event);
                    assertNotNull(after, "After value should not be null");
                    LOGGER.info("Received event {} with name: {}", eventCount, after.get("name"));
                }
            }
        }

        assertEquals(10, eventCount, "Should receive all 10 CDC events from MySQL");

        LOGGER.info("Successfully validated MySQL batch INSERT operations");
    }

    @Test
    public void testMySQLSourceMetadata() throws SQLException {
        // Given: Insert a record
        executeMySqlSQL(String.format(
            "INSERT INTO %s (name, description, price) VALUES ('Metadata Test', 'Testing metadata', 49.99)",
            TEST_TABLE
        ));

        // When: Poll for event
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> record =
            pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        // Then: Verify source metadata specific to MySQL
        assertNotNull(record, "Should receive a CDC event");
        org.apache.avro.generic.GenericRecord event = record.value();
        org.apache.avro.generic.GenericRecord source = getSource(event);

        assertNotNull(source, "Source metadata should not be null");
        assertNotNull(source.get("version"), "Source should have version");
        assertNotNull(source.get("connector"), "Source should have connector type");
        assertEquals("mysql", source.get("connector").toString(), "Connector should be mysql");
        assertNotNull(source.get("name"), "Source should have connector name");
        assertEquals(TEST_DATABASE, source.get("db").toString(), "Database name should match");
        assertEquals(TEST_TABLE, source.get("table").toString(), "Table name should match");

        LOGGER.info("Successfully validated MySQL source metadata in CDC event");
    }
}
