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

    private static final Network network = Network.newNetwork();

    private static final KafkaContainer kafkaContainer = DebeziumKafkaContainer
            .defaultKRaftContainer(network);

    public static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("quay.io/debezium/postgres:15").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("registry").withUsername("postgres").withPassword("postgres")
            .withNetwork(network).withNetworkAliases("postgres");

    public static DebeziumContainer debeziumContainer = createDebeziumContainer();

    /**
     * Detects if running in a CI environment or on Linux.
     * Host network mode works on Linux but not on Docker Desktop (Mac/Windows).
     */
    private static boolean shouldUseHostNetwork() {
        boolean isCI = System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null;
        boolean isLinux = System.getProperty("os.name", "").toLowerCase().contains("linux");
        return isCI || isLinux;
    }

    /**
     * Creates the Debezium container with appropriate network configuration.
     * On Linux/CI: Uses host network mode to access registry on localhost.
     * On Mac/Windows: Uses bridge network mode (registry accessed via host.docker.internal).
     */
    private static DebeziumContainer createDebeziumContainer() {
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

    @Override
    public Map<String, String> start() {
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

        // Start the postgresql database, kafka, and debezium
        Startables.deepStart(Stream.of(kafkaContainer, postgresContainer, debeziumContainer)).join();
        System.setProperty("bootstrap.servers", kafkaContainer.getBootstrapServers());

        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        debeziumContainer.stop();
        postgresContainer.stop();
        kafkaContainer.stop();
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
