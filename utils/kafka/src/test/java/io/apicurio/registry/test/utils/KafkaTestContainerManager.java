package io.apicurio.registry.test.utils;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.redpanda.RedpandaContainer;

import java.util.Map;

public class KafkaTestContainerManager implements QuarkusTestResourceLifecycleManager {
    private static final Logger log = LoggerFactory.getLogger(KafkaTestContainerManager.class);

    private RedpandaContainer kafka;

    @Override
    public Map<String, String> start() {
        log.info("Starting the Kafka Test Container");
        kafka = new RedpandaContainer("docker.redpanda.com/vectorized/redpanda");

        kafka.addEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
        kafka.addEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
        kafka.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("kafka-testcontainer")));
        kafka.start();

        String bootstrapServers = kafka.getBootstrapServers();

        System.setProperty("bootstrap.servers", bootstrapServers);

        return Map.of("bootstrap.servers", bootstrapServers, "registry.events.kafka.config.bootstrap.servers",
                bootstrapServers, "registry.kafkasql.bootstrap.servers", bootstrapServers);
    }

    @Override
    public void stop() {
        log.info("Stopping the Kafka Test Container");
        kafka.stop();
    }
}
