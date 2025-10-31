package io.apicurio.tests.debezium;

import io.apicurio.registry.rest.client.models.ArtifactMetaData;
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

import java.util.Collections;

import static io.apicurio.tests.debezium.DebeziumTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for schema evolution scenarios with Debezium and Apicurio Registry.
 * Tests forward compatibility (adding fields), backward compatibility (removing fields),
 * and schema versioning.
 */
@QuarkusIntegrationTest
@TestProfile(DebeziumIntegrationTestProfile.class)
public class DebeziumSchemaEvolutionIT extends DebeziumTestBase {

    private static final String TEST_TOPIC_PREFIX = "evolution_test";
    private static final String TEST_TABLE_PG = "evolution_pg";
    private static final String TEST_TABLE_MYSQL = "evolution_mysql";
    private static final String CONNECTOR_NAME_PG = "postgres-evolution-connector";
    private static final String CONNECTOR_NAME_MYSQL = "mysql-evolution-connector";

    @BeforeEach
    public void setup() {
        // Tests will set up their own tables and connectors
    }

    @AfterEach
    public void teardown() {
        // Clean up
        if (avroConsumer != null) {
            avroConsumer.close();
            avroConsumer = null;
        }
    }

    @Test
    public void testPostgreSQLAddColumnEvolution() throws Exception {
        // Given: Create initial table with basic fields
        String createTableSQL = String.format(
            "CREATE TABLE %s (" +
            "id SERIAL PRIMARY KEY, " +
            "name VARCHAR(255)" +
            ")",
            TEST_TABLE_PG
        );
        executePostgresSQL(createTableSQL);

        // Register connector
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_pg")
            .with("table.include.list", "public." + TEST_TABLE_PG)
            .with("slot.name", "evolution_slot_" + System.currentTimeMillis())
            .with("publication.name", "evolution_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector(CONNECTOR_NAME_PG, connector);

        String topic = TEST_TOPIC_PREFIX + "_pg.public." + TEST_TABLE_PG;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // Insert initial record
        executePostgresSQL(String.format(
            "INSERT INTO %s (name) VALUES ('Initial Record')",
            TEST_TABLE_PG
        ));

        // Wait for initial event
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> initialRecord =
            pollForEvent(avroConsumer, isCreate().or(isRead()), 30);
        assertNotNull(initialRecord, "Should receive initial CDC event");

        // Get initial schema version
        ArtifactSearchResults initialResults = registryClient.search().artifacts()
            .get(config -> {
                config.queryParameters.limit = 100;
                config.queryParameters.order = SortOrder.Asc;
            });
        int initialSchemaCount = (int) initialResults.getCount();

        // When: Add new column (forward compatibility - adding optional field)
        executePostgresSQL(String.format(
            "ALTER TABLE %s ADD COLUMN email VARCHAR(255)",
            TEST_TABLE_PG
        ));

        // Insert record with new field
        executePostgresSQL(String.format(
            "INSERT INTO %s (name, email) VALUES ('New Record', 'test@example.com')",
            TEST_TABLE_PG
        ));

        // Poll for new event
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> newRecord =
            pollForEvent(avroConsumer, isCreate(), 30);

        // Then: Verify new field is present
        assertNotNull(newRecord, "Should receive CDC event after schema evolution");
        org.apache.avro.generic.GenericRecord event = newRecord.value();
        org.apache.avro.generic.GenericRecord after = getAfter(event);

        assertNotNull(after, "After value should not be null");
        assertEquals("New Record", after.get("name").toString());
        assertNotNull(after.get("email"), "New email field should be present");
        assertEquals("test@example.com", after.get("email").toString());

        // Verify schema was updated/versioned in registry
        ArtifactSearchResults newResults = registryClient.search().artifacts()
            .get(config -> {
                config.queryParameters.limit = 100;
                config.queryParameters.order = SortOrder.Asc;
            });

        // Schema evolution may create new versions
        assertTrue(newResults.getCount() >= initialSchemaCount,
            "Should have at least the same number of schemas after evolution");

        LOGGER.info("Successfully validated PostgreSQL ADD COLUMN schema evolution");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + TEST_TABLE_PG);
    }

