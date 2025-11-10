package io.apicurio.tests.serdes.apicurio.debezium;

import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.debezium.testing.testcontainers.ConnectorConfiguration;
import io.debezium.testing.testcontainers.DebeziumContainer;
import io.restassured.response.Response;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;

/**
 * Common base class for all Debezium CDC integration tests with Apicurio Registry.
 * Contains shared logic for Kafka consumers, connector management, schema operations, etc.
 * Subclasses provide database-specific implementations (PostgreSQL, MySQL).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class DebeziumAvroBaseIT extends ApicurioRegistryBaseIT {

    private static final Logger log = LoggerFactory.getLogger(DebeziumAvroBaseIT.class);

    protected KafkaConsumer<byte[], byte[]> consumer;
    protected Connection dbConnection;
    protected List<String> createdTables = new ArrayList<>();
    protected String currentConnectorName;
    protected static final AtomicInteger connectorCounter = new AtomicInteger(0);

    /**
     * Returns the registry URL to use for connector configuration.
     */
    protected abstract String getRegistryUrl();

    /**
     * Returns the Debezium container to use for this test.
     */
    protected abstract DebeziumContainer getDebeziumContainer();

    /**
     * Deserializes Avro-encoded bytes to GenericRecord.
     */
    protected abstract GenericRecord deserializeAvroValue(byte[] bytes) throws Exception;

    /**
     * Creates a database connection.
     */
    protected abstract Connection createDatabaseConnection() throws SQLException;

    /**
     * Returns the database type (e.g., "postgresql", "mysql").
     */
    protected abstract String getDatabaseType();

    /**
     * Registers a Debezium connector with Apicurio converters and database-specific configuration.
     */
    protected abstract void registerDebeziumConnectorWithApicurioConverters(
            String connectorName, String topicPrefix, String tableIncludeList);

    @BeforeAll
    public void setup() throws Exception {
        log.info("Debezium {} Avro Integration Test setup complete", getDatabaseType().toUpperCase());
        log.info("Registry URL (host): {}", getRegistryV3ApiUrl());
        log.info("Registry Base URL: {}", getRegistryBaseUrl());
        log.info("Kafka Bootstrap Servers: {}", System.getProperty("bootstrap.servers"));

        String hostRegistryUrl = getRegistryUrl();
        String containerRegistryUrl = getContainerAccessibleRegistryUrl();

        log.info("=== Registry URL Validation ===");
        log.info("Host registry URL: {}", hostRegistryUrl);
        log.info("Container-accessible registry URL: {}", containerRegistryUrl);

        try {
            var info = registryClient.system().info().get();
            log.info("✓ Registry is accessible from test host, version: {}", info.getVersion());
        }
        catch (Exception e) {
            String errorMsg = String.format(
                    "FATAL: Registry not accessible from test host at %s. Error: %s",
                    hostRegistryUrl, e.getMessage());
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    @BeforeEach
    public void beforeEachTest() throws InterruptedException {
        currentConnectorName = null;

        if (consumer != null) {
            try {
                consumer.close();
            }
            catch (Exception e) {
                log.warn("Failed to close previous consumer: {}", e.getMessage());
            }
        }
        consumer = createKafkaConsumer();
        log.info("Created fresh Kafka consumer for test");
    }

    @AfterEach
    public void cleanup() throws Exception {
        Exception cleanupException = null;

        if (currentConnectorName != null) {
            try {
                log.info("Deleting Debezium connector: {}", currentConnectorName);
                getDebeziumContainer().deleteConnector(currentConnectorName);
                Thread.sleep(5000);
                log.info("Successfully deleted connector: {}", currentConnectorName);
            }
            catch (Exception e) {
                log.error("Failed to delete connector {}: {}", currentConnectorName, e.getMessage(), e);
                cleanupException = e;
            }
        }

        if (consumer != null) {
            try {
                consumer.unsubscribe();
                log.info("Unsubscribed consumer from all topics");
            }
            catch (Exception e) {
                log.warn("Failed to unsubscribe consumer: {}", e.getMessage());
            }
        }

        if (dbConnection != null) {
            for (String tableName : createdTables) {
                try {
                    dropTable(tableName);
                    log.info("Dropped table: {}", tableName);
                }
                catch (SQLException e) {
                    log.warn("Failed to drop table {}: {}", tableName, e.getMessage());
                }
            }
            createdTables.clear();
        }

        if (cleanupException != null) {
            throw new RuntimeException("Test cleanup failed", cleanupException);
        }
    }

    /**
     * Drops a table. Database-specific syntax handled by subclasses.
     */
    protected abstract void dropTable(String tableName) throws SQLException;

    protected Connection getDatabaseConnection() throws SQLException {
        if (null == dbConnection || dbConnection.isClosed() || !dbConnection.isValid(2)) {
            if (dbConnection != null && !dbConnection.isClosed()) {
                try {
                    dbConnection.close();
                } catch (SQLException e) {
                    log.warn("Error closing stale connection: {}", e.getMessage());
                }
            }
            dbConnection = createDatabaseConnection();
            log.debug("Created new database connection");
        }
        return dbConnection;
    }

    // ==================== Common Kafka Consumer Methods ====================

    protected KafkaConsumer<byte[], byte[]> createKafkaConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("bootstrap.servers"));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");
        return new KafkaConsumer<>(props, new ByteArrayDeserializer(), new ByteArrayDeserializer());
    }

    protected List<GenericRecord> consumeAvroEvents(String topic, int expectedCount, Duration timeout)
            throws Exception {
        List<GenericRecord> records = new ArrayList<>();

        Unreliables.retryUntilTrue((int) timeout.getSeconds(), TimeUnit.SECONDS, () -> {
            consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                try {
                    if (record.value() == null || record.value().length < 5) {
                        log.debug("Skipping tombstone message from {}", topic);
                        return;
                    }

                    GenericRecord avroRecord = deserializeAvroValue(record.value());
                    records.add(avroRecord);
                    log.debug("Consumed Avro event from {}: {}", topic, avroRecord);
                }
                catch (Exception e) {
                    log.error("Failed to deserialize Avro record", e);
                }
            });
            return records.size() >= expectedCount;
        });

        return records;
    }

    protected void waitForConsumerReady(Duration timeout) throws Exception {
        log.info("Waiting for consumer to complete partition assignment...");

        Unreliables.retryUntilTrue((int) timeout.getSeconds(), TimeUnit.SECONDS, () -> {
            consumer.poll(Duration.ofMillis(100));
            boolean hasAssignment = !consumer.assignment().isEmpty();

            if (hasAssignment) {
                log.info("Consumer partition assignment complete: {}", consumer.assignment());
            }
            else {
                log.debug("Consumer waiting for partition assignment...");
            }

            return hasAssignment;
        });
    }

    // ==================== Common Connector Management Methods ====================

    protected void waitForConnectorReady(String connectorName, Duration timeout) throws Exception {
        log.info("Waiting for connector {} to be ready...", connectorName);

        String connectUrl = "http://" + getDebeziumContainer().getHost() + ":" +
                getDebeziumContainer().getMappedPort(8083);

        Unreliables.retryUntilTrue((int) timeout.getSeconds(), TimeUnit.SECONDS, () -> {
            try {
                String statusUrl = connectUrl + "/connectors/" + connectorName + "/status";
                Response response = given()
                        .when()
                        .get(statusUrl)
                        .then()
                        .extract()
                        .response();

                if (response.getStatusCode() == 200) {
                    String responseBody = response.getBody().asString();
                    boolean isRunning = responseBody.contains("\"state\":\"RUNNING\"");
                    if (!isRunning) {
                        log.debug("Connector {} status: {}", connectorName, responseBody);

                        if (responseBody.contains("\"state\":\"FAILED\"")) {
                            log.error("Connector {} is in FAILED state: {}", connectorName, responseBody);
                            throw new RuntimeException(
                                    String.format("Connector %s failed to start. Status: %s",
                                            connectorName, responseBody));
                        }
                    }
                    else {
                        log.info("Connector {} is RUNNING", connectorName);
                    }
                    return isRunning;
                }
                return false;
            }
            catch (RuntimeException e) {
                throw e;
            }
            catch (Exception e) {
                log.debug("Connector {} not ready yet: {}", connectorName, e.getMessage());
                return false;
            }
        });
    }

    // ==================== Common Schema Registry Methods ====================

    protected void waitForSchemaInRegistry(String artifactId, Duration timeout) throws Exception {
        try {
            Unreliables.retryUntilTrue((int) timeout.getSeconds(), TimeUnit.SECONDS, () -> {
                try {
                    ArtifactMetaData metadata = registryClient.groups().byGroupId("default")
                            .artifacts().byArtifactId(artifactId)
                            .get();
                    log.info("Schema {} found in registry: type={}",
                            artifactId, metadata.getArtifactType());
                    return true;
                }
                catch (Exception e) {
                    log.debug("Schema {} not yet in registry: {}", artifactId, e.getMessage());
                    return false;
                }
            });
        }
        catch (Exception e) {
            String errorMsg = String.format(
                    "Schema %s was not registered within %d seconds.",
                    artifactId, timeout.getSeconds());
            log.error(errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }

    // ==================== Common Registry URL Transformation ====================

    protected String getContainerAccessibleRegistryUrl() {
        String registryUrl = getRegistryUrl();

        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            log.info("Cluster mode detected - Debezium is running in Kubernetes");

            try {
                io.fabric8.kubernetes.client.KubernetesClient kubernetesClient =
                    io.apicurio.deployment.RegistryDeploymentManager.kubernetesClient;

                io.fabric8.kubernetes.api.model.Service registryService = kubernetesClient.services()
                    .inNamespace(io.apicurio.deployment.KubernetesTestResources.TEST_NAMESPACE)
                    .withName(io.apicurio.deployment.KubernetesTestResources.APPLICATION_SERVICE)
                    .get();

                if (registryService != null) {
                    String clusterIP = registryService.getSpec().getClusterIP();
                    java.net.URI uri = new java.net.URI(registryUrl);
                    String path = uri.getPath() != null ? uri.getPath() : "";
                    String clusterUrl = "http://" + clusterIP + ":8080" + path;
                    log.info("Using registry ClusterIP for in-cluster Debezium: {} (original: {})",
                             clusterUrl, registryUrl);
                    return clusterUrl;
                } else {
                    log.warn("Registry service not found, using original URL: {}", registryUrl);
                    return registryUrl;
                }
            } catch (Exception e) {
                log.error("Failed to get registry ClusterIP, using original URL: {}", e.getMessage());
                return registryUrl;
            }
        }

        boolean isCI = System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null;
        boolean isLinux = System.getProperty("os.name", "").toLowerCase().contains("linux");
        boolean shouldUseHostNetwork = isCI || isLinux;

        boolean isClusterIP = registryUrl.contains("://10.") ||
                registryUrl.contains("://172.") ||
                registryUrl.contains("://192.168.");

        if (isClusterIP) {
            return registryUrl;
        } else if (registryUrl.contains("localhost") || registryUrl.contains("127.0.0.1")) {
            if (shouldUseHostNetwork) {
                log.info("Using localhost directly (host network mode): {}", registryUrl);
                return registryUrl;
            }
            else {
                String transformedUrl = registryUrl
                        .replace("localhost", "host.testcontainers.internal")
                        .replace("127.0.0.1", "host.testcontainers.internal");
                log.info("Transforming localhost to host.testcontainers.internal: {} -> {}",
                        registryUrl, transformedUrl);
                return transformedUrl;
            }
        }
        else {
            log.info("Using registry URL as-is: {}", registryUrl);
            return registryUrl;
        }
    }

    /**
     * Builds base connector configuration with Apicurio converters.
     * Subclasses add database-specific properties.
     */
    protected ConnectorConfiguration buildBaseConnectorConfiguration(String topicPrefix, String tableIncludeList) {
        String dockerAccessibleRegistryUrl = getContainerAccessibleRegistryUrl();

        return ConnectorConfiguration.create()
                .with("topic.prefix", topicPrefix)
                .with("table.include.list", tableIncludeList)
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
    }

    // ==================== Common Helper Methods ====================

    protected void createTable(String tableName, String ddl) throws SQLException {
        try (Statement stmt = getDatabaseConnection().createStatement()) {
            stmt.execute(ddl);
            createdTables.add(tableName);
            log.info("Created table: {}", tableName);
        }
    }

    protected void executeUpdate(String sql) throws SQLException {
        try (Statement stmt = getDatabaseConnection().createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    protected void insertCustomer(String tableName, String name, String email) throws SQLException {
        try (PreparedStatement stmt = getDatabaseConnection().prepareStatement(
                "INSERT INTO " + tableName + " (name, email) VALUES (?, ?)")) {
            stmt.setString(1, name);
            stmt.setString(2, email);
            stmt.executeUpdate();
        }
    }

    // ==================== Common Utility Methods ====================

    protected java.math.BigDecimal decodeAvroDecimal(Object decimalValue, int scale) {
        if (decimalValue == null) {
            return null;
        }

        ByteBuffer buffer;
        if (decimalValue instanceof ByteBuffer) {
            buffer = (ByteBuffer) decimalValue;
        }
        else if (decimalValue instanceof byte[]) {
            buffer = ByteBuffer.wrap((byte[]) decimalValue);
        }
        else {
            throw new IllegalArgumentException("Expected ByteBuffer or byte[], got: " + decimalValue.getClass());
        }

        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        java.math.BigInteger unscaled = new java.math.BigInteger(bytes);

        return new java.math.BigDecimal(unscaled, scale);
    }
}
