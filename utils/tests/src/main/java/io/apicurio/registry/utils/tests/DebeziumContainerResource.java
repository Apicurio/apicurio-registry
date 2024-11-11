package io.apicurio.registry.utils.tests;

import io.debezium.testing.testcontainers.ConnectorConfiguration;
import io.debezium.testing.testcontainers.DebeziumContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

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
            "quay.io/debezium/connect:2.6.2.Final").withNetwork(network).withKafka(kafkaContainer)
            .dependsOn(kafkaContainer);

    @Override
    public Map<String, String> start() {
        // Start the postgresql database, kafka, and debezium
        Startables.deepStart(Stream.of(kafkaContainer, postgresContainer, debeziumContainer)).join();

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
        private static final String defaultImage = "confluentinc/cp-kafka:7.2.10";

        public static KafkaContainer defaultKRaftContainer(Network network) {
            try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse(defaultImage))
                    .withNetwork(network).withKraft()) {
                return kafka;
            } catch (Exception e) {
                throw new RuntimeException("Cannot create KRaftContainer with default image.", e);
            }
        }

        public static KafkaContainer defaultKafkaContainer(Network network) {
            try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse(defaultImage))
                    .withNetwork(network)) {
                return kafka;
            } catch (Exception e) {
                throw new RuntimeException("Cannot create KafkaContainer with default image.", e);
            }
        }
    }
}
