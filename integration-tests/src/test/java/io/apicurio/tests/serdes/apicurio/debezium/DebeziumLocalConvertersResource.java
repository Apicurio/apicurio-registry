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
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Container resource for Debezium integration tests that uses locally built
 * Apicurio converters
 * instead of downloading them from Maven Central. This ensures tests run
 * against the current
 * SNAPSHOT build of the converters library.
 */
public class DebeziumLocalConvertersResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(DebeziumLocalConvertersResource.class);

    private static final Network network = Network.newNetwork();

    private static final KafkaContainer kafkaContainer = DebeziumKafkaContainer
            .defaultKRaftContainer(network);

    public static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("quay.io/debezium/postgres:15").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("registry").withUsername("postgres").withPassword("postgres")
            .withNetwork(network).withNetworkAliases("postgres");

    public static DebeziumContainer debeziumContainer;

    static {
        // Initialize Debezium container with local converters
        debeziumContainer = createDebeziumContainerWithLocalConverters();
    }

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
     * Creates a Debezium container configured to use locally built Apicurio
     * converters.
     * The converters are expected to be unpacked by the maven-dependency-plugin
     * into
     * target/debezium-converters directory during the build.
     */
    private static DebeziumContainer createDebeziumContainerWithLocalConverters() {
        // Determine the path to the local converters
        String projectDir = System.getProperty("user.dir");
        String convertersPath = projectDir + "/target/debezium-converters";
        File convertersDir = new File(convertersPath);

        log.info("Looking for local Apicurio converters at: {}", convertersPath);

        DebeziumContainer container = new DebeziumContainer("quay.io/debezium/connect")
                .withKafka(kafkaContainer)
                .dependsOn(kafkaContainer);

        // Configure network mode based on environment
        if (shouldUseHostNetwork()) {
            log.info("Using host network mode for Debezium container (Linux/CI environment)");
            container.withNetworkMode("host");
        } else {
            log.info("Using bridge network mode for Debezium container (Mac/Windows environment)");
            container.withNetwork(network);
        }

        // Mount local converters if available
        if (convertersDir.exists() && convertersDir.isDirectory()) {
            File[] files = convertersDir.listFiles();
            if (files != null && files.length > 0) {
                log.info("Found {} files in local converters directory", files.length);

                // Copy the entire directory structure to the Kafka Connect plugins path
                container.withCopyFileToContainer(
                        MountableFile.forHostPath(convertersPath),
                        "/kafka/connect/apicurio-converter/");

                log.info("Using local Apicurio converters from: {}", convertersPath);
                log.info("Local converters will be mounted to /kafka/connect/apicurio-converter/ in container");
            } else {
                String errorMsg = "Local converters directory exists but is empty: " + convertersPath;
                log.error(errorMsg);
                throw new IllegalStateException(
                        errorMsg + ". Please run 'mvn clean install -DskipTests' to build the converters.");
            }
        } else {
            String errorMsg = "Local converters not found at: " + convertersPath;
            log.error(errorMsg);
            throw new IllegalStateException(
                    errorMsg + ". Please run 'mvn clean install -DskipTests' to build the converters.");
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
