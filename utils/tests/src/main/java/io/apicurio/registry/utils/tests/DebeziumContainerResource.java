package io.apicurio.registry.utils.tests;

import io.debezium.testing.testcontainers.ConnectorConfiguration;
import io.debezium.testing.testcontainers.DebeziumContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.stream.Stream;

public class DebeziumContainerResource implements QuarkusTestResourceLifecycleManager {

    private static final Network network = Network.newNetwork();

    private static final KafkaContainer kafkaContainer = DebeziumKafkaContainer
            .defaultKafkaContainer(network);

    public static PostgreSQLContainer postgresContainer = new PostgreSQLContainer(
            DockerImageName.parse("quay.io/debezium/postgres:15").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("registry").withUsername("postgres").withPassword("postgres")
            .withNetwork(network).withNetworkAliases("postgres");

    public static DebeziumContainer debeziumContainer = new DebeziumContainer(
            "quay.io/debezium/connect:2.6.2.Final").withNetwork(network)
            .dependsOn(kafkaContainer);

    @Override
    public Map<String, String> start() {
        // Start kafka first so we can get bootstrap servers
        kafkaContainer.start();

        // Configure debezium with kafka's internal network address
        // (external localhost:port is not reachable from within the Docker network)
        debeziumContainer.withKafka(network,
                DebeziumKafkaContainer.KAFKA_ALIAS + ":9092");

        // Start the postgresql database and debezium
        Startables.deepStart(Stream.of(postgresContainer, debeziumContainer)).join();

        // Register the postgresql connector
        ConnectorConfiguration connector = ConnectorConfiguration.forJdbcContainer(postgresContainer)
                .with("topic.prefix", "registry").with("schema.include.list", "public")
                .with("table.include.list", "public.outbox").with("transforms", "outbox")
                .with("transforms.outbox.type", "io.debezium.transforms.outbox.EventRouter");

        debeziumContainer.registerConnector("my-connector", connector);

        System.setProperty("bootstrap.servers", kafkaContainer.getBootstrapServers());

        return Map.of("apicurio.datasource.url", postgresContainer.getJdbcUrl(),
                "apicurio.datasource.username", "postgres", "apicurio.datasource.password", "postgres");
    }

    @Override
    public void stop() {
        debeziumContainer.stop();
        postgresContainer.stop();
        kafkaContainer.stop();
    }

    public class DebeziumKafkaContainer {
        private static final String defaultImage = "apache/kafka:3.8.1";
        public static final String KAFKA_ALIAS = "kafka";

        public static KafkaContainer defaultKafkaContainer(Network network) {
            // In testcontainers 2.x, KRaft is the default mode
            return new KafkaContainer(DockerImageName.parse(defaultImage))
                    .withNetwork(network)
                    .withNetworkAliases(KAFKA_ALIAS);
        }
    }
}
