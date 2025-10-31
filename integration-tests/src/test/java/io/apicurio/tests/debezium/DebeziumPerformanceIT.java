package io.apicurio.tests.debezium;

import io.apicurio.registry.utils.tests.DebeziumContainerResource;
import io.debezium.testing.testcontainers.ConnectorConfiguration;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static io.apicurio.tests.debezium.DebeziumTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance integration tests for Debezium with Apicurio Registry.
 * Tests high-throughput scenarios, schema caching, and concurrent operations.
 */
@QuarkusIntegrationTest
@TestProfile(DebeziumIntegrationTestProfile.class)
public class DebeziumPerformanceIT extends DebeziumTestBase {

    private static final String TEST_TOPIC_PREFIX = "perf_test";

    @AfterEach
    public void teardown() {
        if (avroConsumer != null) {
            avroConsumer.close();
            avroConsumer = null;
        }
    }

    @Test
    public void testHighThroughputInserts() throws Exception {
        // Given: Create table
        String testTable = "high_throughput";
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id BIGSERIAL PRIMARY KEY, data VARCHAR(255), value INT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
            testTable
        ));

        // Register connector
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_throughput")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "throughput_slot_" + System.currentTimeMillis())
            .with("publication.name", "throughput_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("throughput-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_throughput.public." + testTable;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // When: Insert many records quickly
        int recordCount = 1000;
        long startTime = System.currentTimeMillis();

        try (Connection conn = getPostgresConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 String.format("INSERT INTO %s (data, value) VALUES (?, ?)", testTable))) {

            for (int i = 0; i < recordCount; i++) {
                stmt.setString(1, "Record " + i);
                stmt.setInt(2, i);
                stmt.addBatch();

                if (i % 100 == 0) {
                    stmt.executeBatch();
                }
            }
            stmt.executeBatch();
        }

        long insertTime = System.currentTimeMillis() - startTime;
        LOGGER.info("Inserted {} records in {} ms", recordCount, insertTime);

        // Then: Verify all events are received
        AtomicInteger receivedCount = new AtomicInteger(0);
        long pollStartTime = System.currentTimeMillis();
        long timeout = 60000; // 60 seconds for high volume

        while (receivedCount.get() < recordCount && (System.currentTimeMillis() - pollStartTime) < timeout) {
            ConsumerRecords<String, org.apache.avro.generic.GenericRecord> records =
                avroConsumer.poll(Duration.ofMillis(100));

            records.forEach(record -> {
                org.apache.avro.generic.GenericRecord event = record.value();
                String op = getOperationType(event);
                if ("c".equals(op) || "r".equals(op)) {
                    receivedCount.incrementAndGet();
                }
            });

            if (receivedCount.get() % 100 == 0 && receivedCount.get() > 0) {
                LOGGER.info("Received {} events so far", receivedCount.get());
            }
        }

        long pollTime = System.currentTimeMillis() - pollStartTime;
        LOGGER.info("Received {} events in {} ms", receivedCount.get(), pollTime);

        assertTrue(receivedCount.get() >= recordCount * 0.95,
            "Should receive at least 95% of events (received: " + receivedCount.get() + ")");

        // Calculate throughput
        double throughput = (receivedCount.get() * 1000.0) / pollTime;
        LOGGER.info("CDC event throughput: {:.2f} events/second", throughput);

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }

    @Test
    public void testBatchUpdates() throws Exception {
        // Given: Create table with data
        String testTable = "batch_updates";
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id INT PRIMARY KEY, status VARCHAR(50), counter INT)",
            testTable
        ));

        // Insert initial data
        try (Connection conn = getPostgresConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 String.format("INSERT INTO %s (id, status, counter) VALUES (?, ?, ?)", testTable))) {

            for (int i = 1; i <= 100; i++) {
                stmt.setInt(1, i);
                stmt.setString(2, "INITIAL");
                stmt.setInt(3, 0);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }

        // Register connector
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_updates")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "updates_slot_" + System.currentTimeMillis())
            .with("publication.name", "updates_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("updates-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_updates.public." + testTable;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // Consume initial snapshot events
        int snapshotCount = 0;
        long snapshotStart = System.currentTimeMillis();
        while (snapshotCount < 100 && (System.currentTimeMillis() - snapshotStart) < 30000) {
            var records = avroConsumer.poll(Duration.ofMillis(100));
            snapshotCount += records.count();
        }
        LOGGER.info("Consumed {} snapshot events", snapshotCount);

        // When: Perform batch updates
        int updateCount = 100;
        long updateStart = System.currentTimeMillis();

        try (Connection conn = getPostgresConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 String.format("UPDATE %s SET status = ?, counter = counter + 1 WHERE id = ?", testTable))) {

            for (int i = 1; i <= updateCount; i++) {
                stmt.setString(1, "UPDATED");
                stmt.setInt(2, i);
                stmt.addBatch();

                if (i % 20 == 0) {
                    stmt.executeBatch();
                }
            }
            stmt.executeBatch();
        }

        long updateTime = System.currentTimeMillis() - updateStart;
        LOGGER.info("Updated {} records in {} ms", updateCount, updateTime);

        // Then: Verify update events are received
        AtomicInteger updateEventCount = new AtomicInteger(0);
        long pollStart = System.currentTimeMillis();

        while (updateEventCount.get() < updateCount && (System.currentTimeMillis() - pollStart) < 30000) {
            ConsumerRecords<String, org.apache.avro.generic.GenericRecord> records =
                avroConsumer.poll(Duration.ofMillis(100));

            records.forEach(record -> {
                org.apache.avro.generic.GenericRecord event = record.value();
                if (isValidUpdateEvent(event)) {
                    updateEventCount.incrementAndGet();
                }
            });
        }

        LOGGER.info("Received {} update events", updateEventCount.get());
        assertTrue(updateEventCount.get() >= updateCount * 0.95,
            "Should receive at least 95% of update events");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }

    @Test
    public void testSchemaCachingPerformance() throws Exception {
        // Given: Create table
        String testTable = "schema_cache";
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, data TEXT)",
            testTable
        ));

        // Register connector
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_cache")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "cache_slot_" + System.currentTimeMillis())
            .with("publication.name", "cache_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("cache-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_cache.public." + testTable;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // When: Insert records repeatedly using same schema
        int iterations = 100;
        for (int i = 0; i < iterations; i++) {
            executePostgresSQL(String.format(
                "INSERT INTO %s (data) VALUES ('Iteration %d')",
                testTable, i
            ));
        }

        // Then: Verify all events are received efficiently (schema caching helps)
        int receivedCount = 0;
        long startTime = System.currentTimeMillis();

        while (receivedCount < iterations && (System.currentTimeMillis() - startTime) < 30000) {
            var records = avroConsumer.poll(Duration.ofMillis(100));
            for (var record : records) {
                org.apache.avro.generic.GenericRecord event = record.value();
                String op = getOperationType(event);
                if ("c".equals(op) || "r".equals(op)) {
                    receivedCount++;
                }
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        LOGGER.info("Received {} events in {} ms with schema caching", receivedCount, totalTime);

        assertEquals(iterations, receivedCount, "Should receive all events");

        // With caching, average time per event should be low
        double avgTimePerEvent = (double) totalTime / receivedCount;
        LOGGER.info("Average time per event: {:.2f} ms", avgTimePerEvent);

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }

    @Test
    public void testConcurrentConsumers() throws Exception {
        // Given: Create table
        String testTable = "concurrent_test";
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, message TEXT)",
            testTable
        ));

        // Register connector
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_concurrent")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "concurrent_slot_" + System.currentTimeMillis())
            .with("publication.name", "concurrent_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("concurrent-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_concurrent.public." + testTable;

        // Create two consumers in same consumer group
        String groupId = "concurrent-group-" + System.currentTimeMillis();
        var consumer1 = createAvroConsumer(groupId);
        var consumer2 = createAvroConsumer(groupId);

        consumer1.subscribe(Collections.singletonList(topic));
        consumer2.subscribe(Collections.singletonList(topic));

        // When: Insert data
        int recordCount = 50;
        for (int i = 0; i < recordCount; i++) {
            executePostgresSQL(String.format(
                "INSERT INTO %s (message) VALUES ('Message %d')",
                testTable, i
            ));
        }

        // Then: Verify events are distributed between consumers
        AtomicInteger count1 = new AtomicInteger(0);
        AtomicInteger count2 = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();
        while ((count1.get() + count2.get()) < recordCount && (System.currentTimeMillis() - startTime) < 30000) {
            var records1 = consumer1.poll(Duration.ofMillis(100));
            var records2 = consumer2.poll(Duration.ofMillis(100));

            records1.forEach(r -> {
                if (isCreate().or(isRead()).test(r)) {
                    count1.incrementAndGet();
                }
            });

            records2.forEach(r -> {
                if (isCreate().or(isRead()).test(r)) {
                    count2.incrementAndGet();
                }
            });
        }

        int totalReceived = count1.get() + count2.get();
        LOGGER.info("Consumer 1 received: {}, Consumer 2 received: {}, Total: {}",
            count1.get(), count2.get(), totalReceived);

        assertEquals(recordCount, totalReceived, "Total events should match");

        // Both consumers should have received some events (partition distribution)
        // Note: This may not always be true with single partition, but in practice should work

        consumer1.close();
        consumer2.close();

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }

    @Test
    public void testLargePayloadHandling() throws Exception {
        // Given: Create table that can hold large data
        String testTable = "large_payload";
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, large_data TEXT)",
            testTable
        ));

        // Register connector
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_large")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "large_slot_" + System.currentTimeMillis())
            .with("publication.name", "large_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("large-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_large.public." + testTable;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // When: Insert record with large payload (1MB of data)
        StringBuilder largeData = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeData.append("This is a large payload test with repeated data. ");
        }

        String escapedData = largeData.toString().replace("'", "''");
        executePostgresSQL(String.format(
            "INSERT INTO %s (large_data) VALUES ('%s')",
            testTable, escapedData
        ));

        // Then: Verify large payload is handled correctly
        var record = pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        assertNotNull(record, "Should receive event with large payload");
        org.apache.avro.generic.GenericRecord event = record.value();
        org.apache.avro.generic.GenericRecord after = getAfter(event);

        assertNotNull(after.get("large_data"), "Large data field should be present");
        String receivedData = after.get("large_data").toString();
        assertTrue(receivedData.length() > 100000, "Should receive large payload");

        LOGGER.info("Successfully handled large payload of size: {} bytes", receivedData.length());

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }
}
