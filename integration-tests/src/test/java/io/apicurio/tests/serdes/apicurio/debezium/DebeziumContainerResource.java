package io.apicurio.tests.serdes.apicurio.debezium;

import io.debezium.testing.testcontainers.DebeziumContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

public class DebeziumContainerResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(DebeziumContainerResource.class);
    protected static final Network network = Network.newNetwork();

    public static KafkaContainer kafkaContainer;
    public static PostgreSQLContainer<?> postgresContainer;
    public static DebeziumContainer debeziumContainer;

    @Override
    public Map<String, String> start() {
        // Check if running in Kubernetes cluster mode
        // In cluster mode, infrastructure is already deployed by RegistryDeploymentManager
        // We just need to create wrapper objects to access the services
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            log.info("cluster.tests=true detected, creating Kubernetes service wrappers");
            log.info("Debezium infrastructure should already be deployed by RegistryDeploymentManager");

            // Create Kubernetes-aware wrapper containers so tests can access services
            // Using external LoadBalancer services for localhost access via minikube tunnel
            postgresContainer = new KubernetesPostgreSQLContainerWrapper(
                    io.apicurio.deployment.KubernetesTestResources.POSTGRESQL_DEBEZIUM_SERVICE_EXTERNAL);
            debeziumContainer = new KubernetesDebeziumContainerWrapper(
                    io.apicurio.deployment.KubernetesTestResources.DEBEZIUM_CONNECT_SERVICE_EXTERNAL);

            log.info("Debezium service wrappers created for cluster mode using external LoadBalancer services");
            return Collections.emptyMap();
        } else {
            postgresContainer = createPostgreSQLContainer();
            debeziumContainer = createDebeziumContainer();
            kafkaContainer = createKafkaContainer();

            // Start the postgresql database, kafka, and debezium
            Startables.deepStart(Stream.of(kafkaContainer, postgresContainer, debeziumContainer)).join();
            System.setProperty("bootstrap.servers", kafkaContainer.getBootstrapServers());

            // Local mode: Use Testcontainers
            log.info("cluster.tests=false (or not set), using Testcontainers for Debezium infrastructure");
        }

        // Get the registry port from system properties (set by Quarkus test framework)
        // In local tests: random port, in CI: 8080
        String testPort = System.getProperty("quarkus.http.test-port", "8080");

        try {
            int port = Integer.parseInt(testPort);
            if (port > 0) {
                // Expose the registry port so containers can access it via host.testcontainers.internal
                Testcontainers.exposeHostPorts(port);
                log.info("Exposed registry port {} to containers (accessible at host.testcontainers.internal:{})", port, port);
            }
        } catch (NumberFormatException e) {
            log.warn("Could not parse test port '{}', containers may not be able to access registry", testPort);
        }

        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        // In cluster mode, resources are cleaned up by RegistryDeploymentManager
        // when the entire test suite finishes (namespace deletion)
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            log.info("cluster.tests=true, skipping container stop (Kubernetes resources managed by test suite lifecycle)");
            return;
        }

        // Local mode: Stop Testcontainers
        debeziumContainer.stop();
        postgresContainer.stop();
        kafkaContainer.stop();
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
     * CRITICAL: Must use the same network mode as Debezium container to ensure connectivity.
     */
    protected static KafkaContainer createKafkaContainer() {
        KafkaContainer container = DebeziumKafkaContainer.defaultKRaftContainer(network);

        if (shouldUseHostNetwork()) {
            log.info("Using host network mode for Kafka container (Linux/CI environment)");
            container.withNetworkMode("host");
        } else {
            log.info("Using bridge network mode for Kafka container (Mac/Windows environment)");
            // Already configured with network in defaultKRaftContainer
        }

        return container;
    }

    /**
     * Creates PostgreSQL container with appropriate network configuration.
     * CRITICAL: Must use the same network mode as Debezium container to ensure connectivity.
     */
    protected static PostgreSQLContainer<?> createPostgreSQLContainer() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(
                DockerImageName.parse("quay.io/debezium/postgres:15").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("registry")
                .withUsername("postgres")
                .withPassword("postgres");

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

    /**
     * Creates the Debezium container with appropriate network configuration.
     * On Linux/CI: Uses host network mode to access registry on localhost.
     * On Mac/Windows: Uses bridge network mode (registry accessed via host.docker.internal).
     */
    protected DebeziumContainer createDebeziumContainer() {
        DebeziumContainer container = new DebeziumContainer("quay.io/debezium/connect")
                .withKafka(kafkaContainer)
                .withEnv("ENABLE_APICURIO_CONVERTERS", "true")
                .dependsOn(kafkaContainer);

        if (shouldUseHostNetwork()) {
            log.info("Using host network mode for Debezium container (Linux/CI environment)");
            container.withNetworkMode("host");
        } else {
            log.info("Using bridge network mode for Debezium container (Mac/Windows environment)");
            container.withNetwork(network);
        }

        return container;
    }

    public class DebeziumKafkaContainer {
        private static final String defaultImage = "confluentinc/cp-kafka:7.2.10";

        public static KafkaContainer defaultKRaftContainer(Network network) {
            return new KafkaContainer(DockerImageName.parse(defaultImage))
                    .withNetwork(network).withKraft();
        }

        public static KafkaContainer defaultKafkaContainer(Network network) {
            return new KafkaContainer(DockerImageName.parse(defaultImage))
                    .withNetwork(network);
        }
    }
}
