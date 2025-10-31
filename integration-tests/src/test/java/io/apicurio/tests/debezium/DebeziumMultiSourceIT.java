package io.apicurio.tests.debezium;

import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.utils.tests.DebeziumContainerResource;
import io.debezium.testing.testcontainers.ConnectorConfiguration;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.apicurio.tests.debezium.DebeziumTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for multiple database sources with Debezium and Apicurio Registry.
 * Tests concurrent connectors, schema isolation, and cross-database scenarios.
 */
@QuarkusIntegrationTest
@TestProfile(DebeziumIntegrationTestProfile.class)
public class DebeziumMultiSourceIT extends DebeziumTestBase {

    private static final String PG_TOPIC_PREFIX = "multi_pg";
    private static final String MYSQL_TOPIC_PREFIX = "multi_mysql";

    @AfterEach
    public void teardown() {
        if (avroConsumer != null) {
            avroConsumer.close();
            avroConsumer = null;
        }
    }

    @Test
    public void testPostgreSQLAndMySQLSimultaneously() throws Exception {
        // Given: Create tables in both databases
        String pgTable = "pg_orders";
        String mysqlTable = "mysql_products";

        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, order_number VARCHAR(255), amount DECIMAL(10,2))",
            pgTable
        ));

        executeMySqlSQL(String.format(
            "CREATE TABLE %s (id INT AUTO_INCREMENT PRIMARY KEY, product_name VARCHAR(255), price DECIMAL(10,2))",
            mysqlTable
        ));

        // Register both connectors
        ConnectorConfiguration pgConnector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", PG_TOPIC_PREFIX)
            .with("table.include.list", "public." + pgTable)
            .with("slot.name", "multi_pg_slot_" + System.currentTimeMillis())
            .with("publication.name", "multi_pg_pub_" + System.currentTimeMillis());

        ConnectorConfiguration mysqlConnector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.mysqlContainer)
            .with("topic.prefix", MYSQL_TOPIC_PREFIX)
            .with("database.include.list", "inventory")
            .with("table.include.list", "inventory." + mysqlTable)
            .with("include.schema.changes", "false")
            .with("database.server.id", String.valueOf(System.currentTimeMillis()));

        DebeziumContainerResource.debeziumContainer.registerConnector("multi-pg-connector", pgConnector);
        DebeziumContainerResource.debeziumContainer.registerConnector("multi-mysql-connector", mysqlConnector);

        // Subscribe to both topics
        String pgTopic = PG_TOPIC_PREFIX + ".public." + pgTable;
        String mysqlTopic = MYSQL_TOPIC_PREFIX + ".inventory." + mysqlTable;

        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Arrays.asList(pgTopic, mysqlTopic));

        // When: Insert data into both databases
        executePostgresSQL(String.format(
            "INSERT INTO %s (order_number, amount) VALUES ('ORD-001', 150.50)",
            pgTable
        ));

        executeMySqlSQL(String.format(
            "INSERT INTO %s (product_name, price) VALUES ('Widget', 29.99)",
            mysqlTable
        ));

        // Then: Receive events from both sources
        Map<String, ConsumerRecord<String, org.apache.avro.generic.GenericRecord>> receivedEvents = new HashMap<>();
        long startTime = System.currentTimeMillis();

        while (receivedEvents.size() < 2 && (System.currentTimeMillis() - startTime) < 30000) {
            var records = avroConsumer.poll(Duration.ofMillis(100));

            for (var record : records) {
                String op = getOperationType(record.value());
                if ("c".equals(op) || "r".equals(op)) {
                    receivedEvents.put(record.topic(), record);
                    LOGGER.info("Received event from topic: {}", record.topic());
                }
            }
        }

        assertEquals(2, receivedEvents.size(), "Should receive events from both PostgreSQL and MySQL");

        // Verify PostgreSQL event
        assertTrue(receivedEvents.containsKey(pgTopic), "Should have PostgreSQL event");
        org.apache.avro.generic.GenericRecord pgEvent = receivedEvents.get(pgTopic).value();
        org.apache.avro.generic.GenericRecord pgAfter = getAfter(pgEvent);
        assertEquals("ORD-001", pgAfter.get("order_number").toString());

        // Verify MySQL event
        assertTrue(receivedEvents.containsKey(mysqlTopic), "Should have MySQL event");
        org.apache.avro.generic.GenericRecord mysqlEvent = receivedEvents.get(mysqlTopic).value();
        org.apache.avro.generic.GenericRecord mysqlAfter = getAfter(mysqlEvent);
        assertEquals("Widget", mysqlAfter.get("product_name").toString());

        LOGGER.info("Successfully validated concurrent PostgreSQL and MySQL connectors");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + pgTable);
        executeMySqlSQL("DROP TABLE IF EXISTS " + mysqlTable);
    }

    @Test
    public void testMultipleTablesFromSameDatabase() throws Exception {
        // Given: Create multiple tables in PostgreSQL
        String table1 = "multi_customers";
        String table2 = "multi_orders";
        String table3 = "multi_products";

        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, name VARCHAR(255))",
            table1
        ));
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, order_ref VARCHAR(255))",
            table2
        ));
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, product_code VARCHAR(255))",
            table3
        ));

        // Register connector for all tables
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", PG_TOPIC_PREFIX + "_multi")
            .with("table.include.list", String.format("public.%s,public.%s,public.%s", table1, table2, table3))
            .with("slot.name", "multi_tables_slot_" + System.currentTimeMillis())
            .with("publication.name", "multi_tables_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("multi-tables-connector", connector);

        // Subscribe to all topics
        String topic1 = PG_TOPIC_PREFIX + "_multi.public." + table1;
        String topic2 = PG_TOPIC_PREFIX + "_multi.public." + table2;
        String topic3 = PG_TOPIC_PREFIX + "_multi.public." + table3;

        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Arrays.asList(topic1, topic2, topic3));

        // When: Insert data into all tables
        executePostgresSQL(String.format(
            "INSERT INTO %s (name) VALUES ('Customer 1')",
            table1
        ));
        executePostgresSQL(String.format(
            "INSERT INTO %s (order_ref) VALUES ('ORD-123')",
            table2
        ));
        executePostgresSQL(String.format(
            "INSERT INTO %s (product_code) VALUES ('PROD-ABC')",
            table3
        ));

        // Then: Receive events from all tables
        Map<String, Integer> topicEventCounts = new HashMap<>();
        long startTime = System.currentTimeMillis();
        int totalEvents = 0;

        while (totalEvents < 3 && (System.currentTimeMillis() - startTime) < 30000) {
            var records = avroConsumer.poll(Duration.ofMillis(100));

            for (var record : records) {
                String op = getOperationType(record.value());
                if ("c".equals(op) || "r".equals(op)) {
                    topicEventCounts.merge(record.topic(), 1, Integer::sum);
                    totalEvents++;
                    LOGGER.info("Received event from topic: {}", record.topic());
                }
            }
        }

        assertEquals(3, totalEvents, "Should receive events from all 3 tables");
        assertEquals(3, topicEventCounts.size(), "Should have events on 3 different topics");

        LOGGER.info("Successfully validated multiple tables from same database");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + table1);
        executePostgresSQL("DROP TABLE IF EXISTS " + table2);
        executePostgresSQL("DROP TABLE IF EXISTS " + table3);
    }

    @Test
    public void testSchemaNamespaceIsolation() throws Exception {
        // Given: Create tables with same name in different databases
        String tableName = "isolation_test";

        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, pg_data VARCHAR(255))",
            tableName
        ));

        executeMySqlSQL(String.format(
            "CREATE TABLE %s (id INT AUTO_INCREMENT PRIMARY KEY, mysql_data VARCHAR(255))",
            tableName
        ));

        // Register connectors
        ConnectorConfiguration pgConnector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", PG_TOPIC_PREFIX + "_isolation")
            .with("table.include.list", "public." + tableName)
            .with("slot.name", "isolation_pg_slot_" + System.currentTimeMillis())
            .with("publication.name", "isolation_pg_pub_" + System.currentTimeMillis());

        ConnectorConfiguration mysqlConnector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.mysqlContainer)
            .with("topic.prefix", MYSQL_TOPIC_PREFIX + "_isolation")
            .with("database.include.list", "inventory")
            .with("table.include.list", "inventory." + tableName)
            .with("include.schema.changes", "false")
            .with("database.server.id", String.valueOf(System.currentTimeMillis()));

        DebeziumContainerResource.debeziumContainer.registerConnector("isolation-pg-connector", pgConnector);
        DebeziumContainerResource.debeziumContainer.registerConnector("isolation-mysql-connector", mysqlConnector);

        String pgTopic = PG_TOPIC_PREFIX + "_isolation.public." + tableName;
        String mysqlTopic = MYSQL_TOPIC_PREFIX + "_isolation.inventory." + tableName;

        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Arrays.asList(pgTopic, mysqlTopic));

        // When: Insert data into both tables
        executePostgresSQL(String.format(
            "INSERT INTO %s (pg_data) VALUES ('PostgreSQL data')",
            tableName
        ));

        executeMySqlSQL(String.format(
            "INSERT INTO %s (mysql_data) VALUES ('MySQL data')",
            tableName
        ));

        // Then: Verify schemas are isolated (no conflicts)
        Map<String, org.apache.avro.generic.GenericRecord> events = new HashMap<>();
        long startTime = System.currentTimeMillis();

        while (events.size() < 2 && (System.currentTimeMillis() - startTime) < 30000) {
            var records = avroConsumer.poll(Duration.ofMillis(100));

            for (var record : records) {
                String op = getOperationType(record.value());
                if ("c".equals(op) || "r".equals(op)) {
                    events.put(record.topic(), record.value());
                }
            }
        }

        assertEquals(2, events.size(), "Should receive events from both tables");

        // Verify PostgreSQL event has pg_data field
        org.apache.avro.generic.GenericRecord pgEvent = events.get(pgTopic);
        assertNotNull(pgEvent, "Should have PostgreSQL event");
        org.apache.avro.generic.GenericRecord pgAfter = getAfter(pgEvent);
        assertTrue(pgAfter.hasField("pg_data"), "PostgreSQL schema should have pg_data field");
        assertFalse(pgAfter.hasField("mysql_data"), "PostgreSQL schema should not have mysql_data field");

        // Verify MySQL event has mysql_data field
        org.apache.avro.generic.GenericRecord mysqlEvent = events.get(mysqlTopic);
        assertNotNull(mysqlEvent, "Should have MySQL event");
        org.apache.avro.generic.GenericRecord mysqlAfter = getAfter(mysqlEvent);
        assertTrue(mysqlAfter.hasField("mysql_data"), "MySQL schema should have mysql_data field");
        assertFalse(mysqlAfter.hasField("pg_data"), "MySQL schema should not have pg_data field");

        LOGGER.info("Successfully validated schema namespace isolation");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + tableName);
        executeMySqlSQL("DROP TABLE IF EXISTS " + tableName);
    }

    @Test
    public void testNoSchemaConflicts() throws Exception {
        // Given: Multiple tables generating schemas
        String pgTable1 = "no_conflict_pg1";
        String pgTable2 = "no_conflict_pg2";
        String mysqlTable1 = "no_conflict_mysql1";

        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, data1 VARCHAR(255))",
            pgTable1
        ));
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, data2 VARCHAR(255))",
            pgTable2
        ));
        executeMySqlSQL(String.format(
            "CREATE TABLE %s (id INT AUTO_INCREMENT PRIMARY KEY, data3 VARCHAR(255))",
            mysqlTable1
        ));

        // Register connectors
        ConnectorConfiguration pgConnector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", PG_TOPIC_PREFIX + "_noconflict")
            .with("table.include.list", String.format("public.%s,public.%s", pgTable1, pgTable2))
            .with("slot.name", "noconflict_pg_slot_" + System.currentTimeMillis())
            .with("publication.name", "noconflict_pg_pub_" + System.currentTimeMillis());

        ConnectorConfiguration mysqlConnector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.mysqlContainer)
            .with("topic.prefix", MYSQL_TOPIC_PREFIX + "_noconflict")
            .with("database.include.list", "inventory")
            .with("table.include.list", "inventory." + mysqlTable1)
            .with("include.schema.changes", "false")
            .with("database.server.id", String.valueOf(System.currentTimeMillis()));

        DebeziumContainerResource.debeziumContainer.registerConnector("noconflict-pg-connector", pgConnector);
        DebeziumContainerResource.debeziumContainer.registerConnector("noconflict-mysql-connector", mysqlConnector);

        // Subscribe to all topics
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Arrays.asList(
            PG_TOPIC_PREFIX + "_noconflict.public." + pgTable1,
            PG_TOPIC_PREFIX + "_noconflict.public." + pgTable2,
            MYSQL_TOPIC_PREFIX + "_noconflict.inventory." + mysqlTable1
        ));

        // When: Insert data into all tables
        executePostgresSQL(String.format("INSERT INTO %s (data1) VALUES ('PG1')", pgTable1));
        executePostgresSQL(String.format("INSERT INTO %s (data2) VALUES ('PG2')", pgTable2));
        executeMySqlSQL(String.format("INSERT INTO %s (data3) VALUES ('MySQL1')", mysqlTable1));

        // Then: All events are received without schema conflicts
        AtomicInteger eventCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        while (eventCount.get() < 3 && (System.currentTimeMillis() - startTime) < 30000) {
            var records = avroConsumer.poll(Duration.ofMillis(100));

            for (var record : records) {
                String op = getOperationType(record.value());
                if ("c".equals(op) || "r".equals(op)) {
                    eventCount.incrementAndGet();
                    LOGGER.info("Received event from: {}", record.topic());
                }
            }
        }

        assertEquals(3, eventCount.get(), "Should receive all events without schema conflicts");

        // Verify all schemas are registered
        ArtifactSearchResults results = registryClient.search().artifacts()
            .get(config -> {
                config.queryParameters.limit = 100;
                config.queryParameters.order = SortOrder.Asc;
            });

        long schemaCount = results.getArtifacts().stream()
            .filter(artifact -> artifact.getArtifactId().contains("noconflict"))
            .count();

        assertTrue(schemaCount >= 3, "Should have at least 3 schemas registered without conflicts");

        LOGGER.info("Successfully validated no schema conflicts with multiple sources");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + pgTable1);
        executePostgresSQL("DROP TABLE IF EXISTS " + pgTable2);
        executeMySqlSQL("DROP TABLE IF EXISTS " + mysqlTable1);
    }

    @Test
    public void testConcurrentWritesToMultipleSources() throws Exception {
        // Given: Create tables
        String pgTable = "concurrent_pg";
        String mysqlTable = "concurrent_mysql";

        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, counter INT)",
            pgTable
        ));
        executeMySqlSQL(String.format(
            "CREATE TABLE %s (id INT AUTO_INCREMENT PRIMARY KEY, counter INT)",
            mysqlTable
        ));

        // Register connectors
        ConnectorConfiguration pgConnector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", PG_TOPIC_PREFIX + "_concurrent")
            .with("table.include.list", "public." + pgTable)
            .with("slot.name", "concurrent_pg_slot_" + System.currentTimeMillis())
            .with("publication.name", "concurrent_pg_pub_" + System.currentTimeMillis());

        ConnectorConfiguration mysqlConnector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.mysqlContainer)
            .with("topic.prefix", MYSQL_TOPIC_PREFIX + "_concurrent")
            .with("database.include.list", "inventory")
            .with("table.include.list", "inventory." + mysqlTable)
            .with("include.schema.changes", "false")
            .with("database.server.id", String.valueOf(System.currentTimeMillis()));

        DebeziumContainerResource.debeziumContainer.registerConnector("concurrent-pg-connector", pgConnector);
        DebeziumContainerResource.debeziumContainer.registerConnector("concurrent-mysql-connector", mysqlConnector);

        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Arrays.asList(
            PG_TOPIC_PREFIX + "_concurrent.public." + pgTable,
            MYSQL_TOPIC_PREFIX + "_concurrent.inventory." + mysqlTable
        ));

        // When: Concurrent writes to both databases
        int writesPerDb = 20;
        for (int i = 0; i < writesPerDb; i++) {
            executePostgresSQL(String.format("INSERT INTO %s (counter) VALUES (%d)", pgTable, i));
            executeMySqlSQL(String.format("INSERT INTO %s (counter) VALUES (%d)", mysqlTable, i));
        }

        // Then: Receive all events
        AtomicInteger totalEvents = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();
        int expectedTotal = writesPerDb * 2;

        while (totalEvents.get() < expectedTotal && (System.currentTimeMillis() - startTime) < 30000) {
            var records = avroConsumer.poll(Duration.ofMillis(100));

            for (var record : records) {
                String op = getOperationType(record.value());
                if ("c".equals(op) || "r".equals(op)) {
                    totalEvents.incrementAndGet();
                }
            }
        }

        assertTrue(totalEvents.get() >= expectedTotal * 0.95,
            "Should receive at least 95% of events from concurrent writes");

        LOGGER.info("Successfully validated concurrent writes to multiple sources: received {} events",
            totalEvents.get());

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + pgTable);
        executeMySqlSQL("DROP TABLE IF EXISTS " + mysqlTable);
    }
}
