package io.apicurio.tests.serdes.apicurio.debezium;

import io.debezium.testing.testcontainers.DebeziumContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Shared infrastructure for all Debezium integration tests.
 * This class manages a single set of containers (Kafka, MySQL, PostgreSQL, Debezium)
 * that are shared across all test classes to improve CI performance.
 *
 * Containers are started once when first needed and reused across all test classes.
 * They are stopped only when the JVM exits via shutdown hooks.
 */
public class SharedDebeziumInfrastructure {

    private static final Logger log = LoggerFactory.getLogger(SharedDebeziumInfrastructure.class);

    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean mysqlStarted = new AtomicBoolean(false);
    private static final AtomicBoolean postgresStarted = new AtomicBoolean(false);

    protected static final Network network = Network.newNetwork();

    // Shared containers
    public static KafkaContainer kafkaContainer;
    public static MySQLContainer<?> mysqlContainer;
    public static PostgreSQLContainer<?> postgresContainer;
    public static DebeziumContainer debeziumContainerMysql;
    public static DebeziumContainer debeziumContainerPostgres;

    /**
     * Initializes the shared Kafka container if not already initialized.
     * This must be called before starting any database-specific infrastructure.
     */
    public static synchronized void initializeKafka() {
        if (initialized.get()) {
            log.info("Kafka already initialized, skipping");
            return;
        }

        log.info("=== Initializing Shared Debezium Test Infrastructure ===");

        kafkaContainer = createKafkaContainer();
        kafkaContainer.start();
        System.setProperty("bootstrap.servers", kafkaContainer.getBootstrapServers());

        log.info("Kafka container started: {}", kafkaContainer.getBootstrapServers());

        // Expose registry port for container access
        String testPort = System.getProperty("quarkus.http.test-port", "8080");
        try {
            int port = Integer.parseInt(testPort);
            if (port > 0) {
                Testcontainers.exposeHostPorts(port);
                log.info("Exposed registry port {} to containers", port);
            }
        } catch (NumberFormatException e) {
            log.warn("Could not parse test port '{}'", testPort);
        }

        // Register shutdown hook to stop containers when JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("=== Shutting down Shared Debezium Test Infrastructure ===");
            stopAll();
        }));

        initialized.set(true);
        log.info("=== Shared infrastructure initialization complete ===");
    }

    /**
     * Initializes MySQL-specific infrastructure (MySQL + Debezium for MySQL).
     */
    public static synchronized void initializeMySQLInfrastructure() {
        if (mysqlStarted.get()) {
            log.info("MySQL infrastructure already started, skipping");
            return;
        }

        log.info("=== Initializing MySQL Infrastructure ===");

        // Ensure Kafka is initialized first
        initializeKafka();

        mysqlContainer = createMySQLContainer();
        debeziumContainerMysql = createDebeziumContainer(kafkaContainer, "mysql");

        // Start MySQL and its Debezium container
        Startables.deepStart(Stream.of(mysqlContainer, debeziumContainerMysql)).join();

        log.info("MySQL infrastructure started successfully");
        log.info("  MySQL JDBC URL: {}", mysqlContainer.getJdbcUrl());
        log.info("  Debezium Connect: http://{}:{}",
                debeziumContainerMysql.getHost(),
                debeziumContainerMysql.getMappedPort(8083));

        mysqlStarted.set(true);
    }

    /**
     * Initializes PostgreSQL-specific infrastructure (PostgreSQL + Debezium for PostgreSQL).
     */
    public static synchronized void initializePostgreSQLInfrastructure() {
        if (postgresStarted.get()) {
            log.info("PostgreSQL infrastructure already started, skipping");
            return;
        }

        log.info("=== Initializing PostgreSQL Infrastructure ===");

        // Ensure Kafka is initialized first
        initializeKafka();

        postgresContainer = createPostgreSQLContainer();
        debeziumContainerPostgres = createDebeziumContainer(kafkaContainer, "postgres");

        // Start PostgreSQL and its Debezium container
        Startables.deepStart(Stream.of(postgresContainer, debeziumContainerPostgres)).join();

        log.info("PostgreSQL infrastructure started successfully");
        log.info("  PostgreSQL JDBC URL: {}", postgresContainer.getJdbcUrl());
        log.info("  Debezium Connect: http://{}:{}",
                debeziumContainerPostgres.getHost(),
                debeziumContainerPostgres.getMappedPort(8083));

        postgresStarted.set(true);
    }

    /**
     * Stops all shared containers.
     */
    private static synchronized void stopAll() {
        if (debeziumContainerMysql != null && debeziumContainerMysql.isRunning()) {
            log.info("Stopping Debezium MySQL container");
            debeziumContainerMysql.stop();
        }

        if (debeziumContainerPostgres != null && debeziumContainerPostgres.isRunning()) {
            log.info("Stopping Debezium PostgreSQL container");
            debeziumContainerPostgres.stop();
        }

        if (mysqlContainer != null && mysqlContainer.isRunning()) {
            log.info("Stopping MySQL container");
            mysqlContainer.stop();
        }

        if (postgresContainer != null && postgresContainer.isRunning()) {
            log.info("Stopping PostgreSQL container");
            postgresContainer.stop();
        }

        if (kafkaContainer != null && kafkaContainer.isRunning()) {
            log.info("Stopping Kafka container");
            kafkaContainer.stop();
        }

        log.info("All shared containers stopped");
    }

    /**
     * Detects if running in a CI environment or on Linux.
     * Host network mode works on Linux but not on Docker Desktop (Mac/Windows).
     */
    protected static boolean shouldUseHostNetwork() {
        boolean isCI = System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null;
        boolean isLinux = System.getProperty("os.name", "").toLowerCase().contains("linux");
        return isCI || isLinux;
    }

    /**
     * Creates Kafka container with appropriate network configuration.
     */
    private static KafkaContainer createKafkaContainer() {
        KafkaContainer container = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.2.10"))
                .withNetwork(network)
                .withKraft();

        if (shouldUseHostNetwork()) {
            log.info("Using host network mode for Kafka container (Linux/CI environment)");
            container.withNetworkMode("host");
        } else {
            log.info("Using bridge network mode for Kafka container");
        }

        return container;
    }

    /**
     * Creates MySQL container with appropriate network configuration.
     */
    private static MySQLContainer<?> createMySQLContainer() {
        MySQLContainer<?> container = new MySQLContainer<>(
                DockerImageName.parse("quay.io/debezium/example-mysql:2.5").asCompatibleSubstituteFor("mysql"))
                .withDatabaseName("registry")
                .withUsername("root")
                .withPassword("debezium")
                .withUrlParam("allowPublicKeyRetrieval", "true")
                .withUrlParam("useSSL", "false")
                .withUrlParam("connectTimeout", "10000")
                .withUrlParam("socketTimeout", "10000")
                .withUrlParam("autoReconnect", "true");

        if (shouldUseHostNetwork()) {
            log.info("Using host network mode for MySQL container (Linux/CI environment)");
            container.withNetworkMode("host");
            container.withStartupTimeout(java.time.Duration.ofSeconds(60))
                    .waitingFor(new org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy()
                            .withRegEx(".*ready for connections.*")
                            .withTimes(2)
                            .withStartupTimeout(java.time.Duration.ofSeconds(60)));
        } else {
            log.info("Using bridge network mode for MySQL container");
            container.withNetwork(network).withNetworkAliases("mysql");
        }

        return container;
    }

    /**
     * Creates PostgreSQL container with appropriate network configuration.
     */
    private static PostgreSQLContainer<?> createPostgreSQLContainer() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(
                DockerImageName.parse("quay.io/debezium/postgres:15").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("registry")
                .withUsername("postgres")
                .withPassword("postgres")
                .withCommand("postgres", "-c", "max_wal_senders=20", "-c", "max_replication_slots=20");

        if (shouldUseHostNetwork()) {
            log.info("Using host network mode for PostgreSQL container (Linux/CI environment)");
            container.withNetworkMode("host");
            container.withStartupTimeout(java.time.Duration.ofSeconds(60))
                    .waitingFor(new org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy()
                            .withRegEx(".*database system is ready to accept connections.*")
                            .withTimes(2)
                            .withStartupTimeout(java.time.Duration.ofSeconds(60)));
        } else {
            log.info("Using bridge network mode for PostgreSQL container (Mac/Windows environment)");
            container.withNetwork(network).withNetworkAliases("postgres");
        }

        return container;
    }

    /**
     * Creates a Debezium container with appropriate network configuration.
     *
     * @param kafka The Kafka container to connect to
     * @param suffix A suffix to add to the container name for identification
     */
    private static DebeziumContainer createDebeziumContainer(KafkaContainer kafka, String suffix) {
        DebeziumContainer container = new DebeziumContainer("quay.io/debezium/connect")
                .withKafka(kafka)
                .withEnv("ENABLE_APICURIO_CONVERTERS", "true")
                .dependsOn(kafka);

        if (shouldUseHostNetwork()) {
            log.info("Using host network mode for Debezium {} container", suffix);
            container.withNetworkMode("host");
        } else {
            log.info("Using bridge network mode for Debezium {} container", suffix);
            container.withNetwork(network);
        }

        return container;
    }
}
