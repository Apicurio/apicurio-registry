package io.apicurio.deployment.manual;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.redpanda.RedpandaContainer;

public class DockerKafkaRunner implements KafkaRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerKafkaRunner.class);

    private RedpandaContainer kafka;


    @Override
    public void startAndWait() {
        log.info("Starting the Kafka Test Container");
        kafka = new RedpandaContainer("docker.redpanda.com/vectorized/redpanda");
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
