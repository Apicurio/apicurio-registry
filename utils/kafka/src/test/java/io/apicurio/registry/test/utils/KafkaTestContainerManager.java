package io.apicurio.registry.test.utils;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.redpanda.RedpandaContainer;

import java.util.Collections;
import java.util.Map;

public class KafkaTestContainerManager implements QuarkusTestResourceLifecycleManager {
    private static final Logger log = LoggerFactory.getLogger(KafkaTestContainerManager.class);

    private RedpandaContainer kafka;

    @Override
    public Map<String, String> start() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {

            log.info("Starting the Kafka Test Container");
            kafka = new RedpandaContainer("docker.redpanda.com/vectorized/redpanda");

            kafka.addEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
            kafka.addEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
            kafka.withListener(() -> "redpanda:19092");
            kafka.withNetwork(Network.SHARED);
            kafka.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("kafka-testcontainer")));
            kafka.start();

            String externalBootstrapServers = kafka.getBootstrapServers();

            String bootstrapServers = "redpanda:19092";
            System.setProperty("bootstrap.servers.internal", bootstrapServers);
            System.setProperty("bootstrap.servers.external", externalBootstrapServers);

            return Map.of(
                    "bootstrap.servers", externalBootstrapServers,
                    "apicurio.events.kafka.config.bootstrap.servers", externalBootstrapServers,
                    "apicurio.kafkasql.bootstrap.servers", externalBootstrapServers);
        } else {
            return Collections.emptyMap();
        }
    }

    @Override
    public void stop() {
        if (kafka != null) {
            log.info("Stopping the Kafka Test Container");
            kafka.stop();
        }
    }
}
