package io.apicurio.tests.serdes.apicurio.debezium;

import io.debezium.testing.testcontainers.DebeziumContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Base class for Debezium container test resources.
 * Provides common infrastructure for both PostgreSQL and MySQL Debezium tests,
 * supporting both local Testcontainers mode and Kubernetes cluster mode.
 */
public abstract class BaseDebeziumContainerResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(BaseDebeziumContainerResource.class);
    protected static final Network network = Network.newNetwork();

    public static KafkaContainer kafkaContainer;
    public static DebeziumContainer debeziumContainer;

    @Override
    public Map<String, String> start() {
        // Check if running in Kubernetes cluster mode
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            log.info("cluster.tests=true detected, creating Kubernetes service wrappers");
            log.info("Debezium {} infrastructure should already be deployed by RegistryDeploymentManager",
                    getDatabaseType());

            // Create Kubernetes-aware wrapper containers
            createKubernetesWrappers();

            log.info("Debezium {} service wrappers created for cluster mode", getDatabaseType());
            return Collections.emptyMap();
        } else {
            // Local mode: Use Testcontainers
            log.info("cluster.tests=false, using Testcontainers for Debezium {} infrastructure",
                    getDatabaseType());

            createLocalContainers();
            kafkaContainer = createKafkaContainer();

            // Start all containers
            Startables.deepStart(Stream.of(kafkaContainer, getDatabaseContainer(), debeziumContainer)).join();
            System.setProperty("bootstrap.servers", kafkaContainer.getBootstrapServers());
        }

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

        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        // In cluster mode, resources are cleaned up by RegistryDeploymentManager
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            log.info("cluster.tests=true, skipping container stop");
            return;
        }

        // Local mode: Stop Testcontainers
        if (debeziumContainer != null) {
            debeziumContainer.stop();
        }
        if (getDatabaseContainer() != null) {
            getDatabaseContainer().stop();
        }
        if (kafkaContainer != null) {
            kafkaContainer.stop();
        }
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
    protected static KafkaContainer createKafkaContainer() {
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
     * Creates the Debezium container with appropriate network configuration.
     */
    protected DebeziumContainer createDebeziumContainer() {
        DebeziumContainer container = new DebeziumContainer("quay.io/debezium/connect")
                .withKafka(kafkaContainer)
                .withEnv("ENABLE_APICURIO_CONVERTERS", "true")
                .dependsOn(kafkaContainer);

        if (shouldUseHostNetwork()) {
            log.info("Using host network mode for Debezium container");
            container.withNetworkMode("host");
        } else {
            log.info("Using bridge network mode for Debezium container");
            container.withNetwork(network);
        }

        return container;
    }

    // ==================== Abstract Methods for Database-Specific Behavior ====================

    /**
     * Returns the database type name for logging purposes.
     */
    protected abstract String getDatabaseType();

    /**
     * Creates Kubernetes wrapper containers for cluster mode.
     * Implementations should set the database container and Debezium container fields.
     */
    protected abstract void createKubernetesWrappers();

    /**
     * Creates local Testcontainers for local mode.
     * Implementations should set the database container and Debezium container fields.
     */
    protected abstract void createLocalContainers();

    /**
     * Returns the database container instance.
     * This is needed for the start/stop methods to work with both PostgreSQL and MySQL.
     */
    protected abstract GenericContainer<?> getDatabaseContainer();
}
