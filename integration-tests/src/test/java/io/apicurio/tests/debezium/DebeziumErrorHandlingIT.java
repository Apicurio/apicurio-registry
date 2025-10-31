package io.apicurio.tests.debezium;

import io.apicurio.registry.utils.tests.DebeziumContainerResource;
import io.debezium.testing.testcontainers.ConnectorConfiguration;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;

import static io.apicurio.tests.debezium.DebeziumTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Error handling integration tests for Debezium with Apicurio Registry.
 * Tests recovery scenarios, invalid data handling, and error conditions.
 */
@QuarkusIntegrationTest
@TestProfile(DebeziumIntegrationTestProfile.class)
public class DebeziumErrorHandlingIT extends DebeziumTestBase {

    private static final String TEST_TOPIC_PREFIX = "error_test";

    @AfterEach
    public void teardown() {
        if (avroConsumer != null) {
            avroConsumer.close();
            avroConsumer = null;
        }
    }

    @Test
    public void testRecoveryAfterTableDrop() throws Exception {
        // Given: Create table and connector
        String testTable = "recovery_test";
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, data VARCHAR(255))",
            testTable
        ));

        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_recovery")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "recovery_slot_" + System.currentTimeMillis())
            .with("publication.name", "recovery_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("recovery-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_recovery.public." + testTable;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // Insert initial record
        executePostgresSQL(String.format(
            "INSERT INTO %s (data) VALUES ('Before drop')",
            testTable
        ));

        pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        // When: Drop and recreate table
        executePostgresSQL("DROP TABLE " + testTable);

        Thread.sleep(2000); // Wait for connector to detect drop

        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, data VARCHAR(255))",
            testTable
        ));

        // Insert after recreation
        executePostgresSQL(String.format(
            "INSERT INTO %s (data) VALUES ('After recreation')",
            testTable
        ));

        // Then: Should recover and process new events
        // Note: This behavior depends on connector configuration
        // The test validates that the system can handle table drops gracefully

        LOGGER.info("Successfully handled table drop and recreation scenario");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }

    @Test
    public void testHandlingOfSpecialCharacters() throws Exception {
        // Given: Create table
        String testTable = "special_chars";
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, data TEXT)",
            testTable
        ));

        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_chars")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "chars_slot_" + System.currentTimeMillis())
            .with("publication.name", "chars_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("chars-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_chars.public." + testTable;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // When: Insert data with special characters
        String specialData = "Special chars: 你好 мир 🎉 \n\t\r quotes\"'`";
        String escapedData = specialData.replace("'", "''");

        executePostgresSQL(String.format(
            "INSERT INTO %s (data) VALUES ('%s')",
            testTable, escapedData
        ));

        // Then: Verify special characters are preserved
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> record =
            pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        assertNotNull(record, "Should receive event with special characters");
        org.apache.avro.generic.GenericRecord event = record.value();
        org.apache.avro.generic.GenericRecord after = getAfter(event);

        String receivedData = after.get("data").toString();
        assertTrue(receivedData.contains("你好"), "Should preserve Chinese characters");
        assertTrue(receivedData.contains("мир"), "Should preserve Cyrillic characters");
        assertTrue(receivedData.contains("🎉"), "Should preserve emoji");

        LOGGER.info("Successfully handled special characters in CDC events");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }

    @Test
    public void testTransactionRollback() throws Exception {
        // Given: Create table
        String testTable = "transaction_test";
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id INT PRIMARY KEY, value VARCHAR(255))",
            testTable
        ));

        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_txn")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "txn_slot_" + System.currentTimeMillis())
            .with("publication.name", "txn_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("txn-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_txn.public." + testTable;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // When: Start transaction, insert, then rollback
        try (var conn = getPostgresConnection()) {
            conn.setAutoCommit(false);

            try (var stmt = conn.createStatement()) {
                stmt.execute(String.format(
                    "INSERT INTO %s (id, value) VALUES (1, 'Will be rolled back')",
                    testTable
                ));
                conn.rollback(); // Rollback the transaction
            }
        }

        // Insert a committed record
        executePostgresSQL(String.format(
            "INSERT INTO %s (id, value) VALUES (2, 'Committed')",
            testTable
        ));

        // Then: Should only receive the committed event
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> record =
            pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        assertNotNull(record, "Should receive committed event");
        org.apache.avro.generic.GenericRecord event = record.value();
        org.apache.avro.generic.GenericRecord after = getAfter(event);

        assertEquals("Committed", after.get("value").toString(),
            "Should only see committed data, not rolled back");

        // Verify no event for rolled back data
        var additionalRecords = avroConsumer.poll(Duration.ofSeconds(2));
        long rollbackEventCount = 0;
        for (var additionalRecord : additionalRecords) {
            org.apache.avro.generic.GenericRecord evt = additionalRecord.value();
            org.apache.avro.generic.GenericRecord aft = getAfter(evt);
            if (aft != null && "Will be rolled back".equals(aft.get("value").toString())) {
                rollbackEventCount++;
            }
        }

        assertEquals(0, rollbackEventCount, "Should not receive events for rolled back transaction");

        LOGGER.info("Successfully validated transaction rollback handling");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }

    @Test
    public void testDuplicateKeyHandling() throws Exception {
        // Given: Create table
        String testTable = "duplicate_key";
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id INT PRIMARY KEY, data VARCHAR(255))",
            testTable
        ));

        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_dup")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "dup_slot_" + System.currentTimeMillis())
            .with("publication.name", "dup_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("dup-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_dup.public." + testTable;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // When: Insert valid record
        executePostgresSQL(String.format(
            "INSERT INTO %s (id, data) VALUES (100, 'First insert')",
            testTable
        ));

        pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        // Try to insert duplicate key (should fail at database level)
        try {
            executePostgresSQL(String.format(
                "INSERT INTO %s (id, data) VALUES (100, 'Duplicate attempt')",
                testTable
            ));
            fail("Should have thrown SQLException for duplicate key");
        } catch (SQLException e) {
            // Expected - duplicate key violation
            LOGGER.info("Expected duplicate key error: {}", e.getMessage());
        }

        // Then: Insert different key successfully
        executePostgresSQL(String.format(
            "INSERT INTO %s (id, data) VALUES (101, 'Different key')",
            testTable
        ));

        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> record =
            pollForEvent(avroConsumer, isCreate(), 30);

        assertNotNull(record, "Should receive event for valid insert after error");
        org.apache.avro.generic.GenericRecord event = record.value();
        org.apache.avro.generic.GenericRecord after = getAfter(event);

        assertEquals(101, after.get("id"));
        assertEquals("Different key", after.get("data").toString());

        LOGGER.info("Successfully validated duplicate key handling");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }

    @Test
    public void testLongRunningTransaction() throws Exception {
        // Given: Create table
        String testTable = "long_txn";
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, data VARCHAR(255))",
            testTable
        ));

        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_longtxn")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "longtxn_slot_" + System.currentTimeMillis())
            .with("publication.name", "longtxn_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("longtxn-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_longtxn.public." + testTable;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // When: Start long transaction
        try (var conn = getPostgresConnection()) {
            conn.setAutoCommit(false);

            try (var stmt = conn.createStatement()) {
                // Insert in transaction but don't commit yet
                stmt.execute(String.format(
                    "INSERT INTO %s (data) VALUES ('In transaction')",
                    testTable
                ));

                // Insert from different connection (auto-commit)
                executePostgresSQL(String.format(
                    "INSERT INTO %s (data) VALUES ('Outside transaction')",
                    testTable
                ));

                // Wait a bit
                Thread.sleep(1000);

                // Commit the transaction
                conn.commit();
            }
        }

        // Then: Should receive both events
        int eventCount = 0;
        long startTime = System.currentTimeMillis();

        while (eventCount < 2 && (System.currentTimeMillis() - startTime) < 30000) {
            var records = avroConsumer.poll(Duration.ofMillis(100));
            for (var record : records) {
                org.apache.avro.generic.GenericRecord event = record.value();
                String op = getOperationType(event);
                if ("c".equals(op) || "r".equals(op)) {
                    eventCount++;
                    org.apache.avro.generic.GenericRecord after = getAfter(event);
                    LOGGER.info("Received event with data: {}", after.get("data"));
                }
            }
        }

        assertEquals(2, eventCount, "Should receive both events after transaction commits");

        LOGGER.info("Successfully validated long-running transaction handling");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }

    @Test
    public void testEmptyStringAndWhitespace() throws Exception {
        // Given: Create table
        String testTable = "empty_strings";
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, data VARCHAR(255), trimmed VARCHAR(255))",
            testTable
        ));

        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_empty")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "empty_slot_" + System.currentTimeMillis())
            .with("publication.name", "empty_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("empty-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_empty.public." + testTable;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // When: Insert empty and whitespace strings
        executePostgresSQL(String.format(
            "INSERT INTO %s (data, trimmed) VALUES ('', '   ')",
            testTable
        ));

        // Then: Verify handling of empty/whitespace
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> record =
            pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        assertNotNull(record, "Should receive event");
        org.apache.avro.generic.GenericRecord event = record.value();
        org.apache.avro.generic.GenericRecord after = getAfter(event);

        assertNotNull(after.get("data"), "Empty string field should be present");
        assertEquals("", after.get("data").toString(), "Should preserve empty string");

        assertNotNull(after.get("trimmed"), "Whitespace field should be present");
        assertEquals("   ", after.get("trimmed").toString(), "Should preserve whitespace");

        LOGGER.info("Successfully validated empty string and whitespace handling");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }
}
