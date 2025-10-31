package io.apicurio.tests.debezium;

import io.apicurio.registry.utils.converter.AvroConverter;
import io.apicurio.registry.utils.converter.ExtJsonConverter;
import io.apicurio.registry.utils.tests.DebeziumContainerResource;
import io.debezium.testing.testcontainers.ConnectorConfiguration;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.apicurio.tests.debezium.DebeziumTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for different Kafka Connect converters with Debezium and Apicurio Registry.
 * Tests Avro converter, JSON converter, and various converter configurations.
 */
@QuarkusIntegrationTest
@TestProfile(DebeziumIntegrationTestProfile.class)
public class DebeziumConvertersIT extends DebeziumTestBase {

    private static final String TEST_TOPIC_PREFIX = "converter_test";
    private AvroConverter<Object> avroConverter;
    private ExtJsonConverter jsonConverter;

    @AfterEach
    public void teardown() {
        if (avroConsumer != null) {
            avroConsumer.close();
            avroConsumer = null;
        }
        if (jsonConsumer != null) {
            jsonConsumer.close();
            jsonConsumer = null;
        }
    }

    @Test
    public void testAvroConverterWithDebeziumEnvelope() throws Exception {
        // Given: Create table
        String testTable = "avro_converter_test";
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, name VARCHAR(255), value INT)",
            testTable
        ));

        // Register connector
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_avro")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "avro_slot_" + System.currentTimeMillis())
            .with("publication.name", "avro_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("avro-converter-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_avro.public." + testTable;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // When: Insert data
        executePostgresSQL(String.format(
            "INSERT INTO %s (name, value) VALUES ('Avro Test', 123)",
            testTable
        ));

        // Then: Verify Avro deserialization works
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> record =
            pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        assertNotNull(record, "Should receive Avro-encoded CDC event");
        org.apache.avro.generic.GenericRecord event = record.value();

        // Verify Debezium envelope structure
        assertTrue(hasValidDebeziumStructure(event), "Should have valid Debezium structure");

        org.apache.avro.generic.GenericRecord after = getAfter(event);
        assertEquals("Avro Test", after.get("name").toString());
        assertEquals(123, after.get("value"));

        LOGGER.info("Successfully validated Avro converter with Debezium envelope");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }

    @Test
    public void testAvroConverterSchemaRegistration() throws Exception {
        // Given: Initialize Avro converter
        avroConverter = new AvroConverter<>();
        Map<String, Object> config = createConverterConfig(false);
        config.put("schemas.enable", true);
        avroConverter.configure(config, false);

        // Create a Debezium-like envelope schema
        Schema valueSchema = SchemaBuilder.struct()
            .name("Customer")
            .field("id", Schema.INT64_SCHEMA)
            .field("name", Schema.STRING_SCHEMA)
            .build();

        Schema envelopeSchema = SchemaBuilder.struct()
            .name("CustomerEnvelope")
            .optional()
            .field("before", valueSchema)
            .field("after", valueSchema)
            .field("op", Schema.STRING_SCHEMA)
            .build();

        // When: Convert data using AvroConverter
        Struct customerValue = new Struct(valueSchema)
            .put("id", 1L)
            .put("name", "John Doe");

        Struct envelope = new Struct(envelopeSchema)
            .put("before", null)
            .put("after", customerValue)
            .put("op", "c");

        byte[] serialized = avroConverter.fromConnectData("converter-test-topic", envelopeSchema, envelope);

        // Then: Verify serialization succeeded
        assertNotNull(serialized, "Should serialize envelope to Avro");
        assertTrue(serialized.length > 0, "Serialized data should not be empty");

        // Verify schema was registered
        var results = registryClient.search().artifacts()
            .get(config1 -> config1.queryParameters.limit = 100);

        boolean foundSchema = results.getArtifacts().stream()
            .anyMatch(artifact -> artifact.getArtifactId().contains("converter-test-topic"));

        assertTrue(foundSchema, "Schema should be registered in Apicurio Registry");

        LOGGER.info("Successfully validated Avro converter schema registration");
    }

    @Test
    public void testJsonConverterConfiguration() throws Exception {
        // Given: Initialize JSON converter
        jsonConverter = new ExtJsonConverter();
        Map<String, Object> config = new HashMap<>();
        config.put("apicurio.registry.url", getRegistryV3ApiUrl());
        config.put("apicurio.registry.auto-register", true);
        jsonConverter.configure(config, false);

        // Create a simple schema
        Schema testSchema = SchemaBuilder.struct()
            .name("TestRecord")
            .field("id", Schema.INT32_SCHEMA)
            .field("message", Schema.STRING_SCHEMA)
            .build();

        Struct testValue = new Struct(testSchema)
            .put("id", 42)
            .put("message", "Test message");

        // When: Convert data
        byte[] serialized = jsonConverter.fromConnectData("json-test-topic", testSchema, testValue);

        // Then: Verify serialization
        assertNotNull(serialized, "Should serialize to JSON");
        assertTrue(serialized.length > 0, "Serialized data should not be empty");

        String jsonString = new String(serialized);
        assertTrue(jsonString.contains("Test message"), "JSON should contain the message");

        LOGGER.info("Successfully validated JSON converter configuration");
    }

    @Test
    public void testConverterWithKeyAndValue() throws Exception {
        // Given: Create table
        String testTable = "key_value_test";
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id INT PRIMARY KEY, data VARCHAR(255))",
            testTable
        ));

        // Register connector with key converter configuration
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_keyval")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "keyval_slot_" + System.currentTimeMillis())
            .with("publication.name", "keyval_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("keyval-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_keyval.public." + testTable;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // When: Insert data
        executePostgresSQL(String.format(
            "INSERT INTO %s (id, data) VALUES (100, 'Test Data')",
            testTable
        ));

        // Then: Verify both key and value are properly handled
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> record =
            pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        assertNotNull(record, "Should receive CDC event");
        assertNotNull(record.key(), "Record should have a key");
        assertNotNull(record.value(), "Record should have a value");

        // Verify value structure
        org.apache.avro.generic.GenericRecord event = record.value();
        org.apache.avro.generic.GenericRecord after = getAfter(event);
        assertEquals(100, after.get("id"));
        assertEquals("Test Data", after.get("data").toString());

        LOGGER.info("Successfully validated converter with both key and value");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }

    @Test
    public void testConverterSchemaNameAdjustment() throws Exception {
        // Given: Create table with special characters that need adjustment
        String testTable = "schema_name_test";
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, \"special-field\" VARCHAR(255))",
            testTable
        ));

        // Register connector
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_naming")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "naming_slot_" + System.currentTimeMillis())
            .with("publication.name", "naming_pub_" + System.currentTimeMillis())
            .with("schema.name.adjustment.mode", "avro");

        DebeziumContainerResource.debeziumContainer.registerConnector("naming-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_naming.public." + testTable;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // When: Insert data
        executePostgresSQL(String.format(
            "INSERT INTO %s (\"special-field\") VALUES ('Special Value')",
            testTable
        ));

        // Then: Verify schema name adjustment worked
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> record =
            pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        assertNotNull(record, "Should receive CDC event despite special field names");
        org.apache.avro.generic.GenericRecord event = record.value();
        org.apache.avro.generic.GenericRecord after = getAfter(event);

        // Field name should be adjusted for Avro compatibility
        assertNotNull(after, "After value should not be null");
        assertTrue(after.hasField("special_field") || after.hasField("special-field"),
            "Field should be present with adjusted name");

        LOGGER.info("Successfully validated converter schema name adjustment");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }

    @Test
    public void testConverterWithNullValues() throws Exception {
        // Given: Create table allowing nulls
        String testTable = "null_values_test";
        executePostgresSQL(String.format(
            "CREATE TABLE %s (id SERIAL PRIMARY KEY, nullable_field VARCHAR(255))",
            testTable
        ));

        // Register connector
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(DebeziumContainerResource.postgresContainer)
            .with("topic.prefix", TEST_TOPIC_PREFIX + "_null")
            .with("table.include.list", "public." + testTable)
            .with("slot.name", "null_slot_" + System.currentTimeMillis())
            .with("publication.name", "null_pub_" + System.currentTimeMillis());

        DebeziumContainerResource.debeziumContainer.registerConnector("null-connector", connector);

        String topic = TEST_TOPIC_PREFIX + "_null.public." + testTable;
        avroConsumer = createAvroConsumer();
        avroConsumer.subscribe(Collections.singletonList(topic));

        // When: Insert record with null value
        executePostgresSQL(String.format(
            "INSERT INTO %s (nullable_field) VALUES (NULL)",
            testTable
        ));

        // Then: Verify null handling
        ConsumerRecord<String, org.apache.avro.generic.GenericRecord> record =
            pollForEvent(avroConsumer, isCreate().or(isRead()), 30);

        assertNotNull(record, "Should receive CDC event with null values");
        org.apache.avro.generic.GenericRecord event = record.value();
        org.apache.avro.generic.GenericRecord after = getAfter(event);

        assertNotNull(after, "After value should not be null");
        // The nullable field should be null
        Object nullableValue = after.get("nullable_field");
        assertNull(nullableValue, "Nullable field should be null");

        LOGGER.info("Successfully validated converter with null values");

        // Cleanup
        executePostgresSQL("DROP TABLE IF EXISTS " + testTable);
    }
}
