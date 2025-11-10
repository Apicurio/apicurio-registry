package io.apicurio.tests.serdes.apicurio.debezium.postgresql;

import io.apicurio.tests.serdes.apicurio.debezium.BaseDebeziumContainerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Container resource for Debezium PostgreSQL integration tests.
 */
public class DebeziumContainerResource extends BaseDebeziumContainerResource {

    private static final Logger log = LoggerFactory.getLogger(DebeziumContainerResource.class);

    public static PostgreSQLContainer<?> postgresContainer;

    @Override
    protected String getDatabaseType() {
        return "PostgreSQL";
    }

    @Override
    protected void createKubernetesWrappers() {
        // Create Kubernetes-aware wrapper containers for PostgreSQL
        postgresContainer = new KubernetesPostgreSQLContainerWrapper(
                io.apicurio.deployment.KubernetesTestResources.POSTGRESQL_DEBEZIUM_SERVICE_EXTERNAL);
        debeziumContainer = new KubernetesDebeziumContainerWrapper(
                io.apicurio.deployment.KubernetesTestResources.DEBEZIUM_CONNECT_SERVICE_EXTERNAL);
    }

    @Override
    protected void createLocalContainers() {
        // Create local Testcontainers for PostgreSQL
        postgresContainer = createPostgreSQLContainer();
        debeziumContainer = createDebeziumContainer();
    }

    @Override
    protected GenericContainer<?> getDatabaseContainer() {
        return postgresContainer;
    }

    /**
     * Creates PostgreSQL container with appropriate network configuration.
     */
    protected static PostgreSQLContainer<?> createPostgreSQLContainer() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(
                DockerImageName.parse("quay.io/debezium/postgres:15").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("registry")
                .withUsername("postgres")
                .withPassword("postgres")
                .withCommand("postgres", "-c", "max_wal_senders=20", "-c", "max_replication_slots=20");

        if (shouldUseHostNetwork()) {
            log.info("Using host network mode for PostgreSQL container (Linux/CI environment)");
            container.withNetworkMode("host");
            // In host network mode, PostgreSQL binds directly to localhost:5432
            // Override the wait strategy to use log-based detection instead of port mapping
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
}