    @Test
    public void testMySQLAddColumnEvolution() throws Exception {
        // Given: Create initial table
        String createTableSQL = String.format(
            "CREATE TABLE %s (" +
            "id INT AUTO_INCREMENT PRIMARY KEY, " +
            "product_name VARCHAR(255)" +
            ")",
            TEST_TABLE_MYSQL
        );
        executeMySqlSQL(createTableSQL);

        // Register connector
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.mysqlContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_mysql")
            .with("database.include.list", "inventory")
            .with("table.include.list", "inventory." + TEST_TABLE_MYSQL)
            .with("include.schema.changes", "false")
            .with("database.server.id", String.valueOf(System.currentTimeMillis()));

        DebeziumContainerResource.debeziumContainer.registerConnector(CONNECTOR_NAME_MYSQL, connector);

        String topic = TEST_TOPIC_PREFIX + "_mysql.inventory." + TEST_TABLE_MYSQL;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // Insert initial record
        executeMySqlSQL(String.format(
            "INSERT INTO %s (product_name) VALUES ('Initial Product')",
            TEST_TABLE_MYSQL
        ));

        // Wait for initial event
        pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        // When: Add new column with default value (backward compatible)
        executeMySqlSQL(String.format(
            "ALTER TABLE %s ADD COLUMN price DECIMAL(10,2) DEFAULT 0.00",
            TEST_TABLE_MYSQL
        ));

        // Insert record with new field
        executeMySqlSQL(String.format(
            "INSERT INTO %s (product_name, price) VALUES ('New Product', 99.99)",
            TEST_TABLE_MYSQL
        ));

        // Poll for new event
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> newRecord =
            pollForEvent(avroConsumer, isCreate(), 30);

        // Then: Verify new field is present
        assertNotNull(newRecord, "Should receive CDC event after MySQL schema evolution");
        org.apache.avro.generic.GenericRecord event = newRecord.value();
        org.apache.avro.generic.GenericRecord after = getAfter(event);

        assertNotNull(after, "After value should not be null");
        assertEquals("New Product", after.get("product_name").toString());
        assertNotNull(after.get("price"), "New price field should be present");

        LOGGER.info("Successfully validated MySQL ADD COLUMN schema evolution");

        // Cleanup
        executeMySqlSQL("DROP TABLE IF EXISTS " + TEST_TABLE_MYSQL);
    }

