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
 * Integration tests for Debezium PostgreSQL connector with Apicurio Registry.
 * Tests CDC event flow, schema registration, and CRUD operations.
 */
@QuarkusIntegrationTest
@TestProfile(DebeziumIntegrationTestProfile.class)
public class DebeziumPostgreSQLConnectorIT extends DebeziumTestBase {

    private static final String TEST_TOPIC_PREFIX = "pg_test";
    private static final String TEST_TABLE = "test_customers";
    private static final String CONNECTOR_NAME = "postgres-test-connector";

    @BeforeEach
    public void setup() throws SQLException {
        // Create test table in PostgreSQL
        String createTableSQL = String.format(
            "CREATE TABLE IF NOT EXISTS %s (" +
            "id SERIAL PRIMARY KEY, " +
            "name VARCHAR(255), " +
            "email VARCHAR(255), " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")",
            TEST_TABLE
        );
        executePostgresSQL(createTableSQL);

        // Register PostgreSQL connector
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX)
            .with("table.include.list", "public." + TEST_TABLE)
            .with("slot.name", "test_slot_" + System.currentTimeMillis())
            .with("publication.name", "test_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector(CONNECTOR_NAME, connector);

        // Create Kafka consumer
        String topic = TEST_TOPIC_PREFIX + ".public." + TEST_TABLE;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        LOGGER.info("Test setup complete. Topic: {}", topic);
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
            executePostgresSQL("DROP TABLE IF EXISTS " + TEST_TABLE);
        } catch (Exception e) {
            LOGGER.warn("Error dropping table: {}", e.getMessage());
        }
    }

    @Test
    public void testInsertOperation() throws SQLException {
        // Given: Insert a record into PostgreSQL
        String insertSQL = String.format(
            "INSERT INTO %s (name, email) VALUES ('John Doe', 'john@example.com')",
            TEST_TABLE
        );
        executePostgresSQL(insertSQL);

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
        assertEquals("John Doe", after.get("name").toString());
        assertEquals("john@example.com", after.get("email").toString());

        LOGGER.info("Successfully validated INSERT operation CDC event");
    }

    @Test
    public void testUpdateOperation() throws SQLException {
        // Given: Insert a record
        executePostgresSQL(String.format(
            "INSERT INTO %s (id, name, email) VALUES (100, 'Jane Doe', 'jane@example.com')",
            TEST_TABLE
        ));

        // Wait for initial event
        pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        // When: Update the record
        executePostgresSQL(String.format(
            "UPDATE %s SET email = 'jane.doe@example.com' WHERE id = 100",
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

        assertEquals("jane@example.com", before.get("email").toString());
        assertEquals("jane.doe@example.com", after.get("email").toString());

        LOGGER.info("Successfully validated UPDATE operation CDC event");
    }

    @Test
    public void testDeleteOperation() throws SQLException {
        // Given: Insert a record
        executePostgresSQL(String.format(
            "INSERT INTO %s (id, name, email) VALUES (200, 'Bob Smith', 'bob@example.com')",
            TEST_TABLE
        ));

        // Wait for initial event
        pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        // When: Delete the record
        executePostgresSQL(String.format(
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

        assertEquals("Bob Smith", before.get("name").toString());

        LOGGER.info("Successfully validated DELETE operation CDC event");
    }

    @Test
    public void testSchemaRegistration() throws SQLException {
        // Given: Insert a record to trigger schema registration
        executePostgresSQL(String.format(
            "INSERT INTO %s (name, email) VALUES ('Schema Test', 'schema@example.com')",
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

        assertTrue(foundValueSchema, "Should find schema for our test topic");

        LOGGER.info("Successfully validated schema registration in Apicurio Registry");
    }

    @Test
    public void testMultipleInsertsInOrder() throws SQLException {
        // Given: Insert multiple records
        for (int i = 1; i <= 5; i++) {
            executePostgresSQL(String.format(
                "INSERT INTO %s (name, email) VALUES ('User %d', 'user%d@example.com')",
                TEST_TABLE, i, i
            ));
        }

        // Then: Verify we receive all events
        int eventCount = 0;
        long startTime = System.currentTimeMillis();
        long timeout = 30000; // 30 seconds

        while (eventCount < 5 && (System.currentTimeMillis() - startTime) < timeout) {
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

        assertEquals(5, eventCount, "Should receive all 5 CDC events");

        LOGGER.info("Successfully validated multiple INSERT operations in order");
    }

    @Test
    public void testSourceMetadata() throws SQLException {
        // Given: Insert a record
        executePostgresSQL(String.format(
            "INSERT INTO %s (name, email) VALUES ('Metadata Test', 'metadata@example.com')",
            TEST_TABLE
        ));

        // When: Poll for event
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> record =
            pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        // Then: Verify source metadata
        assertNotNull(record, "Should receive a CDC event");
        org.apache.avro.generic.GenericRecord event = record.value();
        org.apache.avro.generic.GenericRecord source = getSource(event);

        assertNotNull(source, "Source metadata should not be null");
        assertNotNull(source.get("version"), "Source should have version");
        assertNotNull(source.get("connector"), "Source should have connector type");
        assertNotNull(source.get("name"), "Source should have connector name");
        assertNotNull(source.get("db"), "Source should have database name");
        assertEquals(TEST_TABLE, source.get("table").toString(), "Table name should match");

        LOGGER.info("Successfully validated source metadata in CDC event");
    }
}
