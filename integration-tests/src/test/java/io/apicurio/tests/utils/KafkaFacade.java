package io.apicurio.tests.utils;

import io.strimzi.test.container.StrimziKafkaCluster;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

/**
 * Facade class for simulate Kafka cluster
 */
public class KafkaFacade implements AutoCloseable {
    static final Logger LOGGER = LoggerFactory.getLogger(KafkaFacade.class);

    private AdminClient client;

    private static KafkaFacade instance;
    private StrimziKafkaCluster kafkaContainer;

    public static KafkaFacade getInstance() {
        if (instance == null) {
            instance = new KafkaFacade();
        }
        return instance;
    }

    private KafkaFacade() {
        // hidden constructor, singleton class
    }

    public void createTopic(String topic, int partitions, int replicationFactor) {
        adminClient().createTopics(Arrays.asList(new NewTopic(topic, partitions, (short) replicationFactor)));
    }

    public String bootstrapServers() {
        return kafkaContainer.getBootstrapServers();
    }

    public Properties connectionProperties() {
        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        properties.put(CommonClientConfigs.CONNECTIONS_MAX_IDLE_MS_CONFIG, 10000);
        properties.put(CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        return properties;
    }

    private boolean isRunning() {
        return kafkaContainer != null;
    }

    public void startIfNeeded() {
        if (isRunning()) {
            LOGGER.info("Skipping deployment of kafka, because it's already deployed");
        } else {
            start();
        }
    }

    private static final int MAX_START_ATTEMPTS = 2;

    public void start() {
        if (isRunning()) {
            throw new IllegalStateException("Kafka cluster is already running");
        }

        for (int attempt = 1; attempt <= MAX_START_ATTEMPTS; attempt++) {
            LOGGER.info("Starting kafka container (attempt {}/{})", attempt, MAX_START_ATTEMPTS);
            this.kafkaContainer = new StrimziKafkaCluster.StrimziKafkaClusterBuilder()
                    .withNumberOfBrokers(1)
                    .withAdditionalKafkaConfiguration(Map.of(
                            "transaction.state.log.replication.factor", "1",
                            "transaction.state.log.min.isr", "1"))
                    .build();
            try {
                kafkaContainer.start();
                return;
            } catch (Exception e) {
                if (e instanceof InterruptedException
                        || e.getCause() instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                LOGGER.warn("Kafka container start attempt {} failed: {}", attempt, e.getMessage());
                try {
                    kafkaContainer.stop();
                } catch (Exception stopEx) {
                    LOGGER.debug("Error stopping failed container", stopEx);
                }
                kafkaContainer = null;
                if (attempt == MAX_START_ATTEMPTS) {
                    throw new RuntimeException("Failed to start Kafka container after " + MAX_START_ATTEMPTS + " attempts", e);
                }
            }
        }
    }

    public void stopIfPossible() throws Exception {
        if (isRunning()) {
            close();
        }
    }

    public AdminClient adminClient() {
        if (client == null) {
            client = AdminClient.create(connectionProperties());
        }
        return client;
    }

    @Override
    public void close() throws Exception {
        LOGGER.info("Stopping kafka container");
        if (client != null) {
            client.close();
            client = null;
        }
        if (kafkaContainer != null) {
            kafkaContainer.stop();
            kafkaContainer = null;
        }
    }
}
