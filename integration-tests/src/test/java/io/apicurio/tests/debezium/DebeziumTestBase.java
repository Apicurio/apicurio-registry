package io.apicurio.tests.debezium;

import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.Constants;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.apicurio.registry.utils.tests.DebeziumContainerResource.mysqlContainer;
import static io.apicurio.registry.utils.tests.DebeziumContainerResource.postgresContainer;
import static org.awaitility.Awaitility.await;

/**
 * Base class for Debezium integration tests providing common functionality for:
 * - Kafka consumer/producer setup
 * - Database connection helpers
 * - CDC event validation utilities
 * - Common test patterns
 */
@Tag(Constants.SERDES)
@Tag(ApicurioTestTags.SLOW)
public abstract class DebeziumTestBase extends ApicurioRegistryBaseIT {

    protected static final Logger LOGGER = LoggerFactory.getLogger(DebeziumTestBase.class);

    protected KafkaConsumer<String, GenericRecord> avroConsumer;
    protected KafkaConsumer<String, Object> jsonConsumer;
    protected KafkaProducer<String, GenericRecord> avroProducer;

    /**
     * Creates a Kafka consumer configured for Avro deserialization with Apicurio Registry
     */
    protected KafkaConsumer<String, GenericRecord> createAvroConsumer() {
        return createAvroConsumer(UUID.randomUUID().toString());
    }

    /**
     * Creates a Kafka consumer with a specific group ID for Avro deserialization
     */
    protected KafkaConsumer<String, GenericRecord> createAvroConsumer(String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("bootstrap.servers"));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, AvroKafkaDeserializer.class.getName());
        props.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
        props.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, Boolean.TRUE);

        return new KafkaConsumer<>(props);
    }

    /**
     * Creates a Kafka consumer configured for JSON deserialization with Apicurio Registry
     */
    protected KafkaConsumer<String, Object> createJsonConsumer() {
        return createJsonConsumer(UUID.randomUUID().toString());
    }

    /**
     * Creates a Kafka consumer with a specific group ID for JSON deserialization
     */
    protected KafkaConsumer<String, Object> createJsonConsumer(String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("bootstrap.servers"));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        return new KafkaConsumer<>(props);
    }

    /**
     * Creates a Kafka producer configured for Avro serialization with Apicurio Registry
     */
    protected KafkaProducer<String, GenericRecord> createAvroProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("bootstrap.servers"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());
        props.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
        props.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, Boolean.TRUE);

        return new KafkaProducer<>(props);
    }

    /**
     * Polls for a CDC event matching the given predicate with a timeout
     *
     * @param consumer The Kafka consumer to poll
     * @param predicate The predicate to match the event
     * @param timeoutSeconds Maximum time to wait for the event
     * @return The matching ConsumerRecord or null if not found
     */
    protected ConsumerRecord<String, GenericRecord> pollForEvent(
            KafkaConsumer<String, GenericRecord> consumer,
            java.util.function.Predicate<ConsumerRecord<String, GenericRecord>> predicate,
            int timeoutSeconds) {

        final ConsumerRecord<String, GenericRecord>[] result = new ConsumerRecord[1];

        await().atMost(timeoutSeconds, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(() -> {
                var records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, GenericRecord> record : records) {
                    LOGGER.debug("Received CDC event: topic={}, partition={}, offset={}",
                        record.topic(), record.partition(), record.offset());
                    if (predicate.test(record)) {
                        result[0] = record;
                        return true;
                    }
                }
                return false;
            });

        return result[0];
    }

    /**
     * Wait for at least one CDC event on the given topic
     */
    protected void waitForCDCEvent(KafkaConsumer<String, GenericRecord> consumer, String topic, int timeoutSeconds) {
        await().atMost(timeoutSeconds, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(() -> {
                var records = consumer.poll(Duration.ofMillis(100));
                return records.count() > 0;
            });
    }

    /**
     * Gets a PostgreSQL database connection
     */
    protected Connection getPostgresConnection() throws SQLException {
        return getPostgresConnection(postgresContainer);
    }

    /**
     * Gets a PostgreSQL database connection for a specific container
     */
    protected Connection getPostgresConnection(PostgreSQLContainer<?> container) throws SQLException {
        return DriverManager.getConnection(
            container.getJdbcUrl(),
            container.getUsername(),
            container.getPassword()
        );
    }

    /**
     * Gets a MySQL database connection
     */
    protected Connection getMySqlConnection() throws SQLException {
        return getMySqlConnection(mysqlContainer);
    }

    /**
     * Gets a MySQL database connection for a specific container
     */
    protected Connection getMySqlConnection(MySQLContainer<?> container) throws SQLException {
        return DriverManager.getConnection(
            container.getJdbcUrl(),
            container.getUsername(),
            container.getPassword()
        );
    }

    /**
     * Executes a SQL statement on PostgreSQL
     */
    protected void executePostgresSQL(String sql) throws SQLException {
        try (Connection conn = getPostgresConnection();
             Statement stmt = conn.createStatement()) {
            LOGGER.debug("Executing PostgreSQL: {}", sql);
            stmt.execute(sql);
        }
    }

    /**
     * Executes a SQL statement on MySQL
     */
    protected void executeMySqlSQL(String sql) throws SQLException {
        try (Connection conn = getMySqlConnection();
             Statement stmt = conn.createStatement()) {
            LOGGER.debug("Executing MySQL: {}", sql);
            stmt.execute(sql);
        }
    }

    /**
     * Helper to extract the "op" field from a Debezium CDC event (c=create, u=update, d=delete, r=read)
     */
    protected String getOperationType(GenericRecord event) {
        Object op = event.get("op");
        return op != null ? op.toString() : null;
    }

    /**
     * Helper to extract the "before" field from a Debezium CDC event
     */
    protected GenericRecord getBefore(GenericRecord event) {
        return (GenericRecord) event.get("before");
    }

    /**
     * Helper to extract the "after" field from a Debezium CDC event
     */
    protected GenericRecord getAfter(GenericRecord event) {
        return (GenericRecord) event.get("after");
    }

    /**
     * Helper to extract the "source" field from a Debezium CDC event
     */
    protected GenericRecord getSource(GenericRecord event) {
        return (GenericRecord) event.get("source");
    }

    /**
     * Creates configuration map for Apicurio converters
     */
    protected Map<String, Object> createConverterConfig(boolean isKey) {
        Map<String, Object> config = new HashMap<>();
        config.put("apicurio.registry.url", getRegistryV3ApiUrl());
        config.put("apicurio.registry.auto-register", true);
        config.put("apicurio.registry.find-latest", true);
        return config;
    }

    @AfterAll
    public void cleanup() {
        if (avroConsumer != null) {
            avroConsumer.close();
        }
        if (jsonConsumer != null) {
            jsonConsumer.close();
        }
        if (avroProducer != null) {
            avroProducer.close();
        }
    }
}