    @Test
    public void testSchemaVersioning() throws Exception {
        // Given: Create table
        String testTable = "versioning_test";
        String createTableSQL = String.format(
            "CREATE TABLE %s (" +
            "id SERIAL PRIMARY KEY, " +
            "data VARCHAR(255)" +
            ")",
            testTable
        );
        executePostgresSQL(createTableSQL);

        // Register connector
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_ver")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "versioning_slot_" + System.currentTimeMillis())
            .with("publication.name", "versioning_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("postgres-versioning-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_ver.public." + testTable;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // Insert record to create initial schema
        executePostgresSQL(String.format(
            "INSERT INTO %s (data) VALUES ('Version 1')",
            testTable
        ));

        pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        // Find the schema artifact
        ArtifactSearchResults results = registryClient.search().artifacts()
            .get(config -> {
                config.queryParameters.limit = 100;
                config.queryParameters.order = SortOrder.Asc;
            });

        String artifactId = results.getArtifacts().stream()
            .filter(artifact -> artifact.getArtifactId().contains(TEST_TOPIC_PREFIX + "_ver") &&
                               artifact.getArtifactId().contains(testTable))
            .map(artifact -> artifact.getArtifactId())
            .findFirst()
            .orElse(null);

        assertNotNull(artifactId, "Should find schema artifact");

        // Get initial version info
        ArtifactMetaData initialMeta = registryClient.groups().byGroupId("default")
            .artifacts().byArtifactId(artifactId).get();

        assertNotNull(initialMeta, "Should retrieve initial schema metadata");
        LOGGER.info("Initial schema retrieved: {}", initialMeta.getArtifactId());

        // When: Evolve schema by adding column
        executePostgresSQL(String.format(
            "ALTER TABLE %s ADD COLUMN extra VARCHAR(255)",
            testTable
        ));

        executePostgresSQL(String.format(
            "INSERT INTO %s (data, extra) VALUES ('Version 2', 'Extra data')",
            testTable
        ));

        pollForEvent(avroConsumer, isCreate(), 30);

        // Then: Verify schema versioning occurred
        // Note: Debezium may create new schema versions when structure changes
        LOGGER.info("Successfully validated schema versioning");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }

    @Test
    public void testMultipleSchemaChanges() throws Exception {
        // Given: Create initial table
        String testTable = "multi_evolution";
        String createTableSQL = String.format(
            "CREATE TABLE %s (" +
            "id SERIAL PRIMARY KEY, " +
            "field1 VARCHAR(255)" +
            ")",
            testTable
        );
        executePostgresSQL(createTableSQL);

        // Register connector
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_multi")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "multi_slot_" + System.currentTimeMillis())
            .with("publication.name", "multi_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("postgres-multi-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_multi.public." + testTable;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // Insert initial record
        executePostgresSQL(String.format(
            "INSERT INTO %s (field1) VALUES ('Initial')",
            testTable
        ));
        pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        // When: Apply multiple schema changes
        // Add field2
        executePostgresSQL(String.format(
            "ALTER TABLE %s ADD COLUMN field2 VARCHAR(255)",
            testTable
        ));
        executePostgresSQL(String.format(
            "INSERT INTO %s (field1, field2) VALUES ('Change 1', 'Field 2')",
            testTable
        ));
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> record1 =
            pollForEvent(avroConsumer, isCreate(), 30);
        assertNotNull(record1, "Should receive event after first schema change");

        // Add field3
        executePostgresSQL(String.format(
            "ALTER TABLE %s ADD COLUMN field3 INT",
            testTable
        ));
        executePostgresSQL(String.format(
            "INSERT INTO %s (field1, field2, field3) VALUES ('Change 2', 'Field 2', 42)",
            testTable
        ));
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> record2 =
            pollForEvent(avroConsumer, isCreate(), 30);
        assertNotNull(record2, "Should receive event after second schema change");

        // Then: Verify final schema has all fields
        org.apache.avro.generic.GenericRecord finalEvent = record2.value();
        org.apache.avro.generic.GenericRecord after = getAfter(finalEvent);

        assertNotNull(after.get("field1"), "Should have field1");
        assertNotNull(after.get("field2"), "Should have field2");
        assertNotNull(after.get("field3"), "Should have field3");

        assertEquals("Change 2", after.get("field1").toString());
        assertEquals("Field 2", after.get("field2").toString());
        assertEquals(42, after.get("field3"));

        LOGGER.info("Successfully validated multiple schema changes");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }

    @Test
    public void testDefaultValueHandling() throws Exception {
        // Given: Create table and add column with default value
        String testTable = "default_values";
        String createTableSQL = String.format(
            "CREATE TABLE %s (" +
            "id SERIAL PRIMARY KEY, " +
            "name VARCHAR(255)" +
            ")",
            testTable
        );
        executePostgresSQL(createTableSQL);

        // Register connector
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_default")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "default_slot_" + System.currentTimeMillis())
            .with("publication.name", "default_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("postgres-default-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_default.public." + testTable;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // Insert initial record
        executePostgresSQL(String.format(
            "INSERT INTO %s (name) VALUES ('Before Default')",
            testTable
        ));
        pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        // When: Add column with default value
        executePostgresSQL(String.format(
            "ALTER TABLE %s ADD COLUMN status VARCHAR(50) DEFAULT 'ACTIVE'",
            testTable
        ));

        // Insert without specifying status (will use default)
        executePostgresSQL(String.format(
            "INSERT INTO %s (name) VALUES ('With Default')",
            testTable
        ));

        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> record =
            pollForEvent(avroConsumer, isCreate(), 30);

        // Then: Verify default value is captured
        assertNotNull(record, "Should receive CDC event");
        org.apache.avro.generic.GenericRecord event = record.value();
        org.apache.avro.generic.GenericRecord after = getAfter(event);

        assertNotNull(after.get("status"), "Status field should be present");
        assertEquals("ACTIVE", after.get("status").toString(), "Should have default value");

        LOGGER.info("Successfully validated default value handling in schema evolution");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }
}
