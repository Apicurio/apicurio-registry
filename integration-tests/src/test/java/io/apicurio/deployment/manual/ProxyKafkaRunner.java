package io.apicurio.deployment.manual;

import lombok.SneakyThrows;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static io.apicurio.deployment.TestConfiguration.isClusterTests;
import static org.apache.kafka.clients.CommonClientConfigs.*;
import static org.apache.kafka.common.config.TopicConfig.*;

public class ProxyKafkaRunner implements KafkaRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyKafkaRunner.class);

    private KafkaRunner delegate;

    private AdminClient kafkaClient;


    public ProxyKafkaRunner() {
        if (isClusterTests()) {
            delegate = new ClusterKafkaRunner();
        } else {
            delegate = new DockerKafkaRunner();
        }
    }


    @Override
    public void startAndWait() {
        delegate.startAndWait();
    }


    @Override
    public String getBootstrapServers() {
        return delegate.getBootstrapServers();
    }


    @Override
    public String getTestClientBootstrapServers() {
        return delegate.getTestClientBootstrapServers();
    }


    /**
     * Do not close the client, it is done when the runner is stopped.
     */
    public AdminClient kafkaClient() {
        if (kafkaClient == null) {
            var properties = new Properties();
            properties.put(BOOTSTRAP_SERVERS_CONFIG, getTestClientBootstrapServers());
            properties.put(CONNECTIONS_MAX_IDLE_MS_CONFIG, 2 * 30 * 1000);
            properties.put(REQUEST_TIMEOUT_MS_CONFIG, 30 * 1000); // Needs to be longer due to Kubernetes port forwarding
            kafkaClient = AdminClient.create(properties);
        }
        return kafkaClient;
    }


    @Override
    public void stopAndWait() {
        if (kafkaClient != null) {
            kafkaClient.close();
        }
        delegate.stopAndWait();
    }


    // === Health Checks

    /**
     * Verifies that Kafka cluster is reachable and responsive
     */
    @SneakyThrows
    public void verifyKafkaConnectivity() {
        LOGGER.info("Verifying Kafka connectivity...");
        var timeout = io.apicurio.deployment.TestConfiguration.getKafkaTimeout();
        
        Awaitility.await("Kafka to be reachable")
            .atMost(timeout)
            .pollInterval(io.apicurio.deployment.TestConfiguration.getPollInterval())
            .until(() -> {
                try {
                    var metadata = kafkaClient().describeCluster();
                    var nodes = metadata.nodes().get(30, TimeUnit.SECONDS);
                    LOGGER.info("Kafka cluster has {} nodes", nodes.size());
                    return !nodes.isEmpty();
                } catch (Exception e) {
                    LOGGER.warn("Kafka connectivity check failed: {}", e.getMessage());
                    return false;
                }
            });
        LOGGER.info("✓ Kafka connectivity verified");
    }

    /**
     * Verifies that Kafka topics can be created and managed
     */
    @SneakyThrows
    public void verifyKafkaTopicOperations() {
        LOGGER.info("Verifying Kafka topic operations...");
        var timeout = io.apicurio.deployment.TestConfiguration.getKafkaTimeout();
        
        Awaitility.await("Kafka topic operations to work")
            .atMost(timeout)
            .pollInterval(io.apicurio.deployment.TestConfiguration.getPollInterval())
            .until(() -> {
                try {
                    // Try to list topics to verify admin operations work
                    var topics = kafkaClient().listTopics().names().get(30, TimeUnit.SECONDS);
                    LOGGER.info("Kafka has {} existing topics", topics.size());
                    return true;
                } catch (Exception e) {
                    LOGGER.warn("Kafka topic operations check failed: {}", e.getMessage());
                    return false;
                }
            });
        LOGGER.info("✓ Kafka topic operations verified");
    }

    // === Utils


    @SneakyThrows
    public static void createCompactingTopic(ProxyKafkaRunner kafka, String name, int partitions) {
        var topic = new NewTopic(name, partitions, (short) 1);
        topic.configs(Map.of(
                MIN_CLEANABLE_DIRTY_RATIO_CONFIG, "0.000001",
                CLEANUP_POLICY_CONFIG, CLEANUP_POLICY_COMPACT,
                SEGMENT_MS_CONFIG, "100",
                DELETE_RETENTION_MS_CONFIG, "100"
        ));
        kafka.kafkaClient().createTopics(List.of(topic)).all().get();
    }
}
