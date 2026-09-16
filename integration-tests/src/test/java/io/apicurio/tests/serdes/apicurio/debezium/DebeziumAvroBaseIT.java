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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.rnorth.ducttape.TimeoutException;
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
import java.util.concurrent.Callable;
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

    // Class-level connector that is shared across all test methods in a test class.
    // Instance fields, NOT static: @TestInstance(Lifecycle.PER_CLASS) (see
    // ApicurioRegistryBaseIT) gives each test class exactly one instance, reused across all
    // of its own @Test methods — but junit-platform.properties runs distinct test classes
    // concurrently in the same JVM (mode.classes.default=concurrent). Since Java static
    // fields have ONE identity shared by every subclass instance regardless of which
    // concrete class it belongs to, declaring these static meant every concurrently-running
    // Debezium test class (MySQL/PostgreSQL x Integration/LocalConverters, 4 total) stomped
    // on the same connector name/topic prefix/table prefix throughout its entire lifetime,
    // not just at setup — producing exactly the symptoms seen in CI: "connector already
    // exists" 409s, tables colliding ("column already exists" / duplicate key), and consumers
    // reading another class's events (wrong CDC counts, rules "already exists"). Instance
    // fields give each concurrently-running class its own isolated copy.
    protected String sharedConnectorName;
    protected String sharedTopicPrefix;
    protected String tablePrefix;
    // This class's own stable numeric id, captured once in setup(). Subclasses that need a
    // unique-but-stable derived number (e.g. MySQL's database.server.id) must use this, not a
    // fresh connectorCounter.get() — the counter keeps moving as sibling classes run
    // concurrently, so re-reading it later can race and collide with another class's value.
    protected int classId;
    // Global across all Debezium test classes, intentionally: this is what guarantees
    // sharedConnectorName/classId are unique across concurrently-running classes in the first
    // place. Safe to stay static/shared — only ever incremented, never read-then-cached
    // elsewhere except into the per-instance classId field above.
    protected static final AtomicInteger connectorCounter = new AtomicInteger(0);

    // Kafka Connect connector registration/deletion is not safe to run concurrently across
    // test classes (POST /connectors returns 409 while a previous register is still settling),
    // and test classes run in parallel in CI. Serialize the register/delete window.
    private static final Object CONNECTOR_LOCK = new Object();

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
        log.info("Debezium {} Avro Integration Test setup starting", getDatabaseType().toUpperCase());
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

        // Create a single shared connector for this test class that watches all tables
        classId = connectorCounter.incrementAndGet();
        sharedConnectorName = "connector-" + classId;
        sharedTopicPrefix = "test" + classId;
        tablePrefix = "tbl" + classId + "_";

        log.info("Creating shared connector {} for all tests in this class (table prefix: {})",
                sharedConnectorName, tablePrefix);

        // Register connector to watch all tables in the schema
        String tablePattern = getTableIncludePattern();
        synchronized (CONNECTOR_LOCK) {
            registerDebeziumConnectorWithApicurioConverters(sharedConnectorName, sharedTopicPrefix, tablePattern);
            // Wait for connector to be ready with a longer timeout for initial startup
            // Under class-level parallel execution the shared Kafka Connect can take >30 s to
            // bring a connector to RUNNING; a tighter budget makes the second class time out
            // while the first is still booting. 120 s matches the other CI-visible timeouts.
            waitForConnectorReady(sharedConnectorName, Duration.ofSeconds(120));
        }

        log.info("Shared connector {} is ready and watching pattern: {}", sharedConnectorName, tablePattern);
    }

    /**
     * Returns the table include pattern for watching all tables.
     * Database-specific implementations can override this.
     */
    protected String getTableIncludePattern() {
        return "public.*";
    }

    /**
     * Returns a unique table name with prefix to avoid conflicts when sharing database containers.
     * Each test class gets a unique prefix based on its connector counter.
     */
    protected String getTableName(String baseName) {
        return tablePrefix + baseName;
    }

    /**
     * Returns the Kafka topic name for a given table name using the shared topic prefix.
     * Database-specific implementations can override this for custom topic naming.
     */
    protected String getTopicNameForTable(String tableName) {
        return sharedTopicPrefix + ".public." + tableName;
    }

    @AfterAll
    public void teardown() throws Exception {
        if (sharedConnectorName != null) {
            try {
                log.info("Deleting shared connector: {}", sharedConnectorName);
                synchronized (CONNECTOR_LOCK) {
                    getDebeziumContainer().deleteConnector(sharedConnectorName);
                }
                log.info("Successfully deleted shared connector: {}", sharedConnectorName);
            }
            catch (Exception e) {
                log.error("Failed to delete shared connector {}: {}", sharedConnectorName, e.getMessage(), e);
            }
        }
    }

    @BeforeEach
    public void beforeEachTest() throws InterruptedException {
        // Close and recreate consumer for test isolation
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

        // No longer delete connector after each test - it's shared across all tests in the class

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

    /**
     * Consumes Avro events from a topic with retry logic.
     *
     * IMPORTANT: Timeout considerations for CI/CD environments:
     * - When a new table is created, Debezium needs time to detect it and start capturing changes
     * - This detection delay can be 5-10 seconds in CI environments (slower, resource-constrained)
     * - Recommended timeouts: 15-20 seconds for first insert after table creation, 10-15 seconds otherwise
     * - Schema evolution (ALTER TABLE) may also require additional time for Debezium to process
     */
    protected List<GenericRecord> consumeAvroEvents(String topic, int expectedCount, Duration timeout)
            throws Exception {
        List<GenericRecord> records = new ArrayList<>();

        pollUntilTrue(timeout, () -> {
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

    /**
     * Like {@link #consumeAvroEvents(String, int, Duration)}, but only counts records whose
     * {@code after.data} matches {@code expectedData}. Connector snapshots of a freshly created
     * table (or inserts from a concurrently running test class sharing the same Kafka) otherwise
     * inflate a bare record count and make exact-count assertions flaky.
     */
    protected List<GenericRecord> consumeAvroEvents(String topic, int expectedCount, Duration timeout,
            String expectedData) throws Exception {
        List<GenericRecord> records = new ArrayList<>();

        pollUntilTrue(timeout, () -> {
            consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                try {
                    if (record.value() == null || record.value().length < 5) {
                        log.debug("Skipping tombstone message from {}", topic);
                        return;
                    }
                    GenericRecord avroRecord = deserializeAvroValue(record.value());
                    Object afterField = avroRecord.get("after");
                    if (afterField instanceof GenericRecord after
                            && after.get("data") != null
                            && expectedData.equals(after.get("data").toString())) {
                        records.add(avroRecord);
                        log.debug("Consumed matching Avro event from {}: {}", topic, avroRecord);
                    } else {
                        log.debug("Ignoring non-matching event from {}: {}", topic, avroRecord);
                    }
                }
                catch (Exception e) {
                    log.error("Failed to deserialize Avro record", e);
                }
            });
            return records.size() >= expectedCount;
        });

        return records;
    }

    /**
     * Repeatedly calls {@code check} on the CALLING thread until it returns true or the timeout
     * elapses, then throws the same {@link TimeoutException} type Unreliables.retryUntilTrue
     * would have thrown.
     *
     * Deliberately does NOT delegate to Unreliables.retryUntilTrue/retryUntilSuccess: those
     * submit the retry loop to a separate, shared daemon thread pool
     * (org.rnorth.ducttape.timeouts.Timeouts#getWithTimeout) and, on timeout, simply give up
     * waiting on the Future WITHOUT cancelling it — the submitted loop keeps running in the
     * background until its own next doContinue check. Every caller here polls this class's
     * shared, mutable, non-thread-safe `consumer` field, which the next test's @BeforeEach
     * reassigns (after closing the old one). A timed-out test's leaked background thread can
     * still be mid-poll() when that happens, and end up calling poll()/close() concurrently on
     * whichever consumer `this.consumer` now refers to — producing "KafkaConsumer is not safe
     * for multi-threaded access" and cascading failures through every later test in the class
     * (observed in CI: one timeout in test N caused all of tests N+1..11 to fail). Polling on
     * the calling thread has no such leak: once the deadline is hit, nothing from this call
     * touches the consumer again.
     */
    protected void pollUntilTrue(Duration timeout, Callable<Boolean> check) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        Exception lastException = null;
        while (System.nanoTime() < deadlineNanos) {
            try {
                if (check.call()) {
                    return;
                }
                lastException = null;
            }
            catch (Exception e) {
                lastException = e;
            }
        }
        if (lastException != null) {
            throw new TimeoutException("Timeout waiting for result with exception", lastException);
        }
        throw new TimeoutException(new RuntimeException("Not ready yet"));
    }

    protected void waitForConsumerReady(Duration timeout) throws Exception {
        log.info("Waiting for consumer to complete partition assignment...");

        pollUntilTrue(timeout, () -> {
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

    protected void waitForConnectorReady(String connectorName, Duration timeout) throws Exception {
        String connectUrl = "http://" + getDebeziumContainer().getHost() + ":" +
                getDebeziumContainer().getMappedPort(8083);

        log.info("Waiting for connector {} to be ready at {}...", connectorName, connectUrl);

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
                else {
                    log.warn("Failed to get connector status: HTTP {} - {}",
                            response.getStatusCode(), response.getBody().asString());
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
