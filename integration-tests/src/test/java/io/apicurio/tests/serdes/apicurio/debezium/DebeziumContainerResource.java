package io.apicurio.tests.serdes.apicurio.debezium;

import io.debezium.testing.testcontainers.DebeziumContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

public class DebeziumContainerResource implements QuarkusTestResourceLifecycleManager {

    private static final Network network = Network.newNetwork();

    private static final KafkaContainer kafkaContainer = DebeziumKafkaContainer
            .defaultKRaftContainer(network);

    public static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("quay.io/debezium/postgres:15").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("registry").withUsername("postgres").withPassword("postgres")
            .withNetwork(network).withNetworkAliases("postgres");

    public static DebeziumContainer debeziumContainer = new DebeziumContainer(
            "quay.io/debezium/connect")
            .withNetwork(network)
            .withKafka(kafkaContainer)
            .withEnv("ENABLE_APICURIO_CONVERTERS", "true")
            .dependsOn(kafkaContainer);

    @Override
    public Map<String, String> start() {
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
