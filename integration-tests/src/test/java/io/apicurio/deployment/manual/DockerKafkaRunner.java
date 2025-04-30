package io.apicurio.deployment.manual;

import io.strimzi.test.container.StrimziKafkaContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;

public class DockerKafkaRunner implements KafkaRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerKafkaRunner.class);
    private StrimziKafkaContainer kafka;

    @Override
    public void startAndWait() {
        log.info("Starting the Kafka Test Container");
        this.kafka = new StrimziKafkaContainer();
        kafka.addEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
        kafka.addEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
        kafka.addEnv("KAFKA_ADVERTISED_LISTENERS",
                "PLAINTEXT://broker:9092,PLAINTEXT_HOST://localhost:9092");
        kafka.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("kafka")));
        kafka.start();
    }


    @Override
    public String getBootstrapServers() {
        if (!kafka.isRunning()) {
            throw new IllegalStateException("Not started.");
        }
        return kafka.getBootstrapServers();
    }


    @Override
    public String getTestClientBootstrapServers() {
        return getBootstrapServers();
    }


    @Override
    public void stopAndWait() {
        if (kafka != null) {
            kafka.stop();
        }
    }
}
