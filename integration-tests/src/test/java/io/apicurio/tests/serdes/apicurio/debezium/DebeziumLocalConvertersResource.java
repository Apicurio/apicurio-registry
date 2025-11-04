package io.apicurio.tests.serdes.apicurio.debezium;

import io.debezium.testing.testcontainers.ConnectorConfiguration;
import io.debezium.testing.testcontainers.DebeziumContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Container resource for Debezium integration tests that uses locally built Apicurio converters
 * instead of downloading them from Maven Central. This ensures tests run against the current
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
     * Creates a Debezium container configured to use locally built Apicurio converters.
     * The converters are expected to be unpacked by the maven-dependency-plugin into
     * target/debezium-converters directory during the build.
     */
    private static DebeziumContainer createDebeziumContainerWithLocalConverters() {
        // Determine the path to the local converters
        String projectDir = System.getProperty("user.dir");
        String convertersPath = projectDir + "/target/debezium-converters";
        File convertersDir = new File(convertersPath);

        log.info("Looking for local Apicurio converters at: {}", convertersPath);

        DebeziumContainer container = new DebeziumContainer("quay.io/debezium/connect")
                .withNetwork(network)
                .withKafka(kafkaContainer)
                .dependsOn(kafkaContainer);

        // Mount local converters if available
        if (convertersDir.exists() && convertersDir.isDirectory()) {
            File[] files = convertersDir.listFiles();
            if (files != null && files.length > 0) {
                log.info("Found {} files in local converters directory", files.length);

                // Copy the entire directory structure to the Kafka Connect plugins path
                container.withCopyFileToContainer(
                        MountableFile.forHostPath(convertersPath),
                        "/kafka/connect/apicurio-converter/"
                );

                log.info("Using local Apicurio converters from: {}", convertersPath);
                log.info("Local converters will be mounted to /kafka/connect/apicurio-converter/ in container");
            } else {
                String errorMsg = "Local converters directory exists but is empty: " + convertersPath;
                log.error(errorMsg);
                throw new IllegalStateException(errorMsg + ". Please run 'mvn clean install -DskipTests' to build the converters.");
            }
        } else {
            String errorMsg = "Local converters not found at: " + convertersPath;
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg + ". Please run 'mvn clean install -DskipTests' to build the converters.");
        }

        return container;
    }

    @Override
    public Map<String, String> start() {
        // Start the postgresql database, kafka, and debezium
        Startables.deepStart(Stream.of(kafkaContainer, postgresContainer, debeziumContainer)).join();

        System.setProperty("bootstrap.servers", kafkaContainer.getBootstrapServers());

        return Collections.emptyMap();
    }

    /**
     * Helper method to register a PostgreSQL connector with custom configuration
     */
    public static void registerPostgresConnector(String connectorName, String topicPrefix, String tableIncludeList) {
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(postgresContainer)
                .with("topic.prefix", topicPrefix)
                .with("table.include.list", tableIncludeList);

        debeziumContainer.registerConnector(connectorName, connector);
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
